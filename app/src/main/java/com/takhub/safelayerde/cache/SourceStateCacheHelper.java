package com.takhub.safelayerde.cache;

import com.takhub.safelayerde.domain.model.SourceState;

import java.util.ArrayList;
import java.util.List;

public final class SourceStateCacheHelper {

    private SourceStateCacheHelper() {
    }

    public static List<SourceState> upsert(List<SourceState> existingStates, SourceState incomingState) {
        List<SourceState> merged = new ArrayList<>();
        if (incomingState == null) {
            return existingStates == null ? merged : new ArrayList<>(existingStates);
        }
        boolean replaced = false;

        if (existingStates != null) {
            for (SourceState state : existingStates) {
                if (state == null) {
                    continue;
                }

                if (isSameSource(state, incomingState)) {
                    merged.add(incomingState);
                    replaced = true;
                } else {
                    merged.add(state);
                }
            }
        }

        if (!replaced) {
            merged.add(incomingState);
        }
        return merged;
    }

    private static boolean isSameSource(SourceState existing, SourceState incoming) {
        if (existing == null || incoming == null) {
            return false;
        }
        if (existing.getSourceIdentity() != null && incoming.getSourceIdentity() != null) {
            return existing.getSourceIdentity() == incoming.getSourceIdentity();
        }
        return existing.getSourceType() != null && existing.getSourceType() == incoming.getSourceType();
    }
}
