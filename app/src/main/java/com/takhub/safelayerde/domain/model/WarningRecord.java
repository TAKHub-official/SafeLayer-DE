package com.takhub.safelayerde.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WarningRecord {

    private String stableId;
    private WarningSourceType sourceType;
    private String sourceId;
    private String version;
    private String status;
    private String msgType;
    private String category;
    private String eventCode;
    private String eventLabel;
    private String title;
    private String sourceLabel;
    private String descriptionText;
    private String instructionText;
    private WarningSeverity severity = WarningSeverity.UNKNOWN;
    private WarningUrgency urgency = WarningUrgency.UNKNOWN;
    private String certainty;
    private RenderMode renderMode;
    private long sentAtEpochMs;
    private long effectiveAtEpochMs;
    private long onsetAtEpochMs;
    private long expiresAtEpochMs;
    private long lastFetchedAtEpochMs;
    private WarningGeometry geometry = new WarningGeometry();
    private List<WarningAreaRef> areaRefs = new ArrayList<>();
    private Map<String, String> metadata = new LinkedHashMap<>();

    public String getStableId() {
        return stableId;
    }

    public void setStableId(String stableId) {
        this.stableId = stableId;
    }

    public WarningSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(WarningSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventLabel() {
        return eventLabel;
    }

    public void setEventLabel(String eventLabel) {
        this.eventLabel = eventLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public void setDescriptionText(String descriptionText) {
        this.descriptionText = descriptionText;
    }

    public String getInstructionText() {
        return instructionText;
    }

    public void setInstructionText(String instructionText) {
        this.instructionText = instructionText;
    }

    public WarningSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(WarningSeverity severity) {
        this.severity = severity == null ? WarningSeverity.UNKNOWN : severity;
    }

    public WarningUrgency getUrgency() {
        return urgency;
    }

    public void setUrgency(WarningUrgency urgency) {
        this.urgency = urgency == null ? WarningUrgency.UNKNOWN : urgency;
    }

    public String getCertainty() {
        return certainty;
    }

    public void setCertainty(String certainty) {
        this.certainty = certainty;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public long getSentAtEpochMs() {
        return sentAtEpochMs;
    }

    public void setSentAtEpochMs(long sentAtEpochMs) {
        this.sentAtEpochMs = sentAtEpochMs;
    }

    public long getEffectiveAtEpochMs() {
        return effectiveAtEpochMs;
    }

    public void setEffectiveAtEpochMs(long effectiveAtEpochMs) {
        this.effectiveAtEpochMs = effectiveAtEpochMs;
    }

    public long getOnsetAtEpochMs() {
        return onsetAtEpochMs;
    }

    public void setOnsetAtEpochMs(long onsetAtEpochMs) {
        this.onsetAtEpochMs = onsetAtEpochMs;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public long getLastFetchedAtEpochMs() {
        return lastFetchedAtEpochMs;
    }

    public void setLastFetchedAtEpochMs(long lastFetchedAtEpochMs) {
        this.lastFetchedAtEpochMs = lastFetchedAtEpochMs;
    }

    public WarningGeometry getGeometry() {
        return geometry;
    }

    public void setGeometry(WarningGeometry geometry) {
        this.geometry = geometry == null ? new WarningGeometry() : geometry;
    }

    public List<WarningAreaRef> getAreaRefs() {
        return areaRefs;
    }

    public void setAreaRefs(List<WarningAreaRef> areaRefs) {
        this.areaRefs = areaRefs == null ? new ArrayList<WarningAreaRef>() : areaRefs;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<String, String>() : metadata;
    }
}
