package com.takhub.safelayerde.source.common;

import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class WarningSnapshotExpiryFilter {

    static final long EXPIRED_WARNING_GRACE_PERIOD_MS = 2L * 60L * 60L * 1000L;

    private WarningSnapshotExpiryFilter() {
    }

    public static WarningSnapshot filterExpiredRecords(WarningSnapshot snapshot, long nowEpochMs) {
        if (snapshot == null || snapshot.getRecords() == null || snapshot.getRecords().isEmpty()) {
            return snapshot;
        }

        List<WarningRecord> filteredRecords = new ArrayList<>();
        for (WarningRecord record : snapshot.getRecords()) {
            if (shouldKeep(record, nowEpochMs)) {
                filteredRecords.add(record);
            }
        }

        if (filteredRecords.size() == snapshot.getRecords().size()) {
            return snapshot;
        }

        WarningSnapshot filteredSnapshot = new WarningSnapshot();
        filteredSnapshot.setSourceType(snapshot.getSourceType());
        filteredSnapshot.setFetchedAtEpochMs(snapshot.getFetchedAtEpochMs());
        filteredSnapshot.setRecords(filteredRecords);
        return filteredSnapshot;
    }

    static boolean shouldKeep(WarningRecord record, long nowEpochMs) {
        if (record == null) {
            return false;
        }

        long expiresAtEpochMs = record.getExpiresAtEpochMs();
        if (expiresAtEpochMs <= 0L) {
            return true;
        }
        return nowEpochMs <= expiresAtEpochMs + EXPIRED_WARNING_GRACE_PERIOD_MS;
    }
}
