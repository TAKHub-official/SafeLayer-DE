package com.takhub.safelayerde.source.radar;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.source.common.HttpClient;
import com.takhub.safelayerde.util.StringUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DwdRadarFetcher {

    private static final String TAG = "SafeLayerRadarFetch";
    private static final String DISALLOW_DOCTYPE_FEATURE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD_FEATURE =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String[] ISO_PATTERNS = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mmX"
    };

    private final HttpClient httpClient;

    public DwdRadarFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FetchResult fetchLatestFrame(DwdRadarProduct product) throws IOException {
        if (product == null) {
            throw new IOException("Radar product is required.");
        }

        String productId = product.getProductId();
        SafeLayerDebugLog.i(TAG, "capabilities-fetch-start productId=" + productId);
        try {
            String capabilitiesXml = httpClient.fetchString(product.getCapabilitiesUrl());
            CapabilitiesMetadata metadata = parseCapabilities(capabilitiesXml, product);
            SafeLayerDebugLog.i(TAG, "capabilities-fetch-success productId=" + productId
                    + ", layer=" + metadata.layerName
                    + ", style=" + metadata.styleName
                    + ", imageFormat=" + metadata.imageFormat
                    + ", frameEpochMs=" + metadata.frameEpochMs);
            String requestUrl = buildGetMapUrl(product, metadata);
            SafeLayerDebugLog.i(TAG, "getmap-request-start productId=" + productId
                    + ", frameEpochMs=" + metadata.frameEpochMs);
            byte[] imageBytes = httpClient.fetchBytes(requestUrl);
            SafeLayerDebugLog.i(TAG, "getmap-request-success productId=" + productId
                    + ", frameEpochMs=" + metadata.frameEpochMs
                    + ", bytes=" + imageBytes.length);
            return new FetchResult(
                    product,
                    metadata.layerName,
                    metadata.styleName,
                    metadata.imageFormat,
                    metadata.frameEpochMs,
                    requestUrl,
                    imageBytes);
        } catch (IOException exception) {
            SafeLayerDebugLog.e(TAG, "fetch-latest-frame-failed productId=" + productId, exception);
            throw exception;
        } catch (RuntimeException exception) {
            SafeLayerDebugLog.e(TAG, "fetch-latest-frame-runtime-failed productId=" + productId, exception);
            throw exception;
        }
    }

    private CapabilitiesMetadata parseCapabilities(String capabilitiesXml, DwdRadarProduct product) throws IOException {
        try {
            rejectUnsafeXmlConstructs(capabilitiesXml);
            DocumentBuilderFactory factory = newDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            trySetXIncludeAware(factory, false);
            factory.setExpandEntityReferences(false);
            requireFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            requireFeature(factory, DISALLOW_DOCTYPE_FEATURE, true);
            requireFeature(factory, EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            requireFeature(factory, EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            requireFeature(factory, LOAD_EXTERNAL_DTD_FEATURE, false);

            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new org.xml.sax.EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                    throw new SAXException("External entity resolution is disabled.");
                }
            });
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
            Document document = documentBuilder.parse(
                    new InputSource(new StringReader(capabilitiesXml)));

            Element layerElement = findLayerElement(document.getDocumentElement(), product.getLayerName());
            if (layerElement == null) {
                throw new IOException("Radar layer " + product.getLayerName() + " not found in capabilities.");
            }

            String imageFormat = resolveImageFormat(document.getDocumentElement(), product);
            String styleName = resolveStyleName(layerElement, product.getStyleName());
            long frameEpochMs = resolveLatestFrameEpochMs(layerElement);
            return new CapabilitiesMetadata(product.getLayerName(), styleName, imageFormat, frameEpochMs);
        } catch (IOException exception) {
            SafeLayerDebugLog.e(TAG, "capabilities-parse-failed productId="
                    + (product == null ? "unknown" : product.getProductId()), exception);
            throw exception;
        } catch (Exception exception) {
            SafeLayerDebugLog.e(TAG, "capabilities-parse-failed productId="
                    + (product == null ? "unknown" : product.getProductId()), exception);
            throw new IOException("Failed to parse radar capabilities.", exception);
        }
    }

    private void rejectUnsafeXmlConstructs(String capabilitiesXml) throws IOException {
        String normalizedXml = capabilitiesXml == null ? "" : capabilitiesXml.toUpperCase(Locale.US);
        if (normalizedXml.contains("<!DOCTYPE") || normalizedXml.contains("<!ENTITY")) {
            throw new IOException("Unsafe XML declarations are not allowed in radar capabilities.");
        }
    }

    protected DocumentBuilderFactory newDocumentBuilderFactory() {
        return DocumentBuilderFactory.newInstance();
    }

    private void requireFeature(DocumentBuilderFactory factory, String feature, boolean value) throws IOException {
        if (!trySetFeature(factory, feature, value)) {
            throw new IOException("Secure XML parser feature unavailable: " + feature);
        }
    }

    protected boolean trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
            return true;
        } catch (Exception ignored) {
            // Android XML implementations differ between test and runtime.
            return false;
        }
    }

    protected boolean trySetXIncludeAware(DocumentBuilderFactory factory, boolean value) {
        try {
            factory.setXIncludeAware(value);
            return true;
        } catch (RuntimeException ignored) {
            // Android's bundled parser can reject this configuration despite supporting WMS parsing itself.
            return false;
        }
    }

    private Element findLayerElement(Element parent, String layerName) {
        if (parent == null || layerName == null) {
            return null;
        }

        if ("Layer".equals(parent.getLocalName())
                && layerName.equals(readDirectChildText(parent, "Name"))) {
            return parent;
        }

        NodeList childNodes = parent.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            Node child = childNodes.item(index);
            if (!(child instanceof Element)) {
                continue;
            }
            Element match = findLayerElement((Element) child, layerName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private String resolveImageFormat(Element rootElement, DwdRadarProduct product) throws IOException {
        String requiredFormat = product == null ? null : product.getImageFormat();
        List<String> advertisedFormats = new ArrayList<>();
        NodeList nodes = rootElement.getElementsByTagNameNS("*", "GetMap");
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (!(node instanceof Element)) {
                continue;
            }

            NodeList formats = ((Element) node).getElementsByTagNameNS("*", "Format");
            for (int formatIndex = 0; formatIndex < formats.getLength(); formatIndex++) {
                String format = StringUtils.trimToNull(formats.item(formatIndex).getTextContent());
                if (format == null) {
                    continue;
                }
                if (format.startsWith("image/")) {
                    advertisedFormats.add(format);
                }
                if (requiredFormat != null && requiredFormat.equalsIgnoreCase(format)) {
                    return format;
                }
            }
        }

        String productId = product == null ? "unknown" : product.getProductId();
        StringBuilder message = new StringBuilder()
                .append("Required ")
                .append(productId)
                .append(" image format ")
                .append(requiredFormat)
                .append(" is unavailable in radar capabilities.");
        if (!advertisedFormats.isEmpty()) {
            message.append(" Advertised image formats: ");
            for (int index = 0; index < advertisedFormats.size(); index++) {
                if (index > 0) {
                    message.append(", ");
                }
                message.append(advertisedFormats.get(index));
            }
        }
        throw new IOException(message.toString());
    }

    private String resolveStyleName(Element layerElement, String preferredStyleName) {
        NodeList styleNodes = layerElement.getElementsByTagNameNS("*", "Style");
        String fallback = "";
        for (int index = 0; index < styleNodes.getLength(); index++) {
            Node styleNode = styleNodes.item(index);
            if (!(styleNode instanceof Element)) {
                continue;
            }
            String styleName = readDirectChildText((Element) styleNode, "Name");
            if (styleName == null) {
                continue;
            }
            if (preferredStyleName.equals(styleName)) {
                return styleName;
            }
            if (fallback.isEmpty()) {
                fallback = styleName;
            }
        }
        return fallback;
    }

    private long resolveLatestFrameEpochMs(Element layerElement) throws IOException {
        NodeList dimensions = layerElement.getElementsByTagNameNS("*", "Dimension");
        String defaultValue = null;
        List<String> timeCandidates = new ArrayList<>();
        for (int index = 0; index < dimensions.getLength(); index++) {
            Node node = dimensions.item(index);
            if (!(node instanceof Element)) {
                continue;
            }

            Element dimensionElement = (Element) node;
            String dimensionName = StringUtils.trimToNull(dimensionElement.getAttribute("name"));
            if (!"REFERENCE_TIME".equalsIgnoreCase(dimensionName)
                    && !"time".equalsIgnoreCase(dimensionName)) {
                continue;
            }

            defaultValue = preferConcreteTimestamp(
                    defaultValue,
                    StringUtils.trimToNull(dimensionElement.getAttribute("default")));
            collectTimeValues(StringUtils.trimToNull(dimensionElement.getTextContent()), timeCandidates);
            if ("REFERENCE_TIME".equalsIgnoreCase(dimensionName)) {
                break;
            }
        }

        String resolvedTimestamp = preferConcreteTimestamp(
                latestConcreteTimestamp(timeCandidates),
                defaultValue);
        if (resolvedTimestamp == null) {
            throw new IOException("Radar capabilities do not expose a concrete frame time.");
        }
        return parseIsoTimestamp(resolvedTimestamp);
    }

    private void collectTimeValues(String rawValue, List<String> target) {
        if (target == null) {
            return;
        }
        String value = StringUtils.trimToNull(rawValue);
        if (value == null) {
            return;
        }
        if (value.indexOf(',') >= 0) {
            String[] parts = value.split(",");
            for (String part : parts) {
                addTimeToken(target, part);
            }
            return;
        }
        addTimeToken(target, value);
    }

    private void addTimeToken(List<String> target, String rawToken) {
        String token = StringUtils.trimToNull(rawToken);
        if (token == null) {
            return;
        }
        if (token.indexOf('/') >= 0) {
            String[] parts = token.split("/");
            if (parts.length >= 2) {
                String end = StringUtils.trimToNull(parts[1]);
                if (end != null) {
                    target.add(end);
                }
            }
            return;
        }
        target.add(token);
    }

    private String latestConcreteTimestamp(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (int index = values.size() - 1; index >= 0; index--) {
            String candidate = StringUtils.trimToNull(values.get(index));
            if (candidate != null && !"current".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String preferConcreteTimestamp(String primaryValue, String fallbackValue) {
        String primary = StringUtils.trimToNull(primaryValue);
        if (primary != null && !"current".equalsIgnoreCase(primary)) {
            return primary;
        }
        String fallback = StringUtils.trimToNull(fallbackValue);
        if (fallback != null && !"current".equalsIgnoreCase(fallback)) {
            return fallback;
        }
        return null;
    }

    private long parseIsoTimestamp(String value) throws IOException {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new IOException("Radar timestamp is empty.");
        }
        for (String pattern : ISO_PATTERNS) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.US);
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsedDate = dateFormat.parse(normalized);
                if (parsedDate != null) {
                    return parsedDate.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        throw new IOException("Failed to parse radar timestamp: " + normalized);
    }

    private String buildGetMapUrl(DwdRadarProduct product, CapabilitiesMetadata metadata) throws IOException {
        try {
            return product.getWmsBaseUrl()
                    + "?SERVICE=WMS"
                    + "&VERSION=1.3.0"
                    + "&REQUEST=GetMap"
                    + "&LAYERS=" + encode(metadata.layerName)
                    + "&STYLES=" + encode(metadata.styleName)
                    + "&FORMAT=" + encode(metadata.imageFormat)
                    + "&TRANSPARENT=true"
                    + "&CRS=" + encode(product.getCrs())
                    + "&BBOX=" + encode(product.toWmsBbox())
                    + "&WIDTH=" + product.getWidth()
                    + "&HEIGHT=" + product.getHeight()
                    + "&TIME=" + encode(formatIsoTimestamp(metadata.frameEpochMs));
        } catch (IOException exception) {
            SafeLayerDebugLog.e(TAG, "getmap-request-build-failed productId="
                    + (product == null ? "unknown" : product.getProductId()), exception);
            throw exception;
        } catch (Exception exception) {
            SafeLayerDebugLog.e(TAG, "getmap-request-build-failed productId="
                    + (product == null ? "unknown" : product.getProductId()), exception);
            throw new IOException("Failed to build radar GetMap request.", exception);
        }
    }

    private String formatIsoTimestamp(long epochMs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date(epochMs));
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private String readDirectChildText(Element parent, String localName) {
        if (parent == null || localName == null) {
            return null;
        }

        NodeList childNodes = parent.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            Node child = childNodes.item(index);
            if (child instanceof Element && localName.equals(((Element) child).getLocalName())) {
                return StringUtils.trimToNull(child.getTextContent());
            }
        }
        return null;
    }

    private static final class CapabilitiesMetadata {

        private final String layerName;
        private final String styleName;
        private final String imageFormat;
        private final long frameEpochMs;

        private CapabilitiesMetadata(
                String layerName,
                String styleName,
                String imageFormat,
                long frameEpochMs) {
            this.layerName = layerName;
            this.styleName = styleName == null ? "" : styleName;
            this.imageFormat = imageFormat;
            this.frameEpochMs = frameEpochMs;
        }
    }

    public static final class FetchResult {

        private final DwdRadarProduct product;
        private final String layerName;
        private final String styleName;
        private final String imageFormat;
        private final long frameEpochMs;
        private final String requestUrl;
        private final byte[] imageBytes;

        FetchResult(
                DwdRadarProduct product,
                String layerName,
                String styleName,
                String imageFormat,
                long frameEpochMs,
                String requestUrl,
                byte[] imageBytes) {
            this.product = product;
            this.layerName = layerName;
            this.styleName = styleName;
            this.imageFormat = imageFormat;
            this.frameEpochMs = frameEpochMs;
            this.requestUrl = requestUrl;
            this.imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
        }

        public DwdRadarProduct getProduct() {
            return product;
        }

        public String getLayerName() {
            return layerName;
        }

        public String getStyleName() {
            return styleName;
        }

        public String getImageFormat() {
            return imageFormat;
        }

        public long getFrameEpochMs() {
            return frameEpochMs;
        }

        public String getRequestUrl() {
            return requestUrl;
        }

        public byte[] getImageBytes() {
            return imageBytes.clone();
        }
    }
}
