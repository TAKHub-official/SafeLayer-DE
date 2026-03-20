package com.takhub.safelayerde.plugin;

import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.platform.UiThreadRunner;
import com.takhub.safelayerde.render.model.WarningRenderSpec;
import com.takhub.safelayerde.util.StringUtils;

final class WarningFocusController {

    interface WarningRecordResolver {
        WarningRecord findRecord(String stableId);
    }

    interface FocusExecutor {
        boolean canFocus(WarningRecord record, WarningRenderSpec spec);
        void focus(WarningRecord record, WarningRenderSpec spec);
        void onDeferred(String stableId, WarningRecord record);
    }

    enum FocusResult {
        APPLIED,
        DEFERRED,
        CLEARED
    }

    private String pendingStableId;

    void request(final String stableId, UiThreadRunner uiThreadRunner, final Runnable replayAction) {
        if (stableId == null || uiThreadRunner == null) {
            return;
        }
        pendingStableId = stableId;
        uiThreadRunner.run(new Runnable() {
            @Override
            public void run() {
                if (replayAction != null) {
                    replayAction.run();
                }
            }
        });
    }

    FocusResult focus(
            String stableId,
            WarningRecordResolver recordResolver,
            FocusExecutor focusExecutor) {
        if (stableId == null) {
            return FocusResult.CLEARED;
        }

        WarningRecord record = recordResolver == null ? null : recordResolver.findRecord(stableId);
        if (record == null) {
            clearPending(stableId);
            return FocusResult.CLEARED;
        }

        WarningRenderSpec spec = WarningRenderSpec.from(record);
        if (!spec.hasFocusableLocation()) {
            clearPending(stableId);
            return FocusResult.CLEARED;
        }

        if (focusExecutor == null || !focusExecutor.canFocus(record, spec)) {
            pendingStableId = stableId;
            if (focusExecutor != null) {
                focusExecutor.onDeferred(stableId, record);
            }
            return FocusResult.DEFERRED;
        }

        focusExecutor.focus(record, spec);
        clearPending(stableId);
        return FocusResult.APPLIED;
    }

    FocusResult replayPending(
            WarningRecordResolver recordResolver,
            FocusExecutor focusExecutor) {
        String stableId = StringUtils.trimToNull(pendingStableId);
        if (stableId == null) {
            return FocusResult.CLEARED;
        }
        return focus(stableId, recordResolver, focusExecutor);
    }

    void clearPending(String stableId) {
        if (stableId != null && stableId.equals(pendingStableId)) {
            pendingStableId = null;
        }
    }

    String pendingStableId() {
        return pendingStableId;
    }

    void reset() {
        pendingStableId = null;
    }
}
