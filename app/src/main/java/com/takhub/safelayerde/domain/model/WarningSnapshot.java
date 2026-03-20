package com.takhub.safelayerde.domain.model;

import java.util.ArrayList;
import java.util.List;

public class WarningSnapshot {

    private WarningSourceType sourceType;
    private long fetchedAtEpochMs;
    private List<WarningRecord> records = new ArrayList<>();

    public WarningSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(WarningSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public long getFetchedAtEpochMs() {
        return fetchedAtEpochMs;
    }

    public void setFetchedAtEpochMs(long fetchedAtEpochMs) {
        this.fetchedAtEpochMs = fetchedAtEpochMs;
    }

    public List<WarningRecord> getRecords() {
        return records;
    }

    public void setRecords(List<WarningRecord> records) {
        this.records = records == null ? new ArrayList<WarningRecord>() : records;
    }
}
