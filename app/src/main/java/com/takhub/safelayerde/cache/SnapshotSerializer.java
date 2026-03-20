package com.takhub.safelayerde.cache;

import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningAreaRef;
import com.takhub.safelayerde.domain.model.WarningGeometry;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSeverity;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.model.WarningUrgency;
import com.takhub.safelayerde.util.JsonUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotSerializer {

    private SnapshotSerializer() {
    }

    public static JSONObject toJson(WarningSnapshot snapshot) {
        JSONObject jsonObject = new JSONObject();
        if (snapshot == null) {
            return jsonObject;
        }

        put(jsonObject, "sourceType", valueOrNull(snapshot.getSourceType()));
        put(jsonObject, "fetchedAtEpochMs", snapshot.getFetchedAtEpochMs());

        JSONArray recordsJson = new JSONArray();
        for (WarningRecord record : snapshot.getRecords()) {
            add(recordsJson, toJson(record));
        }
        put(jsonObject, "records", recordsJson);
        return jsonObject;
    }

    public static WarningSnapshot fromJson(JSONObject jsonObject) {
        WarningSnapshot snapshot = new WarningSnapshot();
        if (jsonObject == null) {
            return snapshot;
        }

        snapshot.setSourceType(enumValue(
                WarningSourceType.class,
                JsonUtils.optString(jsonObject, "sourceType", null),
                null));
        snapshot.setFetchedAtEpochMs(JsonUtils.optLong(jsonObject, "fetchedAtEpochMs", 0L));

        JSONArray recordsArray = jsonObject.optJSONArray("records");
        List<WarningRecord> records = new ArrayList<>();
        if (recordsArray != null) {
            for (int index = 0; index < recordsArray.length(); index++) {
                JSONObject recordObject = recordsArray.optJSONObject(index);
                if (recordObject != null) {
                    records.add(fromWarningRecord(recordObject));
                }
            }
        }
        snapshot.setRecords(records);
        return snapshot;
    }

    public static JSONObject toJson(SourceState sourceState) {
        JSONObject jsonObject = new JSONObject();
        if (sourceState == null) {
            return jsonObject;
        }

        put(jsonObject, "sourceIdentity", valueOrNull(sourceState.getSourceIdentity()));
        put(jsonObject, "sourceType", valueOrNull(sourceState.getSourceType()));
        put(jsonObject, "status", valueOrNull(sourceState.getStatus()));
        put(jsonObject, "lastSuccessEpochMs", sourceState.getLastSuccessEpochMs());
        put(jsonObject, "lastErrorMessage", sourceState.getLastErrorMessage());
        return jsonObject;
    }

    public static SourceState fromSourceStateJson(JSONObject jsonObject) {
        SourceState sourceState = new SourceState();
        if (jsonObject == null) {
            return sourceState;
        }

        SourceIdentity sourceIdentity = enumValue(
                SourceIdentity.class,
                JsonUtils.optString(jsonObject, "sourceIdentity", null),
                null);
        if (sourceIdentity != null) {
            sourceState.setSourceIdentity(sourceIdentity);
        } else {
            sourceState.setSourceType(enumValue(
                    WarningSourceType.class,
                    JsonUtils.optString(jsonObject, "sourceType", null),
                    null));
        }
        sourceState.setStatus(enumValue(
                SourceState.Status.class,
                JsonUtils.optString(jsonObject, "status", null),
                SourceState.Status.ERROR_NO_CACHE));
        sourceState.setLastSuccessEpochMs(JsonUtils.optLong(jsonObject, "lastSuccessEpochMs", 0L));
        sourceState.setLastErrorMessage(JsonUtils.optString(jsonObject, "lastErrorMessage", null));
        return sourceState;
    }

    public static JSONObject toJson(RadarFrame radarFrame) {
        JSONObject jsonObject = new JSONObject();
        if (radarFrame == null) {
            return jsonObject;
        }

        put(jsonObject, "frameId", radarFrame.getFrameId());
        put(jsonObject, "productId", radarFrame.getProductId());
        put(jsonObject, "productLabel", radarFrame.getProductLabel());
        put(jsonObject, "frameEpochMs", radarFrame.getFrameEpochMs());
        put(jsonObject, "fetchedAtEpochMs", radarFrame.getFetchedAtEpochMs());
        put(jsonObject, "imageFormat", radarFrame.getImageFormat());
        put(jsonObject, "imagePath", radarFrame.getImagePath());
        put(jsonObject, "requestUrl", radarFrame.getRequestUrl());
        put(jsonObject, "crs", radarFrame.getCrs());
        put(jsonObject, "georeferenceId", radarFrame.getGeoreferenceId());
        put(jsonObject, "minLatitude", radarFrame.getMinLatitude());
        put(jsonObject, "minLongitude", radarFrame.getMinLongitude());
        put(jsonObject, "maxLatitude", radarFrame.getMaxLatitude());
        put(jsonObject, "maxLongitude", radarFrame.getMaxLongitude());
        put(jsonObject, "width", radarFrame.getWidth());
        put(jsonObject, "height", radarFrame.getHeight());
        put(jsonObject, "valid", radarFrame.isValid());
        return jsonObject;
    }

    public static RadarFrame fromRadarFrameJson(JSONObject jsonObject) {
        RadarFrame radarFrame = new RadarFrame();
        if (jsonObject == null) {
            return radarFrame;
        }

        radarFrame.setFrameId(JsonUtils.optString(jsonObject, "frameId", null));
        radarFrame.setProductId(JsonUtils.optString(jsonObject, "productId", null));
        radarFrame.setProductLabel(JsonUtils.optString(jsonObject, "productLabel", null));
        radarFrame.setFrameEpochMs(JsonUtils.optLong(jsonObject, "frameEpochMs", 0L));
        radarFrame.setFetchedAtEpochMs(JsonUtils.optLong(jsonObject, "fetchedAtEpochMs", 0L));
        radarFrame.setImageFormat(JsonUtils.optString(jsonObject, "imageFormat", null));
        radarFrame.setImagePath(JsonUtils.optString(jsonObject, "imagePath", null));
        radarFrame.setRequestUrl(JsonUtils.optString(jsonObject, "requestUrl", null));
        radarFrame.setCrs(JsonUtils.optString(jsonObject, "crs", null));
        radarFrame.setGeoreferenceId(JsonUtils.optString(jsonObject, "georeferenceId", null));
        radarFrame.setMinLatitude(JsonUtils.optDouble(jsonObject, "minLatitude", 0D));
        radarFrame.setMinLongitude(JsonUtils.optDouble(jsonObject, "minLongitude", 0D));
        radarFrame.setMaxLatitude(JsonUtils.optDouble(jsonObject, "maxLatitude", 0D));
        radarFrame.setMaxLongitude(JsonUtils.optDouble(jsonObject, "maxLongitude", 0D));
        radarFrame.setWidth((int) JsonUtils.optLong(jsonObject, "width", 0L));
        radarFrame.setHeight((int) JsonUtils.optLong(jsonObject, "height", 0L));
        radarFrame.setValid(JsonUtils.optBoolean(jsonObject, "valid", false));
        return radarFrame;
    }

    public static JSONArray toJsonArray(List<SourceState> states) {
        JSONArray jsonArray = new JSONArray();
        if (states == null) {
            return jsonArray;
        }

        for (SourceState state : states) {
            add(jsonArray, toJson(state));
        }
        return jsonArray;
    }

    public static JSONObject toSourceStateDocument(List<SourceState> states) {
        JSONObject jsonObject = new JSONObject();
        put(jsonObject, "states", toJsonArray(states));
        return jsonObject;
    }

    public static List<SourceState> fromSourceStateDocument(JSONObject jsonObject) {
        if (jsonObject == null) {
            return new ArrayList<>();
        }
        return fromSourceStateArray(jsonObject.optJSONArray("states"));
    }

    public static List<SourceState> fromSourceStateArray(JSONArray jsonArray) {
        List<SourceState> states = new ArrayList<>();
        if (jsonArray == null) {
            return states;
        }

        for (int index = 0; index < jsonArray.length(); index++) {
            JSONObject object = jsonArray.optJSONObject(index);
            if (object != null) {
                states.add(fromSourceStateJson(object));
            }
        }
        return states;
    }

    private static JSONObject toJson(WarningRecord record) {
        JSONObject jsonObject = new JSONObject();
        if (record == null) {
            return jsonObject;
        }

        put(jsonObject, "stableId", record.getStableId());
        put(jsonObject, "sourceType", valueOrNull(record.getSourceType()));
        put(jsonObject, "sourceId", record.getSourceId());
        put(jsonObject, "version", record.getVersion());
        put(jsonObject, "status", record.getStatus());
        put(jsonObject, "msgType", record.getMsgType());
        put(jsonObject, "category", record.getCategory());
        put(jsonObject, "eventCode", record.getEventCode());
        put(jsonObject, "eventLabel", record.getEventLabel());
        put(jsonObject, "title", record.getTitle());
        put(jsonObject, "sourceLabel", record.getSourceLabel());
        put(jsonObject, "descriptionText", record.getDescriptionText());
        put(jsonObject, "instructionText", record.getInstructionText());
        put(jsonObject, "severity", valueOrNull(record.getSeverity()));
        put(jsonObject, "urgency", valueOrNull(record.getUrgency()));
        put(jsonObject, "certainty", record.getCertainty());
        put(jsonObject, "renderMode", valueOrNull(record.getRenderMode()));
        put(jsonObject, "sentAtEpochMs", record.getSentAtEpochMs());
        put(jsonObject, "effectiveAtEpochMs", record.getEffectiveAtEpochMs());
        put(jsonObject, "onsetAtEpochMs", record.getOnsetAtEpochMs());
        put(jsonObject, "expiresAtEpochMs", record.getExpiresAtEpochMs());
        put(jsonObject, "lastFetchedAtEpochMs", record.getLastFetchedAtEpochMs());
        put(jsonObject, "geometry", toJson(record.getGeometry()));

        JSONArray areaRefsArray = new JSONArray();
        for (WarningAreaRef areaRef : record.getAreaRefs()) {
            JSONObject areaRefObject = new JSONObject();
            put(areaRefObject, "areaId", areaRef.getAreaId());
            put(areaRefObject, "areaName", areaRef.getAreaName());
            put(areaRefObject, "warnCellId", areaRef.getWarnCellId());
            add(areaRefsArray, areaRefObject);
        }
        put(jsonObject, "areaRefs", areaRefsArray);

        JSONObject metadataObject = new JSONObject();
        for (Map.Entry<String, String> entry : record.getMetadata().entrySet()) {
            put(metadataObject, entry.getKey(), entry.getValue());
        }
        put(jsonObject, "metadata", metadataObject);
        return jsonObject;
    }

    private static WarningRecord fromWarningRecord(JSONObject jsonObject) {
        WarningRecord record = new WarningRecord();
        record.setStableId(JsonUtils.optString(jsonObject, "stableId", null));
        record.setSourceType(enumValue(
                WarningSourceType.class,
                JsonUtils.optString(jsonObject, "sourceType", null),
                null));
        record.setSourceId(JsonUtils.optString(jsonObject, "sourceId", null));
        record.setVersion(JsonUtils.optString(jsonObject, "version", null));
        record.setStatus(JsonUtils.optString(jsonObject, "status", null));
        record.setMsgType(JsonUtils.optString(jsonObject, "msgType", null));
        record.setCategory(JsonUtils.optString(jsonObject, "category", null));
        record.setEventCode(JsonUtils.optString(jsonObject, "eventCode", null));
        record.setEventLabel(JsonUtils.optString(jsonObject, "eventLabel", null));
        record.setTitle(JsonUtils.optString(jsonObject, "title", null));
        record.setSourceLabel(JsonUtils.optString(jsonObject, "sourceLabel", null));
        record.setDescriptionText(JsonUtils.optString(jsonObject, "descriptionText", null));
        record.setInstructionText(JsonUtils.optString(jsonObject, "instructionText", null));
        record.setSeverity(enumValue(
                WarningSeverity.class,
                JsonUtils.optString(jsonObject, "severity", null),
                WarningSeverity.UNKNOWN));
        record.setUrgency(enumValue(
                WarningUrgency.class,
                JsonUtils.optString(jsonObject, "urgency", null),
                WarningUrgency.UNKNOWN));
        record.setCertainty(JsonUtils.optString(jsonObject, "certainty", null));
        record.setRenderMode(enumValue(
                RenderMode.class,
                JsonUtils.optString(jsonObject, "renderMode", null),
                null));
        record.setSentAtEpochMs(JsonUtils.optLong(jsonObject, "sentAtEpochMs", 0L));
        record.setEffectiveAtEpochMs(JsonUtils.optLong(jsonObject, "effectiveAtEpochMs", 0L));
        record.setOnsetAtEpochMs(JsonUtils.optLong(jsonObject, "onsetAtEpochMs", 0L));
        record.setExpiresAtEpochMs(JsonUtils.optLong(jsonObject, "expiresAtEpochMs", 0L));
        record.setLastFetchedAtEpochMs(JsonUtils.optLong(jsonObject, "lastFetchedAtEpochMs", 0L));
        record.setGeometry(fromWarningGeometry(jsonObject.optJSONObject("geometry")));
        record.setAreaRefs(fromAreaRefs(jsonObject.optJSONArray("areaRefs")));
        record.setMetadata(fromMetadata(jsonObject.optJSONObject("metadata")));
        return record;
    }

    private static JSONObject toJson(WarningGeometry geometry) {
        JSONObject jsonObject = new JSONObject();
        if (geometry == null) {
            return jsonObject;
        }

        put(jsonObject, "kind", valueOrNull(geometry.getKind()));
        put(jsonObject, "geoJsonGeometry", geometry.getGeoJsonGeometry());
        put(jsonObject, "centroidLat", geometry.getCentroidLat());
        put(jsonObject, "centroidLon", geometry.getCentroidLon());

        JSONArray bboxArray = new JSONArray();
        for (double value : geometry.getBbox()) {
            add(bboxArray, value);
        }
        put(jsonObject, "bbox", bboxArray);
        put(jsonObject, "approximate", geometry.isApproximate());
        put(jsonObject, "geometrySource", geometry.getGeometrySource());
        put(jsonObject, "geometryConfidence", valueOrNull(geometry.getGeometryConfidence()));
        return jsonObject;
    }

    private static WarningGeometry fromWarningGeometry(JSONObject jsonObject) {
        WarningGeometry geometry = new WarningGeometry();
        if (jsonObject == null) {
            return geometry;
        }

        geometry.setKind(enumValue(
                GeometryKind.class,
                JsonUtils.optString(jsonObject, "kind", null),
                GeometryKind.NONE));
        geometry.setGeoJsonGeometry(JsonUtils.optString(jsonObject, "geoJsonGeometry", null));
        geometry.setCentroidLat(jsonObject.optDouble("centroidLat", 0D));
        geometry.setCentroidLon(jsonObject.optDouble("centroidLon", 0D));
        geometry.setBbox(fromBbox(jsonObject.optJSONArray("bbox")));
        geometry.setApproximate(JsonUtils.optBoolean(jsonObject, "approximate", false));
        geometry.setGeometrySource(JsonUtils.optString(jsonObject, "geometrySource", null));
        geometry.setGeometryConfidence(enumValue(
                GeometryConfidence.class,
                JsonUtils.optString(jsonObject, "geometryConfidence", null),
                GeometryConfidence.NONE));
        return geometry;
    }

    private static List<WarningAreaRef> fromAreaRefs(JSONArray jsonArray) {
        List<WarningAreaRef> areaRefs = new ArrayList<>();
        if (jsonArray == null) {
            return areaRefs;
        }

        for (int index = 0; index < jsonArray.length(); index++) {
            JSONObject object = jsonArray.optJSONObject(index);
            if (object == null) {
                continue;
            }

            WarningAreaRef areaRef = new WarningAreaRef();
            areaRef.setAreaId(JsonUtils.optString(object, "areaId", null));
            areaRef.setAreaName(JsonUtils.optString(object, "areaName", null));
            areaRef.setWarnCellId(JsonUtils.optString(object, "warnCellId", null));
            areaRefs.add(areaRef);
        }
        return areaRefs;
    }

    private static Map<String, String> fromMetadata(JSONObject jsonObject) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (jsonObject == null) {
            return metadata;
        }

        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            metadata.put(key, JsonUtils.optString(jsonObject, key, null));
        }
        return metadata;
    }

    private static double[] fromBbox(JSONArray bboxArray) {
        double[] bbox = new double[4];
        if (bboxArray == null) {
            return bbox;
        }

        for (int index = 0; index < bbox.length && index < bboxArray.length(); index++) {
            bbox[index] = bboxArray.optDouble(index, 0D);
        }
        return bbox;
    }

    private static void put(JSONObject jsonObject, String key, Object value) {
        try {
            jsonObject.put(key, value);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to write JSON key: " + key, exception);
        }
    }

    private static void add(JSONArray jsonArray, Object value) {
        jsonArray.put(value);
    }

    private static String valueOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumClass, String value, T defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }
}
