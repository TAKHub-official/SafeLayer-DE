package com.takhub.safelayerde.ui.model;

public class WarningDetailVm {

    private final String stableId;
    private final String title;
    private final String sourceLabel;
    private final String severityLabel;
    private final int severityColor;
    private final String timeframeLabel;
    private final String locationLabel;
    private final String descriptionText;
    private final String instructionText;
    private final String dataAgeLabel;
    private final String displayAccuracyLabel;

    public WarningDetailVm(
            String stableId,
            String title,
            String sourceLabel,
            String severityLabel,
            int severityColor,
            String timeframeLabel,
            String locationLabel,
            String descriptionText,
            String instructionText,
            String dataAgeLabel,
            String displayAccuracyLabel) {
        this.stableId = stableId;
        this.title = title;
        this.sourceLabel = sourceLabel;
        this.severityLabel = severityLabel;
        this.severityColor = severityColor;
        this.timeframeLabel = timeframeLabel;
        this.locationLabel = locationLabel;
        this.descriptionText = descriptionText;
        this.instructionText = instructionText;
        this.dataAgeLabel = dataAgeLabel;
        this.displayAccuracyLabel = displayAccuracyLabel;
    }

    public String getStableId() {
        return stableId;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceLabel() {
        return sourceLabel;
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

    public String getLocationLabel() {
        return locationLabel;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public String getInstructionText() {
        return instructionText;
    }

    public String getDataAgeLabel() {
        return dataAgeLabel;
    }

    public String getDisplayAccuracyLabel() {
        return displayAccuracyLabel;
    }
}
