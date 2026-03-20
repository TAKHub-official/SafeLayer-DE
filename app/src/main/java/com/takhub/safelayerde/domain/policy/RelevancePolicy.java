package com.takhub.safelayerde.domain.policy;

import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.RenderMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RelevancePolicy {

    private static final Comparator<WarningRecord> BY_RELEVANCE = new Comparator<WarningRecord>() {
        @Override
        public int compare(WarningRecord left, WarningRecord right) {
            int severityCompare = Integer.compare(
                    left == null || left.getSeverity() == null ? Integer.MAX_VALUE : left.getSeverity().ordinal(),
                    right == null || right.getSeverity() == null ? Integer.MAX_VALUE : right.getSeverity().ordinal());
            if (severityCompare != 0) {
                return severityCompare;
            }

            int exactnessCompare = Integer.compare(rankExactness(left), rankExactness(right));
            if (exactnessCompare != 0) {
                return exactnessCompare;
            }

            long leftExpires = left == null ? 0L : left.getExpiresAtEpochMs();
            long rightExpires = right == null ? 0L : right.getExpiresAtEpochMs();
            return Long.compare(rightExpires, leftExpires);
        }
    };

    public List<WarningRecord> sortByRelevance(List<WarningRecord> records) {
        List<WarningRecord> sorted = records == null
                ? new ArrayList<WarningRecord>()
                : new ArrayList<>(records);
        Collections.sort(sorted, BY_RELEVANCE);
        return sorted;
    }

    private static int rankExactness(WarningRecord record) {
        if (record == null || record.getRenderMode() == null) {
            return 2;
        }
        if (record.getRenderMode() == RenderMode.LIST_ONLY) {
            return 2;
        }
        if (record.getGeometry() != null
                && record.getGeometry().getGeometryConfidence() == GeometryConfidence.CONFIRMED
                && !record.getGeometry().isApproximate()) {
            return 0;
        }
        return 1;
    }
}
