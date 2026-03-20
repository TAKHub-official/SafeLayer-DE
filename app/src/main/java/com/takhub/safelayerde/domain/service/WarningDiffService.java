package com.takhub.safelayerde.domain.service;

import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.util.JsonUtils;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarningDiffService {

    public DiffResult diff(Map<String, String> previousVersions, Map<String, JSONObject> currentDiscovery) {
        Map<String, String> safePrevious = previousVersions == null
                ? new LinkedHashMap<String, String>()
                : previousVersions;
        Map<String, JSONObject> safeCurrent = currentDiscovery == null
                ? new LinkedHashMap<String, JSONObject>()
                : currentDiscovery;

        Set<String> newIds = new LinkedHashSet<>();
        Set<String> changedIds = new LinkedHashSet<>();
        Set<String> removedIds = new LinkedHashSet<>();

        for (Map.Entry<String, JSONObject> entry : safeCurrent.entrySet()) {
            String warningId = entry.getKey();
            String stableId = toStableId(warningId);
            String currentVersion = versionFromDiscovery(entry.getValue());
            String previousVersion = safePrevious.get(stableId);
            if (previousVersion == null) {
                newIds.add(warningId);
            } else if (!previousVersion.equals(currentVersion)) {
                changedIds.add(warningId);
            }
        }

        for (String stableId : safePrevious.keySet()) {
            String warningId = toWarningId(stableId);
            if (!safeCurrent.containsKey(warningId)) {
                removedIds.add(warningId);
            }
        }

        return new DiffResult(newIds, changedIds, removedIds);
    }

    public Map<String, String> buildVersionMap(List<WarningRecord> records) {
        Map<String, String> versionMap = new LinkedHashMap<>();
        if (records == null) {
            return versionMap;
        }

        for (WarningRecord record : records) {
            if (record == null || record.getStableId() == null) {
                continue;
            }
            versionMap.put(record.getStableId(), record.getVersion() == null ? "0" : record.getVersion());
        }
        return versionMap;
    }

    private String versionFromDiscovery(JSONObject discoveryEntry) {
        JSONObject payload = discoveryEntry == null ? null : discoveryEntry.optJSONObject("payload");
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        String version = JsonUtils.optString(data, "sent", null);
        if (version == null) {
            version = JsonUtils.optString(discoveryEntry, "sent", null);
        }
        if (version == null) {
            version = JsonUtils.optString(discoveryEntry, "version", null);
        }
        if (version == null) {
            version = JsonUtils.optString(discoveryEntry, "startDate", null);
        }
        return version == null ? "0" : version;
    }

    private String toStableId(String warningId) {
        return "BBK:" + warningId;
    }

    private String toWarningId(String stableId) {
        if (stableId != null && stableId.startsWith("BBK:")) {
            return stableId.substring(4);
        }
        return stableId;
    }

    public static final class DiffResult {

        private final Set<String> newIds;
        private final Set<String> changedIds;
        private final Set<String> removedIds;

        public DiffResult(Set<String> newIds, Set<String> changedIds, Set<String> removedIds) {
            this.newIds = newIds;
            this.changedIds = changedIds;
            this.removedIds = removedIds;
        }

        public Set<String> getNewIds() {
            return newIds;
        }

        public Set<String> getChangedIds() {
            return changedIds;
        }

        public Set<String> getRemovedIds() {
            return removedIds;
        }
    }
}
