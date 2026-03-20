package com.takhub.safelayerde.source.common;

import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.WarningSnapshot;

public class SourceRefreshResult {

    private final boolean success;
    private final WarningSnapshot snapshot;
    private final RadarFrame radarFrame;
    private final SourceState sourceState;
    private final String errorMessage;

    private SourceRefreshResult(
            boolean success,
            WarningSnapshot snapshot,
            RadarFrame radarFrame,
            SourceState sourceState,
            String errorMessage) {
        this.success = success;
        this.snapshot = snapshot;
        this.radarFrame = radarFrame;
        this.sourceState = sourceState;
        this.errorMessage = errorMessage;
    }

    public static SourceRefreshResult success(WarningSnapshot snapshot, SourceState sourceState) {
        return success(snapshot, sourceState, null);
    }

    public static SourceRefreshResult success(
            WarningSnapshot snapshot,
            SourceState sourceState,
            String errorMessage) {
        return new SourceRefreshResult(true, snapshot, null, sourceState, errorMessage);
    }

    public static SourceRefreshResult radarSuccess(RadarFrame radarFrame, SourceState sourceState) {
        return new SourceRefreshResult(true, null, radarFrame, sourceState, null);
    }

    public static SourceRefreshResult radarSuccess(
            RadarFrame radarFrame,
            SourceState sourceState,
            String errorMessage) {
        return new SourceRefreshResult(true, null, radarFrame, sourceState, errorMessage);
    }

    public static SourceRefreshResult failure(SourceState sourceState, String errorMessage) {
        return new SourceRefreshResult(false, null, null, sourceState, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public WarningSnapshot getSnapshot() {
        return snapshot;
    }

    public RadarFrame getRadarFrame() {
        return radarFrame;
    }

    public SourceState getSourceState() {
        return sourceState;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
