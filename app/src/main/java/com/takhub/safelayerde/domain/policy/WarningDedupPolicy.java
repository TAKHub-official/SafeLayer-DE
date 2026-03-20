package com.takhub.safelayerde.domain.policy;

import com.takhub.safelayerde.domain.model.WarningRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WarningDedupPolicy {

    public List<WarningRecord> deduplicate(List<WarningRecord> records) {
        List<WarningRecord> deduplicated = new ArrayList<>();
        Set<String> seenStableIds = new LinkedHashSet<>();
        if (records == null) {
            return deduplicated;
        }

        for (WarningRecord record : records) {
            if (record == null || record.getStableId() == null) {
                continue;
            }
            if (seenStableIds.add(record.getStableId())) {
                deduplicated.add(record);
            }
        }
        return deduplicated;
    }
}
