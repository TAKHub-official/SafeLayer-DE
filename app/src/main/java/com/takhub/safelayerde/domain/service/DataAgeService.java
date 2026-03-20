package com.takhub.safelayerde.domain.service;

import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.plugin.SafeLayerConstants;
import com.takhub.safelayerde.source.radar.DwdRadarProduct;

public class DataAgeService {

    private static final long STALE_INTERVAL_MULTIPLIER = 3L;

    public boolean isWarningSnapshotStale(WarningSnapshot snapshot, long nowEpochMs) {
        return isStale(
                warningSnapshotEpochMs(snapshot),
                nowEpochMs,
                SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS * STALE_INTERVAL_MULTIPLIER);
    }

    public boolean isRadarFrameStale(RadarFrame radarFrame, long nowEpochMs, DwdRadarProduct product) {
        long updateIntervalMs = product == null
                ? SafeLayerConstants.RADAR_REFRESH_INTERVAL_MS
                : product.getUpdateIntervalMs();
        return isStale(
                radarFrameEpochMs(radarFrame),
                nowEpochMs,
                updateIntervalMs * STALE_INTERVAL_MULTIPLIER);
    }

    public long warningSnapshotEpochMs(WarningSnapshot snapshot) {
        return snapshot == null ? 0L : snapshot.getFetchedAtEpochMs();
    }

    public long radarFrameEpochMs(RadarFrame radarFrame) {
        return radarFrame == null ? 0L : radarFrame.getFrameEpochMs();
    }

    private boolean isStale(long payloadEpochMs, long nowEpochMs, long staleThresholdMs) {
        if (payloadEpochMs <= 0L) {
            return true;
        }
        long ageMs = Math.max(0L, nowEpochMs - payloadEpochMs);
        return ageMs > Math.max(0L, staleThresholdMs);
    }
}
