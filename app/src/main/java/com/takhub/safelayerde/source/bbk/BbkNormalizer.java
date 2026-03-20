package com.takhub.safelayerde.source.bbk;

import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.WarningAreaRef;
import com.takhub.safelayerde.domain.model.WarningGeometry;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSeverity;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.model.WarningUrgency;
import com.takhub.safelayerde.util.JsonUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BbkNormalizer {

    private static final String VERSION_FALLBACK = "0";

    public WarningRecord normalize(
            String warningId,
            JSONObject discoveryEntry,
            Set<String> discoveryArsSet,
            JSONObject detailJson,
            WarningGeometry geometry,
            WarningRecord previousRecord,
            long fetchedAtMs) {
        JSONObject discoveryData = payloadData(discoveryEntry);
        JSONObject detailData = payloadData(detailJson);
        JSONObject discoveryInfo = preferredInfo(discoveryEntry);
        JSONObject detailInfo = preferredInfo(detailJson);

        WarningRecord record = new WarningRecord();
        record.setStableId("BBK:" + warningId);
        record.setSourceType(WarningSourceType.BBK);
        record.setSourceId(warningId);

        String sent = firstNonEmpty(
                JsonUtils.optString(detailJson, "sent", null),
                JsonUtils.optString(detailData, "sent", null),
                JsonUtils.optString(discoveryEntry, "sent", null),
                JsonUtils.optString(discoveryData, "sent", null),
                JsonUtils.optString(discoveryEntry, "startDate", null),
                JsonUtils.optString(discoveryEntry, "version", null),
                VERSION_FALLBACK);
        record.setVersion(sent);

        record.setTitle(firstNonEmpty(
                JsonUtils.optString(detailInfo, "headline", null),
                JsonUtils.optString(detailData, "headline", null),
                localizedString(detailJson, "i18nTitle"),
                JsonUtils.optString(detailJson, "headline", null),
                JsonUtils.optString(discoveryInfo, "headline", null),
                JsonUtils.optString(discoveryData, "headline", null),
                localizedString(discoveryEntry, "i18nTitle"),
                JsonUtils.optString(discoveryEntry, "headline", null),
                warningId));
        record.setSourceLabel("BBK/NINA");
        record.setSeverity(mapSeverity(firstNonEmpty(
                JsonUtils.optString(detailInfo, "severity", null),
                JsonUtils.optString(detailData, "severity", null),
                JsonUtils.optString(detailJson, "severity", null),
                JsonUtils.optString(discoveryInfo, "severity", null),
                JsonUtils.optString(discoveryData, "severity", null),
                JsonUtils.optString(discoveryEntry, "severity", null),
                null)));
        record.setUrgency(mapUrgency(firstNonEmpty(
                JsonUtils.optString(detailInfo, "urgency", null),
                JsonUtils.optString(detailData, "urgency", null),
                JsonUtils.optString(detailJson, "urgency", null),
                JsonUtils.optString(discoveryInfo, "urgency", null),
                JsonUtils.optString(discoveryData, "urgency", null),
                JsonUtils.optString(discoveryEntry, "urgency", null),
                null)));
        record.setMsgType(firstNonEmpty(
                JsonUtils.optString(detailJson, "msgType", null),
                JsonUtils.optString(detailData, "msgType", null),
                JsonUtils.optString(discoveryEntry, "msgType", null),
                JsonUtils.optString(discoveryData, "msgType", null),
                JsonUtils.optString(discoveryEntry, "type", null),
                JsonUtils.optString(discoveryData, "type", null),
                previousRecord == null ? null : previousRecord.getMsgType()));
        record.setStatus(firstNonEmpty(
                JsonUtils.optString(detailJson, "status", null),
                JsonUtils.optString(detailData, "status", null),
                JsonUtils.optString(discoveryEntry, "status", null),
                JsonUtils.optString(discoveryData, "status", null),
                "Actual"));
        record.setDescriptionText(firstNonEmpty(
                JsonUtils.optString(detailInfo, "description", null),
                JsonUtils.optString(detailData, "description", null),
                JsonUtils.optString(discoveryInfo, "description", null),
                JsonUtils.optString(discoveryData, "description", null),
                previousRecord == null ? null : previousRecord.getDescriptionText()));
        record.setInstructionText(firstNonEmpty(
                JsonUtils.optString(detailInfo, "instruction", null),
                JsonUtils.optString(detailData, "instruction", null),
                JsonUtils.optString(discoveryInfo, "instruction", null),
                JsonUtils.optString(discoveryData, "instruction", null),
                previousRecord == null ? null : previousRecord.getInstructionText()));
        record.setSentAtEpochMs(parseIso8601(sent));
        long onsetAt = firstNonZero(
                parseIso8601(JsonUtils.optString(detailInfo, "onset", null)),
                parseIso8601(JsonUtils.optString(detailInfo, "effective", null)),
                parseIso8601(JsonUtils.optString(detailData, "onset", null)),
                parseIso8601(JsonUtils.optString(detailData, "effective", null)),
                parseIso8601(JsonUtils.optString(discoveryInfo, "onset", null)),
                parseIso8601(JsonUtils.optString(discoveryData, "onset", null)),
                parseIso8601(JsonUtils.optString(discoveryEntry, "startDate", null)),
                previousRecord == null ? 0L : previousRecord.getOnsetAtEpochMs());
        record.setOnsetAtEpochMs(onsetAt);
        record.setExpiresAtEpochMs(firstNonZero(
                parseIso8601(JsonUtils.optString(detailInfo, "expires", null)),
                parseIso8601(JsonUtils.optString(detailData, "expires", null)),
                parseIso8601(JsonUtils.optString(discoveryInfo, "expires", null)),
                parseIso8601(JsonUtils.optString(discoveryData, "expires", null)),
                parseIso8601(JsonUtils.optString(discoveryEntry, "expiresDate", null)),
                previousRecord == null ? 0L : previousRecord.getExpiresAtEpochMs()));
        record.setEffectiveAtEpochMs(firstNonZero(
                parseIso8601(JsonUtils.optString(detailInfo, "effective", null)),
                parseIso8601(JsonUtils.optString(detailData, "effective", null)),
                onsetAt));
        record.setLastFetchedAtEpochMs(fetchedAtMs);
        record.setGeometry(resolveGeometry(geometry, detailData, discoveryData, previousRecord));
        record.setRenderMode(resolveRenderMode(record.getGeometry()));
        record.setAreaRefs(buildAreaRefs(detailInfo, detailData, discoveryInfo, discoveryData, previousRecord));
        record.setMetadata(buildMetadata(warningId, discoveryArsSet, record.getAreaRefs()));
        return record;
    }

    private JSONObject payloadData(JSONObject source) {
        JSONObject payload = source == null ? null : source.optJSONObject("payload");
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        return data == null ? source : data;
    }

    private WarningGeometry emptyGeometry() {
        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(GeometryKind.NONE);
        geometry.setGeometryConfidence(GeometryConfidence.NONE);
        return geometry;
    }

    private List<WarningAreaRef> buildAreaRefs(
            JSONObject detailInfo,
            JSONObject detailData,
            JSONObject discoveryInfo,
            JSONObject discoveryData,
            WarningRecord previousRecord) {
        List<WarningAreaRef> refs = new ArrayList<>();
        JSONObject area = firstArea(detailInfo);
        if (area == null) {
            area = firstArea(detailData);
        }
        if (area == null) {
            area = firstArea(discoveryInfo);
        }
        if (area == null) {
            area = firstArea(discoveryData);
        }
        if (area == null && previousRecord != null && previousRecord.getAreaRefs() != null) {
            return new ArrayList<>(previousRecord.getAreaRefs());
        }
        if (area == null) {
            return refs;
        }

        String areaName = firstNonEmpty(
                JsonUtils.optString(area, "name", null),
                JsonUtils.optString(area, "areaDesc", null),
                null);
        String areaType = JsonUtils.optString(area, "type", null);
        String warnCellId = JsonUtils.optString(area, "warnCellId", null);
        refs.add(createAreaRef(areaName, areaType, warnCellId));
        return refs;
    }

    WarningAreaRef createAreaRef(String areaName, String areaType, String warnCellId) {
        WarningAreaRef areaRef = new WarningAreaRef();
        areaRef.setAreaName(areaName);
        areaRef.setAreaId((areaType == null ? "" : areaType) + ":" + areaName);
        areaRef.setWarnCellId(warnCellId);
        return areaRef;
    }

    private Map<String, String> buildMetadata(
            String warningId,
            Set<String> discoveryArsSet,
            List<WarningAreaRef> areaRefs) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("bbk.warningId", warningId);
        if (discoveryArsSet != null && !discoveryArsSet.isEmpty()) {
            metadata.put("bbk.discoveryArs", join(discoveryArsSet));
        }
        if (areaRefs != null && !areaRefs.isEmpty() && areaRefs.get(0) != null) {
            metadata.put("bbk.areaName", areaRefs.get(0).getAreaName());
        }
        return metadata;
    }

    private WarningGeometry resolveGeometry(
            WarningGeometry geometry,
            JSONObject detailData,
            JSONObject discoveryData,
            WarningRecord previousRecord) {
        WarningGeometry resolved = copyGeometry(geometry);
        if (hasRenderableGeometry(resolved)) {
            return resolved;
        }

        WarningGeometry bboxGeometry = geometryFromArea(detailData);
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        bboxGeometry = geometryFromPayload(detailData);
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        bboxGeometry = geometryFromArea(discoveryData);
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        bboxGeometry = geometryFromPayload(discoveryData);
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        if (previousRecord != null) {
            WarningGeometry previousGeometry = copyGeometry(previousRecord.getGeometry());
            if (hasRenderableGeometry(previousGeometry)) {
                return previousGeometry;
            }
        }
        return resolved == null ? emptyGeometry() : resolved;
    }

    private WarningGeometry geometryFromArea(JSONObject payloadData) {
        JSONObject area = firstArea(payloadData);
        if (area == null) {
            return null;
        }

        WarningGeometry bboxGeometry = geometryFromBbox(area.optJSONArray("bbox"));
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        bboxGeometry = geometryFromCenter(
                area.optDouble("lat", Double.NaN),
                area.optDouble("lon", Double.NaN));
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        bboxGeometry = geometryFromCenter(
                area.optDouble("latitude", Double.NaN),
                area.optDouble("longitude", Double.NaN));
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        JSONObject center = area.optJSONObject("center");
        if (center != null) {
            bboxGeometry = geometryFromCenter(
                    center.optDouble("lat", center.optDouble("latitude", Double.NaN)),
                    center.optDouble("lon", center.optDouble("longitude", Double.NaN)));
            if (hasRenderableGeometry(bboxGeometry)) {
                return bboxGeometry;
            }
        }

        JSONArray coordinate = area.optJSONArray("coordinate");
        if (coordinate != null && coordinate.length() >= 2) {
            return geometryFromCenter(
                    coordinate.optDouble(1, Double.NaN),
                    coordinate.optDouble(0, Double.NaN));
        }
        return null;
    }

    private WarningGeometry geometryFromPayload(JSONObject payloadData) {
        if (payloadData == null) {
            return null;
        }

        WarningGeometry bboxGeometry = geometryFromBbox(payloadData.optJSONArray("bbox"));
        if (hasRenderableGeometry(bboxGeometry)) {
            return bboxGeometry;
        }

        return geometryFromCenter(
                payloadData.optDouble("lat", payloadData.optDouble("latitude", Double.NaN)),
                payloadData.optDouble("lon", payloadData.optDouble("longitude", Double.NaN)));
    }

    private JSONObject preferredInfo(JSONObject source) {
        if (source == null) {
            return null;
        }

        JSONArray infos = source.optJSONArray("info");
        if (infos == null || infos.length() == 0) {
            return null;
        }

        JSONObject languageMatch = null;
        JSONObject first = null;
        for (int index = 0; index < infos.length(); index++) {
            JSONObject info = infos.optJSONObject(index);
            if (info == null) {
                continue;
            }
            if (first == null) {
                first = info;
            }

            String language = JsonUtils.optString(info, "language", null);
            if ("de".equalsIgnoreCase(language)) {
                return info;
            }
            if (languageMatch == null
                    && language != null
                    && language.toLowerCase(Locale.GERMANY).startsWith("de")) {
                languageMatch = info;
            }
        }

        return languageMatch == null ? first : languageMatch;
    }

    private WarningGeometry geometryFromBbox(JSONArray bboxArray) {
        if (bboxArray == null || bboxArray.length() < 4) {
            return null;
        }

        double[] bbox = new double[4];
        for (int index = 0; index < 4; index++) {
            bbox[index] = bboxArray.optDouble(index, 0D);
        }

        if (!hasUsableBbox(bbox)) {
            return null;
        }

        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(GeometryKind.NONE);
        geometry.setBbox(bbox);
        geometry.setCentroidLon((bbox[0] + bbox[2]) / 2D);
        geometry.setCentroidLat((bbox[1] + bbox[3]) / 2D);
        geometry.setApproximate(true);
        geometry.setGeometrySource("BBK_BBOX");
        geometry.setGeometryConfidence(GeometryConfidence.APPROXIMATE);
        return geometry;
    }

    private WarningGeometry geometryFromCenter(double latitude, double longitude) {
        if (!isRenderableCoordinate(latitude, longitude)) {
            return null;
        }

        WarningGeometry geometry = new WarningGeometry();
        geometry.setKind(GeometryKind.POINT);
        geometry.setCentroidLat(latitude);
        geometry.setCentroidLon(longitude);
        geometry.setBbox(new double[] {longitude, latitude, longitude, latitude});
        geometry.setApproximate(true);
        geometry.setGeometrySource("BBK_AREA_CENTER");
        geometry.setGeometryConfidence(GeometryConfidence.APPROXIMATE);
        return geometry;
    }

    private JSONObject firstArea(JSONObject payloadData) {
        if (payloadData == null) {
            return null;
        }

        JSONObject area = payloadData.optJSONObject("area");
        if (area != null) {
            return area;
        }

        JSONArray areas = payloadData.optJSONArray("area");
        return areas == null ? null : areas.optJSONObject(0);
    }

    private boolean hasRenderableGeometry(WarningGeometry geometry) {
        if (geometry == null) {
            return false;
        }
        if ((geometry.getKind() == GeometryKind.POLYGON || geometry.getKind() == GeometryKind.MULTI_POLYGON)
                && geometry.getGeoJsonGeometry() != null
                && !geometry.getGeoJsonGeometry().trim().isEmpty()) {
            return true;
        }
        if (isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon())) {
            return true;
        }
        return hasUsableBbox(geometry.getBbox());
    }

    private boolean hasUsableBbox(double[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return false;
        }
        return !(bbox[0] == 0D && bbox[1] == 0D && bbox[2] == 0D && bbox[3] == 0D);
    }

    private boolean isRenderableCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !(latitude == 0D && longitude == 0D);
    }

    private WarningGeometry copyGeometry(WarningGeometry geometry) {
        if (geometry == null) {
            return null;
        }

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
        if ((geometry.getKind() == GeometryKind.POLYGON || geometry.getKind() == GeometryKind.MULTI_POLYGON)
                && geometry.getGeoJsonGeometry() != null
                && !geometry.getGeoJsonGeometry().trim().isEmpty()) {
            return RenderMode.POLYGON;
        }
        if (isRenderableCoordinate(geometry.getCentroidLat(), geometry.getCentroidLon()) || hasUsableBbox(geometry.getBbox())) {
            return RenderMode.MARKER;
        }
        return RenderMode.LIST_ONLY;
    }

    private long parseIso8601(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String normalized = normalizeIso8601Offset(value.trim());

        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(normalized).getTime();
        } catch (ParseException | NullPointerException ignored) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignoredToo) {
                return 0L;
            }
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

    private String localizedString(JSONObject source, String key) {
        if (source == null || key == null || source.isNull(key)) {
            return null;
        }

        Object value = source.opt(key);
        if (value instanceof JSONObject) {
            JSONObject localized = (JSONObject) value;
            String preferred = firstNonEmpty(
                    JsonUtils.optString(localized, "de", null),
                    JsonUtils.optString(localized, "DE", null),
                    null);
            if (preferred != null) {
                return preferred;
            }

            JSONArray names = localized.names();
            if (names == null) {
                return null;
            }
            for (int index = 0; index < names.length(); index++) {
                String childKey = names.optString(index, null);
                String childValue = JsonUtils.optString(localized, childKey, null);
                if (childValue != null && !childValue.trim().isEmpty()) {
                    return childValue;
                }
            }
            return null;
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            return stringValue.trim().isEmpty() ? null : stringValue;
        }
        return String.valueOf(value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private long firstNonZero(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private String join(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : new LinkedHashSet<>(values)) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }
}
