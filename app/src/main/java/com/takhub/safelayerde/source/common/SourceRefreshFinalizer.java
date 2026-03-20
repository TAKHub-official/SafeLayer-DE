package com.takhub.safelayerde.source.common;

import android.util.Log;

import com.takhub.safelayerde.cache.CacheStore;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.service.DataAgeService;
import com.takhub.safelayerde.source.radar.DwdRadarProduct;
import com.takhub.safelayerde.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SourceRefreshFinalizer {

    private static final String TAG = "SafeLayerFinalizer";

    private final CacheStore cacheStore;
    private final DataAgeService dataAgeService;

    public SourceRefreshFinalizer(CacheStore cacheStore, DataAgeService dataAgeService) {
        this.cacheStore = cacheStore;
        this.dataAgeService = dataAgeService == null ? new DataAgeService() : dataAgeService;
    }

    public SourceRefreshResult finalizeWarningRefresh(
            SourceIdentity sourceIdentity,
            WarningSnapshot freshSnapshot,
            WarningSnapshot cachedSnapshot,
            boolean freshSnapshotUsableOnFailure,
            long nowEpochMs,
            List<String> failureMessages) {
        WarningSnapshot normalizedFreshSnapshot =
                WarningSnapshotExpiryFilter.filterExpiredRecords(freshSnapshot, nowEpochMs);
        WarningSnapshot normalizedCachedSnapshot =
                WarningSnapshotExpiryFilter.filterExpiredRecords(cachedSnapshot, nowEpochMs);
        String errorMessage = joinMessages(failureMessages);
        boolean hasFailures = StringUtils.trimToNull(errorMessage) != null;

        boolean deliveredFreshSnapshot = normalizedFreshSnapshot != null
                && (!hasFailures || freshSnapshotUsableOnFailure);
        if (!hasRestorableWarningSnapshot(normalizedCachedSnapshot)) {
            normalizedCachedSnapshot = null;
        }
        WarningSnapshot deliveredSnapshot = deliveredFreshSnapshot
                ? normalizedFreshSnapshot
                : normalizedCachedSnapshot;

        SourceState sourceState = SourceState.forSource(sourceIdentity);
        sourceState.setLastErrorMessage(errorMessage);

        if (deliveredSnapshot == null) {
            sourceState.setStatus(SourceState.Status.ERROR_NO_CACHE);
            persistSourceState(sourceState);
            return SourceRefreshResult.failure(sourceState, errorMessage);
        }

        sourceState.setLastSuccessEpochMs(dataAgeService.warningSnapshotEpochMs(deliveredSnapshot));
        if (deliveredFreshSnapshot) {
            sourceState.setStatus(hasFailures
                    ? SourceState.Status.DEGRADED_WITH_DATA
                    : SourceState.Status.LIVE);
            persistWarningSnapshot(deliveredSnapshot);
        } else {
            sourceState.setStatus(dataAgeService.isWarningSnapshotStale(deliveredSnapshot, nowEpochMs)
                    ? SourceState.Status.STALE
                    : SourceState.Status.ERROR_WITH_CACHE);
        }

        persistSourceState(sourceState);
        return SourceRefreshResult.success(deliveredSnapshot, sourceState, errorMessage);
    }

    public SourceState restoreWarningState(
            SourceIdentity sourceIdentity,
            SourceState persistedState,
            WarningSnapshot restoredSnapshot,
            long nowEpochMs) {
        WarningSnapshot normalizedSnapshot =
                WarningSnapshotExpiryFilter.filterExpiredRecords(restoredSnapshot, nowEpochMs);
        String errorMessage = StringUtils.trimToNull(
                persistedState == null ? null : persistedState.getLastErrorMessage());

        if (!hasRestorableWarningSnapshot(normalizedSnapshot)) {
            SourceState sourceState = SourceState.forSource(sourceIdentity);
            sourceState.setLastErrorMessage(errorMessage);
            sourceState.setStatus(SourceState.Status.ERROR_NO_CACHE);
            return sourceState;
        }

        SourceState sourceState = SourceState.forSource(sourceIdentity);
        sourceState.setLastErrorMessage(errorMessage);
        sourceState.setLastSuccessEpochMs(dataAgeService.warningSnapshotEpochMs(normalizedSnapshot));
        sourceState.setStatus(dataAgeService.isWarningSnapshotStale(normalizedSnapshot, nowEpochMs)
                ? SourceState.Status.STALE
                : SourceState.Status.ERROR_WITH_CACHE);
        return sourceState;
    }

    public SourceRefreshResult finalizeRadarRefresh(
            RadarFrame freshFrame,
            RadarFrame cachedFrame,
            boolean freshFrameUsableOnFailure,
            long nowEpochMs,
            DwdRadarProduct product,
            List<String> failureMessages) {
        String errorMessage = joinMessages(failureMessages);
        boolean hasFailures = StringUtils.trimToNull(errorMessage) != null;

        boolean deliveredFreshFrame = freshFrame != null && (!hasFailures || freshFrameUsableOnFailure);
        RadarFrame deliveredFrame = deliveredFreshFrame ? freshFrame : cachedFrame;

        SourceState sourceState = SourceState.forSource(SourceIdentity.RADAR);
        sourceState.setLastErrorMessage(errorMessage);
        sourceState.setLastSuccessEpochMs(dataAgeService.radarFrameEpochMs(deliveredFrame));

        if (deliveredFrame == null) {
            sourceState.setStatus(SourceState.Status.ERROR_NO_CACHE);
            persistSourceState(sourceState);
            return SourceRefreshResult.failure(sourceState, errorMessage);
        }

        if (deliveredFreshFrame) {
            sourceState.setStatus(hasFailures
                    ? SourceState.Status.DEGRADED_WITH_DATA
                    : SourceState.Status.LIVE);
            persistRadarFrame(deliveredFrame);
        } else {
            sourceState.setStatus(dataAgeService.isRadarFrameStale(deliveredFrame, nowEpochMs, product)
                    ? SourceState.Status.STALE
                    : SourceState.Status.ERROR_WITH_CACHE);
        }

        persistSourceState(sourceState);
        return SourceRefreshResult.radarSuccess(deliveredFrame, sourceState, errorMessage);
    }

    private String joinMessages(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        Set<String> normalizedMessages = new LinkedHashSet<>();
        for (String message : messages) {
            String normalized = StringUtils.trimToNull(SourceState.sanitizeErrorMessage(message));
            if (normalized != null) {
                normalizedMessages.add(normalized);
            }
        }
        if (normalizedMessages.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (String message : normalizedMessages) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(message);
        }
        return builder.toString();
    }

    private void persistWarningSnapshot(WarningSnapshot snapshot) {
        if (cacheStore == null || snapshot == null) {
            return;
        }
        try {
            cacheStore.writeWarningSnapshot(snapshot);
        } catch (IOException exception) {
            logPersistFailure("warning-snapshot", exception);
        }
    }

    private void persistRadarFrame(RadarFrame radarFrame) {
        if (cacheStore == null || radarFrame == null) {
            return;
        }
        try {
            cacheStore.writeRadarFrame(radarFrame);
        } catch (IOException exception) {
            logPersistFailure("radar-frame", exception);
        }
    }

    private void persistSourceState(SourceState sourceState) {
        if (cacheStore == null || sourceState == null) {
            return;
        }
        try {
            cacheStore.upsertSourceState(sourceState);
        } catch (IOException exception) {
            logPersistFailure("source-state", exception);
        }
    }

    private void logPersistFailure(String target, IOException exception) {
        String message = "Failed to persist " + target + ".";
        String detail = StringUtils.trimToNull(
                SourceState.sanitizeErrorMessage(exception == null ? null : exception.getMessage()));
        try {
            Log.w(TAG, message, exception);
        } catch (RuntimeException ignored) {
            // Host-side tests may not provide the Android logger runtime.
        }
        SafeLayerDebugLog.w(TAG, detail == null ? message : message + " " + detail);
    }

    private boolean hasRestorableWarningSnapshot(WarningSnapshot snapshot) {
        return snapshot != null
                && snapshot.getRecords() != null
                && !snapshot.getRecords().isEmpty();
    }
}
