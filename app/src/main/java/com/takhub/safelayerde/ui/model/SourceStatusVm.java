package com.takhub.safelayerde.ui.model;

public class SourceStatusVm {

    private final String sourceLabel;
    private final String statusLabel;
    private final String dataAgeLabel;
    private final boolean isLive;
    private final boolean degraded;
    private final boolean failed;
    private final String lastErrorMessage;

    public SourceStatusVm(
            String sourceLabel,
            String statusLabel,
            String dataAgeLabel,
            boolean isLive,
            boolean degraded,
            boolean failed,
            String lastErrorMessage) {
        this.sourceLabel = sourceLabel;
        this.statusLabel = statusLabel;
        this.dataAgeLabel = dataAgeLabel;
        this.isLive = isLive;
        this.degraded = degraded;
        this.failed = failed;
        this.lastErrorMessage = lastErrorMessage;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getDataAgeLabel() {
        return dataAgeLabel;
    }

    public boolean isLive() {
        return isLive;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
