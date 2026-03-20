package com.takhub.safelayerde.source.dwd;

import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.WarningAreaRef;
import com.takhub.safelayerde.domain.model.WarningGeometry;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSeverity;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.model.WarningUrgency;
import com.takhub.safelayerde.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DwdCapNormalizer {

    private final Map<String, WarningGeometry> officialGeometriesByWarnCellId;

    public DwdCapNormalizer() {
        this(null);
    }

    public DwdCapNormalizer(Map<String, WarningGeometry> officialGeometriesByWarnCellId) {
        this.officialGeometriesByWarnCellId = officialGeometriesByWarnCellId == null
                ? new LinkedHashMap<String, WarningGeometry>()
                : new LinkedHashMap<>(officialGeometriesByWarnCellId);
    }

    public WarningRecord normalize(DwdCapParser.ParsedAlert alert, long fetchedAtMs) {
        DwdCapParser.ParsedInfo info = alert == null ? null : alert.getInfo();
        WarningRecord record = new WarningRecord();
        record.setStableId("DWD:" + canonicalAlertKey(alert));
        record.setSourceType(WarningSourceType.DWD);
        record.setSourceId(alert == null ? null : alert.getIdentifier());
        record.setVersion(alert == null ? null : firstNonEmpty(alert.getSentRaw(), alert.getIdentifier()));
        record.setStatus(alert == null ? null : alert.getStatus());
        record.setMsgType(alert == null ? null : alert.getMsgType());
        record.setCategory(info == null ? null : info.getCategory());
        record.setEventCode(info == null ? null : info.getEvent());
        record.setEventLabel(info == null ? null : info.getEvent());
        record.setTitle(resolveTitle(alert));
        record.setSourceLabel("DWD");
        record.setDescriptionText(info == null ? null : info.getDescription());
        record.setInstructionText(info == null ? null : info.getInstruction());
        record.setSeverity(mapSeverity(info == null ? null : info.getSeverity()));
        record.setUrgency(mapUrgency(info == null ? null : info.getUrgency()));
        record.setCertainty(info == null ? null : info.getCertainty());
        record.setSentAtEpochMs(alert == null ? 0L : alert.getSentAtEpochMs());
        record.setEffectiveAtEpochMs(info == null ? 0L : info.getEffectiveAtEpochMs());
        record.setOnsetAtEpochMs(info == null ? 0L : info.getOnsetAtEpochMs());
        record.setExpiresAtEpochMs(info == null ? 0L : info.getExpiresAtEpochMs());
        record.setLastFetchedAtEpochMs(fetchedAtMs);
        record.setAreaRefs(buildAreaRefs(info == null ? null : info.getAreas()));
        WarningGeometry geometry = resolveGeometry(info == null ? null : info.getAreas());
        record.setGeometry(geometry);
        record.setRenderMode(resolveRenderMode(geometry));
        record.setMetadata(buildMetadata(alert, record.getAreaRefs(), geometry));
        return record;
    }

    public String canonicalAlertKey(DwdCapParser.ParsedAlert alert) {
        if (alert == null) {
            return null;
        }
        if (alert.getOperationalAlertKey() != null && !alert.getOperationalAlertKey().trim().isEmpty()) {
            return alert.getOperationalAlertKey().trim();
        }

        String msgType = alert.getMsgType();
        if ("Update".equalsIgnoreCase(msgType) || "Cancel".equalsIgnoreCase(msgType)) {
            List<String> references = alert.getReferenceIdentifiers();
            if (!references.isEmpty() && references.get(0) != null && !references.get(0).trim().isEmpty()) {
                return references.get(0).trim();
            }
        }
        return alert.getIdentifier();
    }

    private String resolveTitle(DwdCapParser.ParsedAlert alert) {
        DwdCapParser.ParsedInfo info = alert == null ? null : alert.getInfo();
        if (info != null && info.getHeadline() != null && !info.getHeadline().trim().isEmpty()) {
            return info.getHeadline();
        }
        if (info != null && info.getEvent() != null && !info.getEvent().trim().isEmpty()) {
            String firstArea = firstAreaDescription(info.getAreas());
            return firstArea == null ? info.getEvent() : info.getEvent() + " - " + firstArea;
        }
        return alert == null ? null : alert.getIdentifier();
    }

    private List<WarningAreaRef> buildAreaRefs(List<DwdCapParser.ParsedArea> areas) {
        List<WarningAreaRef> areaRefs = new ArrayList<>();
        if (areas == null) {
            return areaRefs;
        }

        for (int index = 0; index < areas.size(); index++) {
            DwdCapParser.ParsedArea area = areas.get(index);
            if (area == null) {
                continue;
            }
            String areaId = firstNonEmpty(area.getWarnCellId(), area.getAreaDesc(), "dwd-area-" + index);
            areaRefs.add(new WarningAreaRef(areaId, area.getAreaDesc(), area.getWarnCellId()));
        }
        return areaRefs;
    }

    private WarningGeometry resolveGeometry(List<DwdCapParser.ParsedArea> areas) {
        List<WarningGeometry> polygonGeometries = new ArrayList<>();
        List<WarningGeometry> pointGeometries = new ArrayList<>();
        int totalAreaCount = 0;
        int resolvedAreaCount = 0;
        int polygonAreaCount = 0;

        if (areas != null) {
            for (DwdCapParser.ParsedArea area : areas) {
                if (area == null) {
                    continue;
                }
                totalAreaCount++;

                WarningGeometry geometry = resolveAreaGeometry(area);
                if (!hasGeometry(geometry)) {
                    continue;
                }

                resolvedAreaCount++;
                if (isPolygonKind(geometry)) {
                    polygonAreaCount++;
                    polygonGeometries.add(copyGeometry(geometry));
                } else if (geometry.getKind() == GeometryKind.POINT) {
                    pointGeometries.add(copyGeometry(geometry));
                }
            }
        }

        WarningGeometry geometry = emptyGeometry();
        if (!polygonGeometries.isEmpty()) {
            geometry = combinePolygonGeometries(polygonGeometries);
        } else if (!pointGeometries.isEmpty()) {
            geometry = combinePointGeometries(pointGeometries);
        }

        boolean everyAreaResolved = totalAreaCount > 0 && resolvedAreaCount == totalAreaCount;
        boolean polygonCoversEveryArea = everyAreaResolved
                && polygonAreaCount == totalAreaCount
                && isPolygonKind(geometry);
        if ((isPolygonKind(geometry) && !polygonCoversEveryArea)
                || (geometry.getKind() == GeometryKind.POINT && !everyAreaResolved)) {
            markApproximate(geometry);
        }

        return geometry;
    }

    private WarningGeometry resolveAreaGeometry(DwdCapParser.ParsedArea area) {
        if (area == null) {
            return null;
        }
        if (hasGeometry(area.getExplicitGeometry())) {
            return copyGeometry(area.getExplicitGeometry());
        }

        WarningGeometry resolvedGeometry = resolveOfficialWarnCellGeometry(area);
        if (hasGeometry(resolvedGeometry)) {
            return copyGeometry(resolvedGeometry);
        }
        return null;
    }

    private WarningGeometry resolveOfficialWarnCellGeometry(DwdCapParser.ParsedArea area) {
        String warnCellId = StringUtils.trimToNull(area == null ? null : area.getWarnCellId());
        if (warnCellId == null) {
            return null;
        }

        WarningGeometry geometry = officialGeometriesByWarnCellId.get(warnCellId);
        if (!isTrustedOfficialGeometry(geometry)) {
            return null;
        }
        return copyGeometry(geometry);
    }

    private WarningGeometry emptyGeometry() {
        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(GeometryKind.NONE);
        geometry.setGeometryConfidence(GeometryConfidence.NONE);
        return geometry;
    }

    private boolean isPolygonKind(WarningGeometry geometry) {
        if (geometry == null) {
            return false;
        }
        return geometry.getKind() == GeometryKind.POLYGON || geometry.getKind() == GeometryKind.MULTI_POLYGON;
    }

    private void markApproximate(WarningGeometry geometry) {
        if (geometry == null || geometry.getKind() == GeometryKind.NONE) {
            return;
        }
        geometry.setApproximate(true);
        if (geometry.getGeometryConfidence() != GeometryConfidence.NONE) {
            geometry.setGeometryConfidence(GeometryConfidence.APPROXIMATE);
        }
    }

    private WarningGeometry combinePolygonGeometries(List<WarningGeometry> geometries) {
        if (geometries == null || geometries.isEmpty()) {
            return emptyGeometry();
        }
        if (geometries.size() == 1) {
            return copyGeometry(geometries.get(0));
        }

        StringBuilder combinedCoordinates = new StringBuilder();
        double[] bbox = new double[] {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };
        boolean approximate = false;
        GeometryConfidence confidence = GeometryConfidence.CONFIRMED;
        Set<String> geometrySources = new LinkedHashSet<>();
        int polygonCount = 0;

        for (WarningGeometry geometry : geometries) {
            String coordinateBlock = extractMultiPolygonCoordinateBlock(geometry == null ? null : geometry.getGeoJsonGeometry());
            if (coordinateBlock == null || coordinateBlock.trim().isEmpty()) {
                continue;
            }
            if (combinedCoordinates.length() > 0) {
                combinedCoordinates.append(',');
            }
            combinedCoordinates.append(coordinateBlock);
            polygonCount += countTopLevelPolygons(coordinateBlock);
            mergeBbox(bbox, geometry.getBbox());
            approximate = approximate || geometry.isApproximate();
            confidence = mergeConfidence(confidence, geometry.getGeometryConfidence());
            if (geometry.getGeometrySource() != null && !geometry.getGeometrySource().trim().isEmpty()) {
                geometrySources.add(geometry.getGeometrySource().trim());
            }
        }

        if (combinedCoordinates.length() == 0) {
            return emptyGeometry();
        }

        WarningGeometry combined = new WarningGeometry();
        combined.setKind(polygonCount <= 1 ? GeometryKind.POLYGON : GeometryKind.MULTI_POLYGON);
        combined.setGeoJsonGeometry(polygonCount <= 1
                ? "{\"type\":\"Polygon\",\"coordinates\":" + unwrapTopLevelPolygon(combinedCoordinates.toString()) + "}"
                : "{\"type\":\"MultiPolygon\",\"coordinates\":[" + combinedCoordinates + "]}");
        combined.setBbox(normalizeBbox(bbox));
        combined.setCentroidLon((bbox[0] + bbox[2]) / 2D);
        combined.setCentroidLat((bbox[1] + bbox[3]) / 2D);
        combined.setApproximate(approximate || confidence == GeometryConfidence.APPROXIMATE);
        combined.setGeometrySource(join(geometrySources));
        combined.setGeometryConfidence(confidence);
        return combined;
    }

    private WarningGeometry combinePointGeometries(List<WarningGeometry> geometries) {
        double[] bbox = new double[] {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };
        double latSum = 0D;
        double lonSum = 0D;
        int count = 0;
        boolean approximate = false;
        GeometryConfidence confidence = GeometryConfidence.CONFIRMED;
        Set<String> geometrySources = new LinkedHashSet<>();

        for (WarningGeometry geometry : geometries) {
            if (!isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon())) {
                continue;
            }
            latSum += geometry.getCentroidLat();
            lonSum += geometry.getCentroidLon();
            count++;
            mergeBbox(bbox, geometry.getBbox());
            approximate = approximate || geometry.isApproximate();
            confidence = mergeConfidence(confidence, geometry.getGeometryConfidence());
            if (geometry.getGeometrySource() != null && !geometry.getGeometrySource().trim().isEmpty()) {
                geometrySources.add(geometry.getGeometrySource().trim());
            }
        }

        WarningGeometry combined = new WarningGeometry();
        if (count == 0) {
            return emptyGeometry();
        }
        combined.setKind(GeometryKind.POINT);
        combined.setCentroidLat(latSum / count);
        combined.setCentroidLon(lonSum / count);
        combined.setBbox(normalizeBbox(bbox));
        combined.setApproximate(approximate || confidence == GeometryConfidence.APPROXIMATE);
        combined.setGeometrySource(join(geometrySources));
        combined.setGeometryConfidence(confidence);
        return combined;
    }

    private String extractMultiPolygonCoordinateBlock(String geoJsonGeometry) {
        if (geoJsonGeometry == null || geoJsonGeometry.trim().isEmpty()) {
            return null;
        }

        String compact = geoJsonGeometry.replaceAll("\\s+", "");
        int coordinatesIndex = compact.indexOf("\"coordinates\":");
        if (coordinatesIndex < 0) {
            return null;
        }
        int arrayStart = compact.indexOf('[', coordinatesIndex);
        if (arrayStart < 0) {
            return null;
        }
        int arrayEnd = findMatchingBracket(compact, arrayStart);
        if (arrayEnd < 0) {
            return null;
        }

        String coordinates = compact.substring(arrayStart, arrayEnd + 1);
        if (compact.contains("\"type\":\"Polygon\"")) {
            return "[" + coordinates + "]";
        }
        if (compact.contains("\"type\":\"MultiPolygon\"")) {
            return coordinates.length() >= 2 ? coordinates.substring(1, coordinates.length() - 1) : null;
        }
        return null;
    }

    private int countTopLevelPolygons(String multiPolygonCoordinateBlock) {
        if (multiPolygonCoordinateBlock == null || multiPolygonCoordinateBlock.trim().isEmpty()) {
            return 0;
        }

        int count = 0;
        int depth = 0;
        for (int index = 0; index < multiPolygonCoordinateBlock.length(); index++) {
            char ch = multiPolygonCoordinateBlock.charAt(index);
            if (ch == '[') {
                depth++;
                if (depth == 1) {
                    count++;
                }
            } else if (ch == ']') {
                depth--;
            }
        }
        return count;
    }

    private String unwrapTopLevelPolygon(String multiPolygonCoordinateBlock) {
        if (multiPolygonCoordinateBlock == null || multiPolygonCoordinateBlock.trim().isEmpty()) {
            return "[]";
        }
        String compact = multiPolygonCoordinateBlock.trim();
        if (compact.startsWith("[") && compact.endsWith("]")) {
            return compact.substring(1, compact.length() - 1);
        }
        return compact;
    }

    private int findMatchingBracket(String value, int startIndex) {
        int depth = 0;
        for (int index = startIndex; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private Map<String, String> buildMetadata(
            DwdCapParser.ParsedAlert alert,
            List<WarningAreaRef> areaRefs,
            WarningGeometry geometry) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("dwd.identifier", alert == null ? null : alert.getIdentifier());
        metadata.put("dwd.language", alert == null || alert.getInfo() == null ? null : alert.getInfo().getLanguage());
        metadata.put("dwd.sender", alert == null ? null : alert.getSender());
        metadata.put("dwd.references", alert == null ? null : alert.getReferences());
        metadata.put("dwd.referenceIds", alert == null ? null : join(alert.getReferenceIdentifiers()));
        metadata.put("dwd.canonicalAlertKey", canonicalAlertKey(alert));
        metadata.put("dwd.areaDescriptions", joinAreaDescriptions(areaRefs));
        metadata.put("dwd.warnCellIds", joinWarnCellIds(areaRefs));
        metadata.put("dwd.geometrySource", geometry == null ? null : geometry.getGeometrySource());
        metadata.put("dwd.geometryConfidence", geometry == null ? null : geometry.getGeometryConfidence().name());
        metadata.put("dwd.displayAccuracy", displayAccuracyKey(geometry, resolveRenderMode(geometry)));
        return metadata;
    }

    private WarningSeverity mapSeverity(String value) {
        if ("Extreme".equalsIgnoreCase(value)) {
            return WarningSeverity.EXTREME;
        }
        if ("Severe".equalsIgnoreCase(value)) {
            return WarningSeverity.SEVERE;
        }
        if ("Moderate".equalsIgnoreCase(value)) {
            return WarningSeverity.MODERATE;
        }
        if ("Minor".equalsIgnoreCase(value)) {
            return WarningSeverity.MINOR;
        }
        return WarningSeverity.UNKNOWN;
    }

    private WarningUrgency mapUrgency(String value) {
        if ("Immediate".equalsIgnoreCase(value)) {
            return WarningUrgency.IMMEDIATE;
        }
        if ("Expected".equalsIgnoreCase(value)) {
            return WarningUrgency.EXPECTED;
        }
        if ("Future".equalsIgnoreCase(value)) {
            return WarningUrgency.FUTURE;
        }
        if ("Past".equalsIgnoreCase(value)) {
            return WarningUrgency.PAST;
        }
        return WarningUrgency.UNKNOWN;
    }

    private RenderMode resolveRenderMode(WarningGeometry geometry) {
        if (geometry == null) {
            return RenderMode.LIST_ONLY;
        }
        if (isPolygonKind(geometry)
                && geometry.getGeoJsonGeometry() != null
                && !geometry.getGeoJsonGeometry().trim().isEmpty()) {
            return RenderMode.POLYGON;
        }
        if (isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon())) {
            return RenderMode.MARKER;
        }
        return RenderMode.LIST_ONLY;
    }

    private String displayAccuracyKey(WarningGeometry geometry, RenderMode renderMode) {
        if (renderMode == RenderMode.POLYGON && geometry != null && geometry.getGeometryConfidence() == GeometryConfidence.CONFIRMED) {
            return "confirmed_polygon";
        }
        if (renderMode == RenderMode.POLYGON && geometry != null
                && (geometry.getGeometryConfidence() == GeometryConfidence.APPROXIMATE || geometry.isApproximate())) {
            return "approximate_area";
        }
        if (renderMode == RenderMode.MARKER) {
            return "marker_fallback";
        }
        return "list_only";
    }

    private String joinAreaDescriptions(List<WarningAreaRef> areaRefs) {
        List<String> values = new ArrayList<>();
        if (areaRefs != null) {
            for (WarningAreaRef areaRef : areaRefs) {
                if (areaRef != null && areaRef.getAreaName() != null && !areaRef.getAreaName().trim().isEmpty()) {
                    values.add(areaRef.getAreaName().trim());
                }
            }
        }
        return join(values);
    }

    private String joinWarnCellIds(List<WarningAreaRef> areaRefs) {
        List<String> values = new ArrayList<>();
        if (areaRefs != null) {
            for (WarningAreaRef areaRef : areaRefs) {
                if (areaRef != null && areaRef.getWarnCellId() != null && !areaRef.getWarnCellId().trim().isEmpty()) {
                    values.add(areaRef.getWarnCellId().trim());
                }
            }
        }
        return join(values);
    }

    private String firstAreaDescription(List<DwdCapParser.ParsedArea> areas) {
        if (areas == null) {
            return null;
        }
        for (DwdCapParser.ParsedArea area : areas) {
            if (area != null && area.getAreaDesc() != null && !area.getAreaDesc().trim().isEmpty()) {
                return area.getAreaDesc().trim();
            }
        }
        return null;
    }

    private void mergeBbox(double[] target, double[] source) {
        if (!hasUsableBbox(source)) {
            return;
        }
        target[0] = Math.min(target[0], source[0]);
        target[1] = Math.min(target[1], source[1]);
        target[2] = Math.max(target[2], source[2]);
        target[3] = Math.max(target[3], source[3]);
    }

    private GeometryConfidence mergeConfidence(GeometryConfidence current, GeometryConfidence candidate) {
        if (candidate == GeometryConfidence.APPROXIMATE || current == GeometryConfidence.APPROXIMATE) {
            return GeometryConfidence.APPROXIMATE;
        }
        if (candidate == GeometryConfidence.CONFIRMED || current == GeometryConfidence.CONFIRMED) {
            return GeometryConfidence.CONFIRMED;
        }
        return GeometryConfidence.NONE;
    }

    private boolean isTrustedOfficialGeometry(WarningGeometry geometry) {
        if (geometry == null) {
            return false;
        }
        GeometryConfidence confidence = geometry.getGeometryConfidence();
        if (confidence != GeometryConfidence.CONFIRMED
                && confidence != GeometryConfidence.APPROXIMATE) {
            return false;
        }
        String geometrySource = geometry.getGeometrySource();
        return geometrySource != null && geometrySource.startsWith("DWD_OFFICIAL");
    }

    private WarningGeometry copyGeometry(WarningGeometry geometry) {
        WarningGeometry copy = new WarningGeometry();
        copy.setKind(geometry.getKind());
        copy.setGeoJsonGeometry(geometry.getGeoJsonGeometry());
        copy.setCentroidLat(geometry.getCentroidLat());
        copy.setCentroidLon(geometry.getCentroidLon());
        copy.setBbox(geometry.getBbox());
        copy.setApproximate(geometry.isApproximate());
        copy.setGeometrySource(geometry.getGeometrySource());
        copy.setGeometryConfidence(geometry.getGeometryConfidence());
        return copy;
    }

    private boolean hasGeometry(WarningGeometry geometry) {
        if (geometry == null) {
            return false;
        }
        if ((geometry.getKind() == GeometryKind.POLYGON || geometry.getKind() == GeometryKind.MULTI_POLYGON)
                && geometry.getGeoJsonGeometry() != null
                && !geometry.getGeoJsonGeometry().trim().isEmpty()) {
            return true;
        }
        return geometry.getKind() == GeometryKind.POINT
                && isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon());
    }

    private boolean hasUsableBbox(double[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return false;
        }
        return !(bbox[0] == 0D && bbox[1] == 0D && bbox[2] == 0D && bbox[3] == 0D)
                && !Double.isInfinite(bbox[0])
                && !Double.isInfinite(bbox[1])
                && !Double.isInfinite(bbox[2])
                && !Double.isInfinite(bbox[3]);
    }

    private double[] normalizeBbox(double[] bbox) {
        if (!hasUsableBbox(bbox)) {
            return new double[4];
        }
        return new double[] {bbox[0], bbox[1], bbox[2], bbox[3]};
    }

    private boolean isRenderableCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !(latitude == 0D && longitude == 0D);
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private String firstNonEmpty(String first, String second, String third) {
        String value = firstNonEmpty(first, second);
        if (value != null) {
            return value;
        }
        if (third != null && !third.trim().isEmpty()) {
            return third.trim();
        }
        return null;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String join(Set<String> values) {
        return join(new ArrayList<>(values));
    }

}
