package com.takhub.safelayerde.source.dwd;

import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.WarningGeometry;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public class DwdCapParser {

    public ParsedAlert parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        trySetXIncludeAware(factory, false);
        factory.setExpandEntityReferences(false);
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        javax.xml.parsers.DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new org.xml.sax.ErrorHandler() {
            @Override
            public void warning(org.xml.sax.SAXParseException exception) {
            }

            @Override
            public void error(org.xml.sax.SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        Document document = documentBuilder.parse(new InputSource(new StringReader(xml)));
        Element alertElement = document == null ? null : document.getDocumentElement();
        if (alertElement == null || !"alert".equals(alertElement.getLocalName())) {
            throw new IllegalArgumentException("XML did not contain a CAP alert root element.");
        }

        ParsedAlert parsedAlert = new ParsedAlert();
        parsedAlert.setIdentifier(childText(alertElement, "identifier"));
        parsedAlert.setSender(childText(alertElement, "sender"));
        parsedAlert.setSentRaw(childText(alertElement, "sent"));
        parsedAlert.setSentAtEpochMs(parseIso8601(parsedAlert.getSentRaw()));
        parsedAlert.setStatus(childText(alertElement, "status"));
        parsedAlert.setMsgType(childText(alertElement, "msgType"));
        parsedAlert.setReferences(childText(alertElement, "references"));
        parsedAlert.setReferenceIdentifiers(parseReferenceIdentifiers(parsedAlert.getReferences()));
        parsedAlert.setInfo(selectBestInfo(parseInfos(alertElement)));
        return parsedAlert;
    }

    private void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Android XML implementations differ between test and runtime.
        }
    }

    private void trySetXIncludeAware(DocumentBuilderFactory factory, boolean value) {
        try {
            factory.setXIncludeAware(value);
        } catch (RuntimeException ignored) {
            // Android's bundled parser can reject this configuration despite supporting CAP parsing itself.
        }
    }

    private ParsedInfo selectBestInfo(List<ParsedInfo> infos) {
        if (infos.isEmpty()) {
            return new ParsedInfo();
        }

        ParsedInfo best = infos.get(0);
        int bestRank = infoRank(best);
        int bestContentScore = infoContentScore(best);
        for (ParsedInfo info : infos) {
            int rank = infoRank(info);
            int contentScore = infoContentScore(info);
            if (rank < bestRank || (rank == bestRank && contentScore > bestContentScore)) {
                best = info;
                bestRank = rank;
                bestContentScore = contentScore;
            }
        }
        return best == null ? new ParsedInfo() : best;
    }

    private int infoRank(ParsedInfo info) {
        String language = info == null ? null : info.getLanguage();
        if (language == null || language.trim().isEmpty()) {
            return 1;
        }

        String normalized = normalizeLanguage(language);
        if (isGermanLanguage(normalized)) {
            return 0;
        }
        if (normalized.startsWith("mul")) {
            return 2;
        }
        return 3;
    }

    static boolean isGermanLanguage(String language) {
        String normalized = normalizeLanguage(language);
        return normalized.startsWith("de")
                || normalized.startsWith("deu")
                || normalized.startsWith("ger")
                || normalized.startsWith("deutsch")
                || normalized.startsWith("german");
    }

    private static String normalizeLanguage(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.US);
    }

    private int infoContentScore(ParsedInfo info) {
        if (info == null) {
            return 0;
        }

        int score = 0;
        if (hasText(info.getHeadline())) {
            score += 4;
        }
        if (hasText(info.getDescription())) {
            score += 3;
        }
        if (hasText(info.getInstruction())) {
            score += 2;
        }
        if (hasText(info.getEvent())) {
            score += 1;
        }
        return score;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<ParsedInfo> parseInfos(Element alertElement) {
        List<ParsedInfo> infos = new ArrayList<>();
        for (Element infoElement : childElements(alertElement, "info")) {
            ParsedInfo info = new ParsedInfo();
            info.setLanguage(childText(infoElement, "language"));
            info.setCategory(childText(infoElement, "category"));
            info.setEvent(childText(infoElement, "event"));
            info.setUrgency(childText(infoElement, "urgency"));
            info.setSeverity(childText(infoElement, "severity"));
            info.setCertainty(childText(infoElement, "certainty"));
            info.setHeadline(childText(infoElement, "headline"));
            info.setDescription(childText(infoElement, "description"));
            info.setInstruction(childText(infoElement, "instruction"));
            info.setEffectiveRaw(childText(infoElement, "effective"));
            info.setEffectiveAtEpochMs(parseIso8601(info.getEffectiveRaw()));
            info.setOnsetRaw(childText(infoElement, "onset"));
            info.setOnsetAtEpochMs(parseIso8601(info.getOnsetRaw()));
            info.setExpiresRaw(childText(infoElement, "expires"));
            info.setExpiresAtEpochMs(parseIso8601(info.getExpiresRaw()));
            info.setAreas(parseAreas(infoElement));
            infos.add(info);
        }
        return infos;
    }

    private List<ParsedArea> parseAreas(Element infoElement) {
        List<ParsedArea> areas = new ArrayList<>();
        for (Element areaElement : childElements(infoElement, "area")) {
            ParsedArea area = new ParsedArea();
            area.setAreaDesc(childText(areaElement, "areaDesc"));
            area.setWarnCellId(readWarnCellId(areaElement));
            area.setExplicitGeometry(parseExplicitGeometry(areaElement));
            areas.add(area);
        }
        return areas;
    }

    private String readWarnCellId(Element areaElement) {
        for (Element geocodeElement : childElements(areaElement, "geocode")) {
            String valueName = childText(geocodeElement, "valueName");
            if (!"WARNCELLID".equalsIgnoreCase(valueName)) {
                continue;
            }
            return childText(geocodeElement, "value");
        }
        return null;
    }

    private WarningGeometry parseExplicitGeometry(Element areaElement) {
        List<double[][]> polygonCoordinates = new ArrayList<>();
        for (Element polygonElement : childElements(areaElement, "polygon")) {
            double[][] coordinates = parseCapPolygon(textContent(polygonElement));
            if (coordinates.length >= 4) {
                polygonCoordinates.add(coordinates);
            }
        }
        if (!polygonCoordinates.isEmpty()) {
            return buildPolygonGeometry(polygonCoordinates);
        }

        String circle = childText(areaElement, "circle");
        if (circle != null && !circle.trim().isEmpty()) {
            return buildCircleCenterGeometry(circle);
        }
        return null;
    }

    private WarningGeometry buildPolygonGeometry(List<double[][]> polygons) {
        double[] bbox = new double[] {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        StringBuilder coordinatesBuilder = new StringBuilder();
        if (polygons.size() == 1) {
            appendPolygonCoordinates(coordinatesBuilder, polygons.get(0), bbox);
        } else {
            coordinatesBuilder.append('[');
            for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
                if (polygonIndex > 0) {
                    coordinatesBuilder.append(',');
                }
                appendPolygonCoordinates(coordinatesBuilder, polygons.get(polygonIndex), bbox);
            }
            coordinatesBuilder.append(']');
        }

        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(polygons.size() == 1 ? GeometryKind.POLYGON : GeometryKind.MULTI_POLYGON);
        geometry.setGeoJsonGeometry("{\"type\":\""
                + (polygons.size() == 1 ? "Polygon" : "MultiPolygon")
                + "\",\"coordinates\":"
                + coordinatesBuilder
                + "}");
        geometry.setBbox(normalizeBbox(bbox));
        geometry.setCentroidLon((bbox[0] + bbox[2]) / 2D);
        geometry.setCentroidLat((bbox[1] + bbox[3]) / 2D);
        geometry.setApproximate(false);
        geometry.setGeometrySource("DWD_CAP_POLYGON");
        geometry.setGeometryConfidence(GeometryConfidence.CONFIRMED);
        return geometry;
    }

    private void appendPolygonCoordinates(StringBuilder builder, double[][] polygon, double[] bbox) {
        builder.append('[');
        appendRing(builder, polygon, bbox);
        builder.append(']');
    }

    private void appendRing(StringBuilder builder, double[][] polygon, double[] bbox) {
        builder.append('[');
        for (int pointIndex = 0; pointIndex < polygon.length; pointIndex++) {
            double[] point = polygon[pointIndex];
            if (pointIndex > 0) {
                builder.append(',');
            }
            bbox[0] = Math.min(bbox[0], point[0]);
            bbox[1] = Math.min(bbox[1], point[1]);
            bbox[2] = Math.max(bbox[2], point[0]);
            bbox[3] = Math.max(bbox[3], point[1]);
            builder.append('[')
                    .append(point[0])
                    .append(',')
                    .append(point[1])
                    .append(']');
        }
        builder.append(']');
    }

    private WarningGeometry buildCircleCenterGeometry(String circleValue) {
        String[] parts = circleValue.trim().split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        String[] latLon = parts[0].split(",");
        if (latLon.length < 2) {
            return null;
        }

        double latitude = parseDouble(latLon[0]);
        double longitude = parseDouble(latLon[1]);
        if (!isRenderableCoordinate(latitude, longitude)) {
            return null;
        }

        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(GeometryKind.POINT);
        geometry.setCentroidLat(latitude);
        geometry.setCentroidLon(longitude);
        geometry.setBbox(new double[] {longitude, latitude, longitude, latitude});
        geometry.setApproximate(false);
        geometry.setGeometrySource("DWD_CAP_CIRCLE_CENTER");
        geometry.setGeometryConfidence(GeometryConfidence.CONFIRMED);
        return geometry;
    }

    private double[][] parseCapPolygon(String polygonValue) {
        if (polygonValue == null || polygonValue.trim().isEmpty()) {
            return new double[0][0];
        }

        List<double[]> points = new ArrayList<>();
        for (String pointValue : polygonValue.trim().split("\\s+")) {
            String[] latLon = pointValue.split(",");
            if (latLon.length < 2) {
                continue;
            }

            double latitude = parseDouble(latLon[0]);
            double longitude = parseDouble(latLon[1]);
            if (!isRenderableCoordinate(latitude, longitude)) {
                continue;
            }
            points.add(new double[] {longitude, latitude});
        }

        if (points.size() < 3) {
            return new double[0][0];
        }

        double[] first = points.get(0);
        double[] last = points.get(points.size() - 1);
        if (Double.compare(first[0], last[0]) != 0 || Double.compare(first[1], last[1]) != 0) {
            points.add(new double[] {first[0], first[1]});
        }
        return points.toArray(new double[0][0]);
    }

    private List<String> parseReferenceIdentifiers(String referencesRaw) {
        List<String> identifiers = new ArrayList<>();
        if (referencesRaw == null || referencesRaw.trim().isEmpty()) {
            return identifiers;
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String reference : referencesRaw.trim().split("\\s+")) {
            if (reference == null || reference.trim().isEmpty()) {
                continue;
            }

            String identifier = reference.trim();
            String[] parts = identifier.split(",");
            if (parts.length >= 2 && parts[1] != null && !parts[1].trim().isEmpty()) {
                identifier = parts[1].trim();
            }
            if (unique.add(identifier)) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    private List<Element> childElements(Element parent, String localName) {
        List<Element> elements = new ArrayList<>();
        if (parent == null) {
            return elements;
        }

        NodeList childNodes = parent.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            Node child = childNodes.item(index);
            if (child instanceof Element) {
                Element element = (Element) child;
                if (localName.equals(element.getLocalName())) {
                    elements.add(element);
                }
            }
        }
        return elements;
    }

    private String childText(Element parent, String localName) {
        List<Element> children = childElements(parent, localName);
        return children.isEmpty() ? null : textContent(children.get(0));
    }

    private String textContent(Element element) {
        if (element == null) {
            return null;
        }
        String value = element.getTextContent();
        return value == null ? null : value.trim();
    }

    private long parseIso8601(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String normalized = normalizeIso8601Offset(value.trim());
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(normalized).getTime();
        } catch (ParseException | NullPointerException ignored) {
            return 0L;
        }
    }

    private String normalizeIso8601Offset(String value) {
        if (value.endsWith("Z")) {
            return value.substring(0, value.length() - 1) + "+0000";
        }
        int timezoneSeparator = value.length() - 3;
        if (timezoneSeparator > 0 && value.charAt(timezoneSeparator) == ':') {
            return value.substring(0, timezoneSeparator) + value.substring(timezoneSeparator + 1);
        }
        return value;
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private double[] normalizeBbox(double[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return new double[4];
        }
        if (Double.isInfinite(bbox[0]) || Double.isInfinite(bbox[1]) || Double.isInfinite(bbox[2]) || Double.isInfinite(bbox[3])) {
            return new double[4];
        }
        return new double[] {bbox[0], bbox[1], bbox[2], bbox[3]};
    }

    private boolean isRenderableCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !(latitude == 0D && longitude == 0D);
    }

    public static final class ParsedAlert {

        private String identifier;
        private String sender;
        private String sentRaw;
        private long sentAtEpochMs;
        private String status;
        private String msgType;
        private String references;
        private List<String> referenceIdentifiers = new ArrayList<>();
        private String operationalAlertKey;
        private ParsedInfo info = new ParsedInfo();

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getSentRaw() {
            return sentRaw;
        }

        public void setSentRaw(String sentRaw) {
            this.sentRaw = sentRaw;
        }

        public long getSentAtEpochMs() {
            return sentAtEpochMs;
        }

        public void setSentAtEpochMs(long sentAtEpochMs) {
            this.sentAtEpochMs = sentAtEpochMs;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMsgType() {
            return msgType;
        }

        public void setMsgType(String msgType) {
            this.msgType = msgType;
        }

        public String getReferences() {
            return references;
        }

        public void setReferences(String references) {
            this.references = references;
        }

        public List<String> getReferenceIdentifiers() {
            return new ArrayList<>(referenceIdentifiers);
        }

        public void setReferenceIdentifiers(List<String> referenceIdentifiers) {
            this.referenceIdentifiers = referenceIdentifiers == null
                    ? new ArrayList<String>()
                    : referenceIdentifiers;
        }

        public String getOperationalAlertKey() {
            return operationalAlertKey;
        }

        public void setOperationalAlertKey(String operationalAlertKey) {
            this.operationalAlertKey = operationalAlertKey;
        }

        public ParsedInfo getInfo() {
            return info;
        }

        public void setInfo(ParsedInfo info) {
            this.info = info == null ? new ParsedInfo() : info;
        }
    }

    public static final class ParsedInfo {

        private String language;
        private String category;
        private String event;
        private String urgency;
        private String severity;
        private String certainty;
        private String headline;
        private String description;
        private String instruction;
        private String effectiveRaw;
        private long effectiveAtEpochMs;
        private String onsetRaw;
        private long onsetAtEpochMs;
        private String expiresRaw;
        private long expiresAtEpochMs;
        private List<ParsedArea> areas = new ArrayList<>();

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public String getUrgency() {
            return urgency;
        }

        public void setUrgency(String urgency) {
            this.urgency = urgency;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getCertainty() {
            return certainty;
        }

        public void setCertainty(String certainty) {
            this.certainty = certainty;
        }

        public String getHeadline() {
            return headline;
        }

        public void setHeadline(String headline) {
            this.headline = headline;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getInstruction() {
            return instruction;
        }

        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }

        public String getEffectiveRaw() {
            return effectiveRaw;
        }

        public void setEffectiveRaw(String effectiveRaw) {
            this.effectiveRaw = effectiveRaw;
        }

        public long getEffectiveAtEpochMs() {
            return effectiveAtEpochMs;
        }

        public void setEffectiveAtEpochMs(long effectiveAtEpochMs) {
            this.effectiveAtEpochMs = effectiveAtEpochMs;
        }

        public String getOnsetRaw() {
            return onsetRaw;
        }

        public void setOnsetRaw(String onsetRaw) {
            this.onsetRaw = onsetRaw;
        }

        public long getOnsetAtEpochMs() {
            return onsetAtEpochMs;
        }

        public void setOnsetAtEpochMs(long onsetAtEpochMs) {
            this.onsetAtEpochMs = onsetAtEpochMs;
        }

        public String getExpiresRaw() {
            return expiresRaw;
        }

        public void setExpiresRaw(String expiresRaw) {
            this.expiresRaw = expiresRaw;
        }

        public long getExpiresAtEpochMs() {
            return expiresAtEpochMs;
        }

        public void setExpiresAtEpochMs(long expiresAtEpochMs) {
            this.expiresAtEpochMs = expiresAtEpochMs;
        }

        public List<ParsedArea> getAreas() {
            return new ArrayList<>(areas);
        }

        public void setAreas(List<ParsedArea> areas) {
            this.areas = areas == null ? new ArrayList<ParsedArea>() : areas;
        }
    }

    public static final class ParsedArea {

        private String areaDesc;
        private String warnCellId;
        private WarningGeometry explicitGeometry;

        public String getAreaDesc() {
            return areaDesc;
        }

        public void setAreaDesc(String areaDesc) {
            this.areaDesc = areaDesc;
        }

        public String getWarnCellId() {
            return warnCellId;
        }

        public void setWarnCellId(String warnCellId) {
            this.warnCellId = warnCellId;
        }

        public WarningGeometry getExplicitGeometry() {
            return explicitGeometry;
        }

        public void setExplicitGeometry(WarningGeometry explicitGeometry) {
            this.explicitGeometry = explicitGeometry;
        }
    }
}
