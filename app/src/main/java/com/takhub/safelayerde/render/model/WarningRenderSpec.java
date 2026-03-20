package com.takhub.safelayerde.render.model;

import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.WarningGeometry;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSeverity;
import com.takhub.safelayerde.domain.model.WarningSourceType;

public class WarningRenderSpec {

    private final String stableId;
    private final WarningSourceType sourceType;
    private final RenderMode renderMode;
    private final WarningSeverity severity;
    private final String geoJsonGeometry;
    private final double centroidLat;
    private final double centroidLon;
    private final String geometrySignature;
    private final int polygonCount;
    private final String title;
    private final boolean approximate;
    private final GeometryConfidence geometryConfidence;

    public WarningRenderSpec(
            String stableId,
            WarningSourceType sourceType,
            RenderMode renderMode,
            WarningSeverity severity,
            String geoJsonGeometry,
            double centroidLat,
            double centroidLon,
            String geometrySignature,
            int polygonCount,
            String title,
            boolean approximate,
            GeometryConfidence geometryConfidence) {
        this.stableId = stableId;
        this.sourceType = sourceType;
        this.renderMode = renderMode;
        this.severity = severity;
        this.geoJsonGeometry = geoJsonGeometry;
        this.centroidLat = centroidLat;
        this.centroidLon = centroidLon;
        this.geometrySignature = geometrySignature;
        this.polygonCount = polygonCount;
        this.title = title;
        this.approximate = approximate;
        this.geometryConfidence = geometryConfidence == null
                ? GeometryConfidence.NONE
                : geometryConfidence;
    }

    public static WarningRenderSpec from(WarningRecord record) {
        WarningGeometry geometry = record == null ? null : record.getGeometry();
        String geoJsonGeometry = geometry == null ? null : geometry.getGeoJsonGeometry();
        double centroidLat = fallbackCentroidLat(geometry);
        double centroidLon = fallbackCentroidLon(geometry);
        RenderMode renderMode = resolveRenderMode(record, geometry, geoJsonGeometry, centroidLat, centroidLon);
        return new WarningRenderSpec(
                record == null ? null : record.getStableId(),
                record == null ? null : record.getSourceType(),
                renderMode,
                record == null ? WarningSeverity.UNKNOWN : record.getSeverity(),
                geoJsonGeometry,
                centroidLat,
                centroidLon,
                buildGeometrySignature(geoJsonGeometry),
                countPolygons(geoJsonGeometry),
                record == null ? null : record.getTitle(),
                geometry != null && geometry.isApproximate(),
                geometry == null ? GeometryConfidence.NONE : geometry.getGeometryConfidence());
    }

    public String getStableId() {
        return stableId;
    }

    public WarningSourceType getSourceType() {
        return sourceType;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public WarningSeverity getSeverity() {
        return severity;
    }

    public String getGeoJsonGeometry() {
        return geoJsonGeometry;
    }

    public double getCentroidLat() {
        return centroidLat;
    }

    public double getCentroidLon() {
        return centroidLon;
    }

    public String getGeometrySignature() {
        return geometrySignature;
    }

    public int getPolygonCount() {
        return polygonCount;
    }

    public boolean hasRenderableLocation() {
        return isRenderableCoordinate(centroidLat, centroidLon);
    }

    public boolean hasFocusableLocation() {
        return renderMode != RenderMode.LIST_ONLY && hasRenderableLocation();
    }

    public String getTitle() {
        return title;
    }

    public boolean isApproximate() {
        return approximate;
    }

    public GeometryConfidence getGeometryConfidence() {
        return geometryConfidence;
    }

    private static RenderMode resolveRenderMode(
            WarningRecord record,
            WarningGeometry geometry,
            String geoJsonGeometry,
            double centroidLat,
            double centroidLon) {
        RenderMode explicitRenderMode = record == null ? null : record.getRenderMode();
        if (explicitRenderMode != null) {
            if (explicitRenderMode == RenderMode.POLYGON && hasPolygonGeometry(geometry, geoJsonGeometry)) {
                return RenderMode.POLYGON;
            }
            if (explicitRenderMode == RenderMode.MARKER && isRenderableCoordinate(centroidLat, centroidLon)) {
                return RenderMode.MARKER;
            }
            if (explicitRenderMode == RenderMode.LIST_ONLY) {
                return RenderMode.LIST_ONLY;
            }
        }

        if (hasPolygonGeometry(geometry, geoJsonGeometry)) {
            return RenderMode.POLYGON;
        }
        if (geometry != null
                && geometry.getGeometryConfidence() != GeometryConfidence.NONE
                && isRenderableCoordinate(centroidLat, centroidLon)) {
            return RenderMode.MARKER;
        }
        if (isRenderableCoordinate(centroidLat, centroidLon)) {
            return RenderMode.MARKER;
        }
        return RenderMode.LIST_ONLY;
    }

    private static boolean hasPolygonGeometry(WarningGeometry geometry, String geoJsonGeometry) {
        return geometry != null
                && (geometry.getKind() == GeometryKind.POLYGON || geometry.getKind() == GeometryKind.MULTI_POLYGON)
                && geoJsonGeometry != null
                && !geoJsonGeometry.trim().isEmpty();
    }

    private static double fallbackCentroidLat(WarningGeometry geometry) {
        if (geometry == null) {
            return 0D;
        }
        if (isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon())) {
            return geometry.getCentroidLat();
        }

        double[] bbox = geometry.getBbox();
        return hasUsableBbox(bbox) ? (bbox[1] + bbox[3]) / 2D : 0D;
    }

    private static double fallbackCentroidLon(WarningGeometry geometry) {
        if (geometry == null) {
            return 0D;
        }
        if (isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon())) {
            return geometry.getCentroidLon();
        }

        double[] bbox = geometry.getBbox();
        return hasUsableBbox(bbox) ? (bbox[0] + bbox[2]) / 2D : 0D;
    }

    private static boolean hasUsableBbox(double[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return false;
        }
        return !(bbox[0] == 0D && bbox[1] == 0D && bbox[2] == 0D && bbox[3] == 0D);
    }

    private static boolean isRenderableCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !(latitude == 0D && longitude == 0D);
    }

    private static String buildGeometrySignature(String geoJsonGeometry) {
        return geoJsonGeometry == null || geoJsonGeometry.trim().isEmpty()
                ? null
                : Integer.toHexString(geoJsonGeometry.hashCode());
    }

    private static int countPolygons(String geoJsonGeometry) {
        if (geoJsonGeometry == null || geoJsonGeometry.trim().isEmpty()) {
            return 0;
        }

        if (containsCompactToken(geoJsonGeometry, "\"type\":\"Polygon\"")) {
            return 1;
        }
        if (!containsCompactToken(geoJsonGeometry, "\"type\":\"MultiPolygon\"")) {
            return 0;
        }

        int coordinatesIndex = indexOfCompactToken(geoJsonGeometry, "\"coordinates\":");
        if (coordinatesIndex < 0) {
            return 0;
        }

        int arrayStart = indexOfCompactChar(geoJsonGeometry, '[', coordinatesIndex);
        if (arrayStart < 0) {
            return 0;
        }

        int depth = 0;
        int polygonCount = 0;
        boolean insideTopLevel = false;
        for (int index = arrayStart; index < geoJsonGeometry.length(); index++) {
            char ch = geoJsonGeometry.charAt(index);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '[') {
                depth++;
                if (depth == 2 && insideTopLevel) {
                    polygonCount++;
                } else if (depth == 1) {
                    insideTopLevel = true;
                }
            } else if (ch == ']') {
                depth--;
                if (insideTopLevel && depth == 0) {
                    break;
                }
            }
        }

        return polygonCount;
    }

    private static boolean containsCompactToken(String value, String token) {
        return indexOfCompactToken(value, token) >= 0;
    }

    private static int indexOfCompactToken(String value, String token) {
        if (value == null || token == null || token.isEmpty()) {
            return -1;
        }

        int matched = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == token.charAt(matched)) {
                matched++;
                if (matched == token.length()) {
                    return index;
                }
                continue;
            }
            matched = ch == token.charAt(0) ? 1 : 0;
            if (matched == token.length()) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfCompactChar(String value, char target, int startIndex) {
        if (value == null) {
            return -1;
        }
        for (int index = Math.max(0, startIndex); index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == target) {
                return index;
            }
        }
        return -1;
    }
}
