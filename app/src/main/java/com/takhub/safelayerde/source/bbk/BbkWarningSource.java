package com.takhub.safelayerde.source.bbk;

import android.util.Log;

import com.takhub.safelayerde.cache.CacheStore;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.WarningGeometry;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.WarningDedupPolicy;
import com.takhub.safelayerde.domain.service.DataAgeService;
import com.takhub.safelayerde.domain.service.WarningDiffService;
import com.takhub.safelayerde.source.common.SourceAdapter;
import com.takhub.safelayerde.source.common.SourceClock;
import com.takhub.safelayerde.source.common.SourceRefreshFinalizer;
import com.takhub.safelayerde.source.common.SourceRefreshResult;
import com.takhub.safelayerde.source.common.WarningSnapshotExpiryFilter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BbkWarningSource implements SourceAdapter {

    private static final String TAG = "SafeLayerBbkSource";

    private final BbkApiClient apiClient;
    private final BbkNormalizer normalizer;
    private final BbkGeoJsonParser geoJsonParser;
    private final WarningDiffService diffService;
    private final CacheStore cacheStore;
    private final SourceClock sourceClock;
    private final ArsSetProvider arsSetProvider;
    private final SourceRefreshFinalizer refreshFinalizer;
    private final WarningDedupPolicy dedupPolicy = new WarningDedupPolicy();

    private WarningSnapshot lastSnapshot;

    public BbkWarningSource(
            BbkApiClient apiClient,
            BbkNormalizer normalizer,
            BbkGeoJsonParser geoJsonParser,
            WarningDiffService diffService,
            CacheStore cacheStore,
            SourceClock sourceClock,
            Set<String> arsSet) {
        this(
                apiClient,
                normalizer,
                geoJsonParser,
                diffService,
                cacheStore,
                sourceClock,
                new FixedArsSetProvider(arsSet),
                new DataAgeService());
    }

    public BbkWarningSource(
            BbkApiClient apiClient,
            BbkNormalizer normalizer,
            BbkGeoJsonParser geoJsonParser,
            WarningDiffService diffService,
            CacheStore cacheStore,
            SourceClock sourceClock,
            ArsSetProvider arsSetProvider) {
        this(
                apiClient,
                normalizer,
                geoJsonParser,
                diffService,
                cacheStore,
                sourceClock,
                arsSetProvider,
                new DataAgeService());
    }

    BbkWarningSource(
            BbkApiClient apiClient,
            BbkNormalizer normalizer,
            BbkGeoJsonParser geoJsonParser,
            WarningDiffService diffService,
            CacheStore cacheStore,
            SourceClock sourceClock,
            ArsSetProvider arsSetProvider,
            DataAgeService dataAgeService) {
        this.apiClient = apiClient;
        this.normalizer = normalizer;
        this.geoJsonParser = geoJsonParser;
        this.diffService = diffService;
        this.cacheStore = cacheStore;
        this.sourceClock = sourceClock;
        this.arsSetProvider = arsSetProvider == null
                ? new FixedArsSetProvider(Collections.<String>emptySet())
                : arsSetProvider;
        this.refreshFinalizer = new SourceRefreshFinalizer(cacheStore, dataAgeService);
    }

    public WarningSnapshot loadFromCache() {
        WarningSnapshot cached = cacheStore == null ? null : cacheStore.readWarningSnapshot(WarningSourceType.BBK);
        cached = WarningSnapshotExpiryFilter.filterExpiredRecords(cached, sourceClock.nowMs());
        if (cached != null) {
            lastSnapshot = cached;
        }
        return cached;
    }

    @Override
    public SourceRefreshResult refresh() {
        try {
            Set<String> resolvedArsSet = resolveArsSet();
            logInfo("refresh() ARS set=" + resolvedArsSet);
            SafeLayerDebugLog.i(TAG, "refresh-start arsSet=" + resolvedArsSet);

            long now = sourceClock.nowMs();
            Map<String, WarningRecord> previousBySourceId = previousBySourceId();
            BbkApiClient.DiscoveryBatchResult discoveryResult = fetchDiscovery(resolvedArsSet);
            Set<String> changedIds = determineChangedIds(discoveryResult);
            MaterializationResult materialization = materializeRecords(
                    discoveryResult,
                    previousBySourceId,
                    changedIds,
                    now);
            Set<String> discoveryConfirmedRecordIds = recordSourceIds(materialization.records);
            preserveDiscoveryFailureRecords(
                    materialization.records,
                    previousBySourceId,
                    discoveryResult.getEntries().keySet(),
                    discoveryResult.getFailedArs());

            WarningSnapshot snapshot = buildSnapshot(now, materialization.records);
            SafeLayerDebugLog.i(TAG, "refresh-summary records=" + snapshot.getRecords().size()
                    + ", partialFailure=" + materialization.hadPartialFailure
                    + ", failures=" + materialization.failureMessages.size());

            SourceRefreshResult result = refreshFinalizer.finalizeWarningRefresh(
                    SourceIdentity.BBK,
                    snapshot,
                    lastSnapshot,
                    canUseFreshSnapshotOnFailure(discoveryResult, snapshot, discoveryConfirmedRecordIds),
                    now,
                    materialization.failureMessages);
            lastSnapshot = result.getSnapshot();
            SafeLayerDebugLog.i(TAG, "refresh-result records=" + snapshotSize(result.getSnapshot())
                    + ", state=" + result.getSourceState().getStatus()
                    + ", success=" + result.isSuccess());
            return result;
        } catch (Exception exception) {
            logError("Failed to refresh BBK warnings.", exception);
            SafeLayerDebugLog.e(TAG, "refresh-failed", exception);
            SourceRefreshResult result = refreshFinalizer.finalizeWarningRefresh(
                    SourceIdentity.BBK,
                    null,
                    lastSnapshot,
                    false,
                    sourceClock.nowMs(),
                    Collections.singletonList(exception.getMessage()));
            lastSnapshot = result.getSnapshot();
            return result;
        }
    }

    private BbkApiClient.DiscoveryBatchResult fetchDiscovery(Set<String> resolvedArsSet) {
        if (resolvedArsSet.isEmpty()) {
            logWarning("ARS set is empty. Falling back to national BBK discovery.");
            return apiClient.fetchNationalDiscovery();
        }
        return apiClient.fetchDiscoveryForArsSet(resolvedArsSet);
    }

    private Set<String> determineChangedIds(BbkApiClient.DiscoveryBatchResult discoveryResult) {
        Map<String, String> previousVersions = diffService.buildVersionMap(
                lastSnapshot == null || lastSnapshot.getRecords() == null
                        ? new ArrayList<WarningRecord>()
                        : lastSnapshot.getRecords());
        WarningDiffService.DiffResult diffResult = diffService.diff(previousVersions, discoveryResult.asJsonMap());

        Set<String> changedIds = new LinkedHashSet<>();
        changedIds.addAll(diffResult.getNewIds());
        changedIds.addAll(diffResult.getChangedIds());
        return changedIds;
    }

    private MaterializationResult materializeRecords(
            BbkApiClient.DiscoveryBatchResult discoveryResult,
            Map<String, WarningRecord> previousBySourceId,
            Set<String> changedIds,
            long now) {
        MaterializationResult result = new MaterializationResult();
        result.hadPartialFailure = discoveryResult.hasFailures();
        result.failureMessages.addAll(messagesForArsFailures(discoveryResult.getFailedArs()));

        for (Map.Entry<String, BbkApiClient.DiscoveryEntry> entry : discoveryResult.getEntries().entrySet()) {
            String warningId = entry.getKey();
            WarningRecord previousRecord = previousBySourceId.get(warningId);
            if (!changedIds.contains(warningId)) {
                if (previousRecord != null) {
                    result.records.add(previousRecord);
                }
                continue;
            }

            WarningRecord record = materializeChangedRecord(
                    warningId,
                    entry.getValue(),
                    previousRecord,
                    now,
                    result);
            if (record != null) {
                result.records.add(record);
            }
        }
        return result;
    }

    private WarningRecord materializeChangedRecord(
            String warningId,
            BbkApiClient.DiscoveryEntry discoveryEntry,
            WarningRecord previousRecord,
            long now,
            MaterializationResult result) {
        BbkApiClient.FetchResult<JSONObject> detailResult = apiClient.fetchDetail(warningId);
        if (!detailResult.isSuccess()) {
            result.recordFailure(detailResult.getErrorMessage());
            return previousRecord;
        }

        BbkApiClient.FetchResult<String> geoJsonResult = apiClient.fetchGeoJson(warningId);
        WarningGeometry parsedGeometry = null;
        if (geoJsonResult.isSuccess()) {
            parsedGeometry = geoJsonParser.parse(geoJsonResult.getValue());
        } else {
            result.recordFailure(geoJsonResult.getErrorMessage());
            if (previousRecord != null) {
                return previousRecord;
            }
        }

        return normalizer.normalize(
                warningId,
                discoveryEntry.getEntry(),
                discoveryEntry.getArsSet(),
                detailResult.getValue(),
                parsedGeometry,
                previousRecord,
                now);
    }

    private WarningSnapshot buildSnapshot(long now, List<WarningRecord> mergedRecords) {
        WarningSnapshot snapshot = new WarningSnapshot();
        snapshot.setSourceType(WarningSourceType.BBK);
        snapshot.setFetchedAtEpochMs(now);
        snapshot.setRecords(dedupPolicy.deduplicate(mergedRecords));
        return WarningSnapshotExpiryFilter.filterExpiredRecords(snapshot, now);
    }

    private Set<String> resolveArsSet() {
        Set<String> arsSet = arsSetProvider.resolve();
        if (arsSet == null || arsSet.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> sanitized = new LinkedHashSet<>();
        for (String ars : arsSet) {
            if (ars != null && !ars.trim().isEmpty()) {
                sanitized.add(ars);
            }
        }
        return sanitized;
    }

    private Map<String, WarningRecord> previousBySourceId() {
        Map<String, WarningRecord> previous = new LinkedHashMap<>();
        if (lastSnapshot == null || lastSnapshot.getRecords() == null) {
            return previous;
        }

        for (WarningRecord record : lastSnapshot.getRecords()) {
            if (record != null && record.getSourceId() != null) {
                previous.put(record.getSourceId(), record);
            }
        }
        return previous;
    }

    private void preserveDiscoveryFailureRecords(
            List<WarningRecord> mergedRecords,
            Map<String, WarningRecord> previousBySourceId,
            Set<String> currentDiscoveryIds,
            Set<String> failedArs) {
        if (failedArs == null || failedArs.isEmpty()) {
            return;
        }

        Set<String> alreadyMerged = new LinkedHashSet<>();
        for (WarningRecord record : mergedRecords) {
            if (record != null && record.getSourceId() != null) {
                alreadyMerged.add(record.getSourceId());
            }
        }

        for (Map.Entry<String, WarningRecord> previousEntry : previousBySourceId.entrySet()) {
            if (currentDiscoveryIds.contains(previousEntry.getKey()) || alreadyMerged.contains(previousEntry.getKey())) {
                continue;
            }

            WarningRecord previousRecord = previousEntry.getValue();
            if (belongsToAnyArs(previousRecord, failedArs)) {
                mergedRecords.add(previousRecord);
            }
        }
    }

    private boolean belongsToAnyArs(WarningRecord record, Set<String> arsSet) {
        if (record == null || record.getMetadata() == null || arsSet == null || arsSet.isEmpty()) {
            return false;
        }

        String discoveryArs = record.getMetadata().get("bbk.discoveryArs");
        if (discoveryArs == null || discoveryArs.trim().isEmpty()) {
            return false;
        }

        for (String ars : Arrays.asList(discoveryArs.split(","))) {
            if (ars != null && arsSet.contains(ars.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> messagesForArsFailures(Set<String> failedArs) {
        List<String> messages = new ArrayList<>();
        if (failedArs == null) {
            return messages;
        }

        for (String ars : failedArs) {
            if (ars != null && !ars.trim().isEmpty()) {
                messages.add("Failed to fetch BBK discovery for ARS " + ars);
            }
        }
        return messages;
    }

    private boolean canUseFreshSnapshotOnFailure(
            BbkApiClient.DiscoveryBatchResult discoveryResult,
            WarningSnapshot snapshot,
            Set<String> discoveryConfirmedRecordIds) {
        return hasUsableRecords(snapshot)
                && discoveryResult != null
                && discoveryResult.getSuccessfulArs() != null
                && !discoveryResult.getSuccessfulArs().isEmpty()
                && containsAnyRecord(snapshot, discoveryConfirmedRecordIds);
    }

    private Set<String> recordSourceIds(List<WarningRecord> records) {
        Set<String> sourceIds = new LinkedHashSet<>();
        if (records == null) {
            return sourceIds;
        }
        for (WarningRecord record : records) {
            if (record != null && record.getSourceId() != null) {
                sourceIds.add(record.getSourceId());
            }
        }
        return sourceIds;
    }

    private boolean containsAnyRecord(WarningSnapshot snapshot, Set<String> sourceIds) {
        if (!hasUsableRecords(snapshot) || sourceIds == null || sourceIds.isEmpty()) {
            return false;
        }
        for (WarningRecord record : snapshot.getRecords()) {
            if (record != null && sourceIds.contains(record.getSourceId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsableRecords(WarningSnapshot snapshot) {
        return snapshot != null
                && snapshot.getRecords() != null
                && !snapshot.getRecords().isEmpty();
    }

    private int snapshotSize(WarningSnapshot snapshot) {
        return snapshot == null || snapshot.getRecords() == null ? 0 : snapshot.getRecords().size();
    }

    private void logWarning(String message) {
        try {
            Log.w(TAG, message);
        } catch (RuntimeException ignored) {
            // Local unit tests use the Android stub logger.
        }
        SafeLayerDebugLog.w(TAG, message);
    }

    private void logInfo(String message) {
        SafeLayerDebugLog.i(TAG, message);
    }

    private void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Local unit tests use the Android stub logger.
        }
        SafeLayerDebugLog.e(TAG, message, throwable);
    }

    public interface ArsSetProvider {
        Set<String> resolve();
    }

    private static final class FixedArsSetProvider implements ArsSetProvider {

        private final Set<String> arsSet;

        private FixedArsSetProvider(Set<String> arsSet) {
            this.arsSet = arsSet == null ? Collections.<String>emptySet() : new LinkedHashSet<>(arsSet);
        }

        @Override
        public Set<String> resolve() {
            return new LinkedHashSet<>(arsSet);
        }
    }

    private static final class MaterializationResult {

        private final List<WarningRecord> records = new ArrayList<>();
        private final List<String> failureMessages = new ArrayList<>();
        private boolean hadPartialFailure;

        private void recordFailure(String message) {
            hadPartialFailure = true;
            if (message != null && !message.trim().isEmpty()) {
                failureMessages.add(message);
            }
        }
    }
}
