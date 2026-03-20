package com.takhub.safelayerde.ui.model;

public class WarningListItemVm {

    private final String stableId;
    private final String title;
    private final String severityLabel;
    private final int severityColor;
    private final String timeframeLabel;
    private final String sourceLabel;
    private final String locationLabel;
    private final String displayAccuracyLabel;
    private final String dataAgeLabel;

    public WarningListItemVm(
            String stableId,
            String title,
            String severityLabel,
            int severityColor,
            String timeframeLabel,
            String sourceLabel,
            String locationLabel,
            String displayAccuracyLabel,
            String dataAgeLabel) {
        this.stableId = stableId;
        this.title = title;
        this.severityLabel = severityLabel;
        this.severityColor = severityColor;
        this.timeframeLabel = timeframeLabel;
        this.sourceLabel = sourceLabel;
        this.locationLabel = locationLabel;
        this.displayAccuracyLabel = displayAccuracyLabel;
        this.dataAgeLabel = dataAgeLabel;
    }

    public String getStableId() {
        return stableId;
    }

    public String getTitle() {
        return title;
    }

    public String getSeverityLabel() {
        return severityLabel;
    }

    public int getSeverityColor() {
        return severityColor;
    }

    public String getTimeframeLabel() {
        return timeframeLabel;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public String getDisplayAccuracyLabel() {
        return displayAccuracyLabel;
    }

    public String getDataAgeLabel() {
        return dataAgeLabel;
    }
}
