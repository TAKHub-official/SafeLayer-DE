package com.takhub.safelayerde.source.bbk;

import android.util.Log;

import com.takhub.safelayerde.plugin.OperationalAreaArsResolver;
import com.takhub.safelayerde.plugin.SafeLayerConstants;
import com.takhub.safelayerde.source.common.HttpClient;
import com.takhub.safelayerde.source.common.HttpClient.RemoteEndpointPolicy;
import com.takhub.safelayerde.util.JsonUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class BbkApiClient {

    private static final String TAG = "SafeLayerBbkApi";
    private static final String REGIONAL_DISCOVERY_ENDPOINT_TEMPLATE = "dashboard/%s.json";
    private static final Pattern WARNING_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]+$");

    private final HttpClient httpClient;
    private final RemoteEndpointPolicy endpointPolicy;

    public BbkApiClient(HttpClient httpClient) {
        this(httpClient, SafeLayerConstants.BBK_API_ENDPOINT);
    }

    BbkApiClient(HttpClient httpClient, RemoteEndpointPolicy endpointPolicy) {
        this.httpClient = httpClient;
        this.endpointPolicy = endpointPolicy;
    }

    public FetchResult<JSONArray> fetchDiscovery(String ars) {
        try {
            String endpoint = String.format(
                    Locale.ROOT,
                    REGIONAL_DISCOVERY_ENDPOINT_TEMPLATE,
                    validateArs(ars));
            return FetchResult.success(new JSONArray(httpClient.fetchString(resolveEndpoint(endpoint))));
        } catch (IOException | JSONException exception) {
            String message = "Failed to fetch BBK discovery for ARS " + ars;
            logError(message, exception);
            return FetchResult.failure(message, exception);
        }
    }

    public DiscoveryBatchResult fetchNationalDiscovery() {
        try {
            JSONArray response = new JSONArray(httpClient.fetchString(
                    endpointPolicy.resolve(SafeLayerConstants.BBK_MOWAS_MAPDATA_PATH)));
            LinkedHashMap<String, DiscoveryEntry> merged = new LinkedHashMap<>();
            for (int index = 0; index < response.length(); index++) {
                JSONObject entry = response.optJSONObject(index);
                if (entry == null) {
                    continue;
                }
                String id = JsonUtils.optString(entry, "id", null);
                if (id == null || id.trim().isEmpty()) {
                    continue;
                }
                merged.put(id, new DiscoveryEntry(id, entry));
            }
            return new DiscoveryBatchResult(
                    merged,
                    Collections.<String>emptySet(),
                    Collections.singleton("mowas"));
        } catch (IOException | JSONException exception) {
            logError("Failed to fetch national BBK discovery.", exception);
            return new DiscoveryBatchResult(
                    new LinkedHashMap<String, DiscoveryEntry>(),
                    Collections.singleton("mowas"),
                    Collections.<String>emptySet());
        }
    }

    public FetchResult<JSONObject> fetchDetail(String warningId) {
        try {
            return FetchResult.success(new JSONObject(httpClient.fetchString(
                    resolveEndpoint("warnings/" + validateWarningId(warningId) + ".json"))));
        } catch (IOException | JSONException exception) {
            String message = "Failed to fetch BBK detail for warning " + warningId;
            logError(message, exception);
            return FetchResult.failure(message, exception);
        }
    }

    public FetchResult<String> fetchGeoJson(String warningId) {
        try {
            return FetchResult.success(httpClient.fetchString(
                    resolveEndpoint("warnings/" + validateWarningId(warningId) + ".geojson")));
        } catch (IOException exception) {
            String message = "Failed to fetch BBK GeoJSON for warning " + warningId;
            logError(message, exception);
            return FetchResult.failure(message, exception);
        }
    }

    public DiscoveryBatchResult fetchDiscoveryForArsSet(Set<String> arsSet) {
        Map<String, DiscoveryEntry> merged = new LinkedHashMap<>();
        Set<String> failedArs = new LinkedHashSet<>();
        Set<String> successfulArs = new LinkedHashSet<>();
        if (arsSet == null) {
            return new DiscoveryBatchResult(merged, failedArs, successfulArs);
        }

        for (String ars : arsSet) {
            if (ars == null || ars.trim().isEmpty()) {
                continue;
            }

            FetchResult<JSONArray> discoveryResult = fetchDiscovery(ars);
            if (!discoveryResult.isSuccess()) {
                failedArs.add(ars);
                continue;
            }

            successfulArs.add(ars);
            JSONArray discovery = discoveryResult.getValue();
            for (int index = 0; index < discovery.length(); index++) {
                JSONObject entry = discovery.optJSONObject(index);
                if (entry == null) {
                    continue;
                }

                String id = JsonUtils.optString(entry, "id", null);
                if (id != null && !id.trim().isEmpty()) {
                    DiscoveryEntry mergedEntry = merged.get(id);
                    if (mergedEntry == null) {
                        mergedEntry = new DiscoveryEntry(id, entry);
                        merged.put(id, mergedEntry);
                    }
                    mergedEntry.addArs(ars);
                }
            }
        }

        return new DiscoveryBatchResult(merged, failedArs, successfulArs);
    }

    HttpClient.RemoteRequest resolveEndpoint(String relativePath) throws IOException {
        return endpointPolicy.resolve(relativePath);
    }

    private String validateArs(String rawValue) throws IOException {
        String normalizedValue = rawValue == null ? null : rawValue.trim();
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new IOException("ARS must not be empty.");
        }
        if (!OperationalAreaArsResolver.isValidArs(normalizedValue)) {
            throw new IOException("ARS must use the 12-digit format.");
        }
        return normalizedValue;
    }

    private String validateWarningId(String rawValue) throws IOException {
        String normalizedValue = rawValue == null ? null : rawValue.trim();
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            throw new IOException("warningId must not be empty.");
        }
        if (".".equals(normalizedValue) || "..".equals(normalizedValue)) {
            throw new IOException("warningId dot segments are not allowed.");
        }
        if (!WARNING_ID_PATTERN.matcher(normalizedValue).matches()) {
            throw new IOException("warningId contains invalid characters.");
        }
        return normalizedValue;
    }

    private void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Local unit tests use the Android stub logger.
        }
    }

    public static final class FetchResult<T> {

        private final T value;
        private final boolean success;
        private final String errorMessage;

        private FetchResult(T value, boolean success, String errorMessage) {
            this.value = value;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static <T> FetchResult<T> success(T value) {
            return new FetchResult<>(value, true, null);
        }

        public static <T> FetchResult<T> failure(String errorMessage, Throwable throwable) {
            return new FetchResult<>(null, false, errorMessage);
        }

        public T getValue() {
            return value;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class DiscoveryEntry {

        private final String warningId;
        private JSONObject entry;
        private final Set<String> arsSet = new LinkedHashSet<>();

        DiscoveryEntry(String warningId, JSONObject entry) {
            this.warningId = warningId;
            this.entry = entry;
        }

        public String getWarningId() {
            return warningId;
        }

        public JSONObject getEntry() {
            return entry;
        }

        public Set<String> getArsSet() {
            return Collections.unmodifiableSet(arsSet);
        }

        void addArs(String ars) {
            if (ars != null && !ars.trim().isEmpty()) {
                arsSet.add(ars);
            }
        }
    }

    public static final class DiscoveryBatchResult {

        private final Map<String, DiscoveryEntry> entries;
        private final Set<String> failedArs;
        private final Set<String> successfulArs;

        DiscoveryBatchResult(
                Map<String, DiscoveryEntry> entries,
                Set<String> failedArs,
                Set<String> successfulArs) {
            this.entries = entries == null ? new LinkedHashMap<String, DiscoveryEntry>() : entries;
            this.failedArs = failedArs == null ? new LinkedHashSet<String>() : failedArs;
            this.successfulArs = successfulArs == null ? new LinkedHashSet<String>() : successfulArs;
        }

        public Map<String, DiscoveryEntry> getEntries() {
            return Collections.unmodifiableMap(entries);
        }

        public Map<String, JSONObject> asJsonMap() {
            Map<String, JSONObject> jsonMap = new LinkedHashMap<>();
            for (Map.Entry<String, DiscoveryEntry> entry : entries.entrySet()) {
                jsonMap.put(entry.getKey(), entry.getValue().getEntry());
            }
            return jsonMap;
        }

        public Set<String> getFailedArs() {
            return Collections.unmodifiableSet(failedArs);
        }

        public Set<String> getSuccessfulArs() {
            return Collections.unmodifiableSet(successfulArs);
        }

        public boolean hasFailures() {
            return !failedArs.isEmpty();
        }
    }
}
