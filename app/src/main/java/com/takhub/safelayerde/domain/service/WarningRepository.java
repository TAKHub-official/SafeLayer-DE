package com.takhub.safelayerde.domain.service;

import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;

import java.util.ArrayList;
import java.util.List;

public class WarningRepository {

    private WarningSnapshot bbkSnapshot;
    private WarningSnapshot dwdSnapshot;

    public WarningSnapshot getSnapshot(WarningSourceType sourceType) {
        if (sourceType == WarningSourceType.BBK) {
            return bbkSnapshot;
        }
        if (sourceType == WarningSourceType.DWD) {
            return dwdSnapshot;
        }
        return null;
    }

    public void update(WarningSourceType sourceType, WarningSnapshot snapshot) {
        if (sourceType == WarningSourceType.BBK) {
            bbkSnapshot = snapshot;
        } else if (sourceType == WarningSourceType.DWD) {
            dwdSnapshot = snapshot;
        }
    }

    public List<WarningRecord> getRecords(WarningSourceType sourceType) {
        return recordsFromSnapshot(getSnapshot(sourceType));
    }

    public WarningRecord findByStableId(String stableId) {
        WarningRecord record = findByStableId(stableId, bbkSnapshot);
        return record != null ? record : findByStableId(stableId, dwdSnapshot);
    }

    private List<WarningRecord> recordsFromSnapshot(WarningSnapshot snapshot) {
        if (snapshot == null || snapshot.getRecords() == null) {
            return new ArrayList<WarningRecord>();
        }
        return new ArrayList<>(snapshot.getRecords());
    }

    private WarningRecord findByStableId(String stableId, WarningSnapshot snapshot) {
        if (stableId == null || snapshot == null || snapshot.getRecords() == null) {
            return null;
        }
        for (WarningRecord record : snapshot.getRecords()) {
            if (record != null && stableId.equals(record.getStableId())) {
                return record;
            }
        }
        return null;
    }
}
