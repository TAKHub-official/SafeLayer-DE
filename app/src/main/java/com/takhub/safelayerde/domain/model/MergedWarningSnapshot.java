package com.takhub.safelayerde.domain.model;

import java.util.ArrayList;
import java.util.List;

public class MergedWarningSnapshot {

    private long mergedAtEpochMs;
    private List<WarningRecord> records = new ArrayList<>();

    public long getMergedAtEpochMs() {
        return mergedAtEpochMs;
    }

    public void setMergedAtEpochMs(long mergedAtEpochMs) {
        this.mergedAtEpochMs = mergedAtEpochMs;
    }

    public List<WarningRecord> getRecords() {
        return records;
    }

    public void setRecords(List<WarningRecord> records) {
        this.records = records == null ? new ArrayList<WarningRecord>() : records;
    }
}
