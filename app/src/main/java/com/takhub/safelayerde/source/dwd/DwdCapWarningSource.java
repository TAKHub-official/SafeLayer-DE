package com.takhub.safelayerde.source.dwd;

import android.util.Log;

import com.takhub.safelayerde.cache.CacheStore;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.WarningDedupPolicy;
import com.takhub.safelayerde.domain.service.DataAgeService;
import com.takhub.safelayerde.source.common.SourceAdapter;
import com.takhub.safelayerde.source.common.SourceClock;
import com.takhub.safelayerde.source.common.SourceRefreshFinalizer;
import com.takhub.safelayerde.source.common.SourceRefreshResult;
import com.takhub.safelayerde.source.common.WarningSnapshotExpiryFilter;
import com.takhub.safelayerde.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DwdCapWarningSource implements SourceAdapter {

    private static final String TAG = "SafeLayerDwdSource";

    private final DwdCapZipFetcher zipFetcher;
    private final DwdCapParser parser;
    private final DwdCapNormalizer normalizer;
    private final CacheStore cacheStore;
    private final SourceClock sourceClock;
    private final String feedUrl;
    private final SourceRefreshFinalizer refreshFinalizer;
    private final WarningDedupPolicy dedupPolicy = new WarningDedupPolicy();

    private WarningSnapshot lastSnapshot;

    public DwdCapWarningSource(
            DwdCapZipFetcher zipFetcher,
            DwdCapParser parser,
            DwdCapNormalizer normalizer,
            CacheStore cacheStore,
            SourceClock sourceClock,
            String feedUrl) {
        this(zipFetcher, parser, normalizer, cacheStore, sourceClock, feedUrl, new DataAgeService());
    }

    DwdCapWarningSource(
            DwdCapZipFetcher zipFetcher,
            DwdCapParser parser,
            DwdCapNormalizer normalizer,
            CacheStore cacheStore,
            SourceClock sourceClock,
            String feedUrl,
            DataAgeService dataAgeService) {
        this.zipFetcher = zipFetcher;
        this.parser = parser;
        this.normalizer = normalizer;
        this.cacheStore = cacheStore;
        this.sourceClock = sourceClock;
        this.feedUrl = feedUrl;
        this.refreshFinalizer = new SourceRefreshFinalizer(cacheStore, dataAgeService);
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public WarningSnapshot loadFromCache() {
        WarningSnapshot cached = cacheStore == null ? null : cacheStore.readWarningSnapshot(WarningSourceType.DWD);
        cached = WarningSnapshotExpiryFilter.filterExpiredRecords(cached, sourceClock.nowMs());
        if (cached != null && isGermanSnapshot(cached)) {
            lastSnapshot = cached;
            return cached;
        }
        if (cached != null) {
            SafeLayerDebugLog.w(TAG, "cache-ignored reason=non-german-content records=" + snapshotSize(cached));
        }
        return null;
    }

    @Override
    public SourceRefreshResult refresh() {
        try {
            long now = sourceClock.nowMs();
            SafeLayerDebugLog.i(TAG, "refresh-start feedUrl=" + feedUrl);
            ParsedFeed parsedFeed = parseFetchedArchive();
            PreparedRefresh preparedRefresh = prepareRefresh(now, parsedFeed);
            logRefreshSummary(preparedRefresh);
            SourceRefreshResult result = refreshFinalizer.finalizeWarningRefresh(
                    SourceIdentity.DWD,
                    preparedRefresh.snapshot,
                    lastSnapshot,
                    hasUsableRecords(preparedRefresh.snapshot),
                    preparedRefresh.now,
                    preparedRefresh.failureMessages);
            lastSnapshot = result.getSnapshot();
            SafeLayerDebugLog.i(TAG, "refresh-result records=" + snapshotSize(result.getSnapshot())
                    + ", state=" + result.getSourceState().getStatus()
                    + ", success=" + result.isSuccess()
                    + ", renderModes=" + summarizeRenderModes(result.getSnapshot()));
            return result;
        } catch (Exception exception) {
            logError("Failed to refresh DWD warnings.", exception);
            SafeLayerDebugLog.e(TAG, "refresh-failed feedUrl=" + feedUrl, exception);
            SourceRefreshResult result = refreshFinalizer.finalizeWarningRefresh(
                    SourceIdentity.DWD,
                    null,
                    lastSnapshot,
                    false,
                    sourceClock.nowMs(),
                    Collections.singletonList(exception.getMessage()));
            lastSnapshot = result.getSnapshot();
            return result;
        }
    }

    private ParsedFeed parseFetchedArchive() throws IOException {
        DwdCapZipFetcher.FetchResult fetchResult = zipFetcher.fetch(feedUrl);
        List<String> failureMessages = new ArrayList<>(fetchResult.getFailures());
        boolean hadPartialFailure = fetchResult.hasFailures();
        List<DwdCapParser.ParsedAlert> parsedAlerts = new ArrayList<>();

        for (DwdCapZipFetcher.XmlEntry xmlEntry : fetchResult.getXmlEntries()) {
            if (xmlEntry == null || xmlEntry.getXml() == null) {
                continue;
            }
            try {
                parsedAlerts.add(parser.parse(xmlEntry.getXml()));
            } catch (Exception exception) {
                hadPartialFailure = true;
                failureMessages.add("Failed to parse DWD CAP entry "
                        + DwdCapZipFetcher.summarizeEntryName(xmlEntry.getEntryName())
                        + ": "
                        + exception.getMessage());
            }
        }

        return new ParsedFeed(fetchResult, parsedAlerts, failureMessages, hadPartialFailure);
    }

    private PreparedRefresh prepareRefresh(long now, ParsedFeed parsedFeed) {
        List<DwdCapParser.ParsedAlert> reconciledAlerts = reconcileAlerts(parsedFeed.parsedAlerts);
        WarningSnapshot snapshot = buildSnapshot(reconciledAlerts, now);
        snapshot = enforcePreferredLanguage(parsedFeed.fetchResult, snapshot, parsedFeed.failureMessages);
        boolean hadPartialFailure = parsedFeed.hadPartialFailure || !parsedFeed.failureMessages.isEmpty();
        return new PreparedRefresh(
                now,
                parsedFeed.fetchResult,
                parsedFeed.parsedAlerts,
                reconciledAlerts,
                snapshot,
                parsedFeed.failureMessages,
                hadPartialFailure);
    }

    private void logRefreshSummary(PreparedRefresh preparedRefresh) {
        SafeLayerDebugLog.i(TAG, "refresh-summary parsedAlerts=" + preparedRefresh.parsedAlerts.size()
                + ", reconciledAlerts=" + preparedRefresh.reconciledAlerts.size()
                + ", records=" + snapshotSize(preparedRefresh.snapshot)
                + ", failures=" + preparedRefresh.failureMessages.size()
                + ", archiveLanguage=" + preparedRefresh.fetchResult.getArchiveLanguage()
                + ", languages=" + summarizeLanguages(preparedRefresh.reconciledAlerts)
                + ", renderModes=" + summarizeRenderModes(preparedRefresh.snapshot));
    }

    private WarningSnapshot buildSnapshot(List<DwdCapParser.ParsedAlert> alerts, long fetchedAtMs) {
        List<WarningRecord> records = new ArrayList<>();
        for (DwdCapParser.ParsedAlert alert : alerts) {
            WarningRecord record = normalizer.normalize(alert, fetchedAtMs);
            if (record != null) {
                records.add(record);
            }
        }

        WarningSnapshot snapshot = new WarningSnapshot();
        snapshot.setSourceType(WarningSourceType.DWD);
        snapshot.setFetchedAtEpochMs(fetchedAtMs);
        snapshot.setRecords(dedupPolicy.deduplicate(records));
        return WarningSnapshotExpiryFilter.filterExpiredRecords(snapshot, fetchedAtMs);
    }

    private WarningSnapshot enforcePreferredLanguage(
            DwdCapZipFetcher.FetchResult fetchResult,
            WarningSnapshot snapshot,
            List<String> failureMessages) {
        if (snapshot == null || snapshot.getRecords() == null || snapshot.getRecords().isEmpty()) {
            return snapshot;
        }

        String archiveLanguage = StringUtils.trimToNull(fetchResult == null ? null : fetchResult.getArchiveLanguage());

        List<WarningRecord> filteredRecords = new ArrayList<>();
        int removed = 0;
        for (WarningRecord record : snapshot.getRecords()) {
            if (isGermanRecord(record)) {
                filteredRecords.add(record);
            } else {
                removed++;
            }
        }

        if (removed <= 0) {
            return snapshot;
        }

        WarningSnapshot filteredSnapshot = new WarningSnapshot();
        filteredSnapshot.setSourceType(snapshot.getSourceType());
        filteredSnapshot.setFetchedAtEpochMs(snapshot.getFetchedAtEpochMs());
        filteredSnapshot.setRecords(filteredRecords);
        failureMessages.add("Filtered " + removed + " non-German DWD records from archive "
                + (archiveLanguage == null ? "unknown" : archiveLanguage) + ".");
        SafeLayerDebugLog.w(TAG, "language-filter archiveLanguage="
                + (archiveLanguage == null ? "unknown" : archiveLanguage)
                + " removed=" + removed
                + ", remaining=" + filteredRecords.size());
        return filteredSnapshot;
    }

    private boolean hasUsableRecords(WarningSnapshot snapshot) {
        return snapshot != null
                && snapshot.getRecords() != null
                && !snapshot.getRecords().isEmpty();
    }

    private List<DwdCapParser.ParsedAlert> reconcileAlerts(List<DwdCapParser.ParsedAlert> alerts) {
        List<DwdCapParser.ParsedAlert> orderedAlerts = alerts == null
                ? new ArrayList<DwdCapParser.ParsedAlert>()
                : new ArrayList<>(alerts);
        Collections.sort(orderedAlerts, new Comparator<DwdCapParser.ParsedAlert>() {
            @Override
            public int compare(DwdCapParser.ParsedAlert left, DwdCapParser.ParsedAlert right) {
                long leftSent = left == null ? 0L : left.getSentAtEpochMs();
                long rightSent = right == null ? 0L : right.getSentAtEpochMs();
                return Long.compare(leftSent, rightSent);
            }
        });

        Map<String, DwdCapParser.ParsedAlert> activeByCanonicalKey = new LinkedHashMap<>();
        Map<String, String> operationalKeyByIdentifier = new LinkedHashMap<>();
        for (DwdCapParser.ParsedAlert alert : orderedAlerts) {
            String canonicalKey = resolveOperationalAlertKey(alert, operationalKeyByIdentifier);
            if (canonicalKey == null || canonicalKey.trim().isEmpty()) {
                continue;
            }
            alert.setOperationalAlertKey(canonicalKey);
            if (alert.getIdentifier() != null && !alert.getIdentifier().trim().isEmpty()) {
                operationalKeyByIdentifier.put(alert.getIdentifier().trim(), canonicalKey);
            }
            if ("Cancel".equalsIgnoreCase(alert.getMsgType())) {
                activeByCanonicalKey.remove(canonicalKey);
                continue;
            }
            activeByCanonicalKey.put(canonicalKey, alert);
        }

        return new ArrayList<>(activeByCanonicalKey.values());
    }

    private String resolveOperationalAlertKey(
            DwdCapParser.ParsedAlert alert,
            Map<String, String> operationalKeyByIdentifier) {
        if (alert == null) {
            return null;
        }

        List<String> referenceIdentifiers = alert.getReferenceIdentifiers();
        if (referenceIdentifiers != null) {
            for (String referenceIdentifier : referenceIdentifiers) {
                if (referenceIdentifier == null || referenceIdentifier.trim().isEmpty()) {
                    continue;
                }
                String resolved = operationalKeyByIdentifier.get(referenceIdentifier.trim());
                if (resolved != null && !resolved.trim().isEmpty()) {
                    return resolved.trim();
                }
            }
            for (String referenceIdentifier : referenceIdentifiers) {
                if (referenceIdentifier != null && !referenceIdentifier.trim().isEmpty()) {
                    return referenceIdentifier.trim();
                }
            }
        }
        return alert.getIdentifier();
    }

    private void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Local unit tests use the Android stub logger.
        }
    }

    private int snapshotSize(WarningSnapshot snapshot) {
        return snapshot == null || snapshot.getRecords() == null ? 0 : snapshot.getRecords().size();
    }

    private boolean isGermanSnapshot(WarningSnapshot snapshot) {
        if (snapshot == null || snapshot.getRecords() == null || snapshot.getRecords().isEmpty()) {
            return true;
        }
        for (WarningRecord record : snapshot.getRecords()) {
            if (!isGermanRecord(record)) {
                return false;
            }
        }
        return true;
    }

    private boolean isGermanRecord(WarningRecord record) {
        if (record == null) {
            return true;
        }

        String language = record.getMetadata() == null ? null : record.getMetadata().get("dwd.language");
        if (language != null && !language.trim().isEmpty()) {
            return DwdCapParser.isGermanLanguage(language);
        }

        String sourceId = StringUtils.trimToNull(record.getSourceId());
        if (sourceId == null) {
            return true;
        }
        if (sourceId.endsWith(".ENG")
                || sourceId.endsWith(".EN")
                || sourceId.endsWith(".FRA")
                || sourceId.endsWith(".FR")
                || sourceId.endsWith(".ESP")
                || sourceId.endsWith(".ES")) {
            return false;
        }
        return true;
    }

    private String summarizeLanguages(List<DwdCapParser.ParsedAlert> alerts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (alerts == null) {
            return counts.toString();
        }

        for (DwdCapParser.ParsedAlert alert : alerts) {
            DwdCapParser.ParsedInfo info = alert == null ? null : alert.getInfo();
            String language = info == null ? null : info.getLanguage();
            String normalized = StringUtils.trimToNull(language);
            normalized = normalized == null ? "unknown" : normalized;
            Integer count = counts.get(normalized);
            counts.put(normalized, count == null ? 1 : count + 1);
        }
        return counts.toString();
    }

    private String summarizeRenderModes(WarningSnapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (snapshot == null || snapshot.getRecords() == null) {
            return counts.toString();
        }

        for (WarningRecord record : snapshot.getRecords()) {
            String renderMode = record == null || record.getRenderMode() == null
                    ? "null"
                    : record.getRenderMode().name();
            Integer count = counts.get(renderMode);
            counts.put(renderMode, count == null ? 1 : count + 1);
        }
        return counts.toString();
    }

    private static final class ParsedFeed {

        private final DwdCapZipFetcher.FetchResult fetchResult;
        private final List<DwdCapParser.ParsedAlert> parsedAlerts;
        private final List<String> failureMessages;
        private final boolean hadPartialFailure;

        private ParsedFeed(
                DwdCapZipFetcher.FetchResult fetchResult,
                List<DwdCapParser.ParsedAlert> parsedAlerts,
                List<String> failureMessages,
                boolean hadPartialFailure) {
            this.fetchResult = fetchResult;
            this.parsedAlerts = parsedAlerts;
            this.failureMessages = failureMessages;
            this.hadPartialFailure = hadPartialFailure;
        }
    }

    private static final class PreparedRefresh {

        private final long now;
        private final DwdCapZipFetcher.FetchResult fetchResult;
        private final List<DwdCapParser.ParsedAlert> parsedAlerts;
        private final List<DwdCapParser.ParsedAlert> reconciledAlerts;
        private final WarningSnapshot snapshot;
        private final List<String> failureMessages;
        private final boolean hadPartialFailure;

        private PreparedRefresh(
                long now,
                DwdCapZipFetcher.FetchResult fetchResult,
                List<DwdCapParser.ParsedAlert> parsedAlerts,
                List<DwdCapParser.ParsedAlert> reconciledAlerts,
                WarningSnapshot snapshot,
                List<String> failureMessages,
                boolean hadPartialFailure) {
            this.now = now;
            this.fetchResult = fetchResult;
            this.parsedAlerts = parsedAlerts;
            this.reconciledAlerts = reconciledAlerts;
            this.snapshot = snapshot;
            this.failureMessages = failureMessages;
            this.hadPartialFailure = hadPartialFailure;
        }
    }
}
