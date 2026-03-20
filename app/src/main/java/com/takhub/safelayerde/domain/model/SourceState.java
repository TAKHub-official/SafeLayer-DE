package com.takhub.safelayerde.domain.model;

import com.takhub.safelayerde.util.StringUtils;

import java.io.File;
import java.util.regex.Pattern;

public class SourceState {

    private static final String HOSTNAME_TOKEN = "(?:[A-Za-z0-9-]+\\.)+[A-Za-z0-9-]+";
    private static final String IPV4_TOKEN = "(?:\\d{1,3}\\.){3}\\d{1,3}";
    private static final String IPV6_TOKEN = "(?:\\[[0-9A-Fa-f:.%]+\\]|(?:[0-9A-Fa-f]{0,4}:){2,}[0-9A-Fa-f:.%]+)";
    private static final String NETWORK_HOST_TOKEN =
            "(?:" + HOSTNAME_TOKEN + "|" + IPV4_TOKEN + "|" + IPV6_TOKEN + ")";
    private static final String NETWORK_ENDPOINT_TOKEN =
            "(?:/?(?:" + HOSTNAME_TOKEN + "/)?" + NETWORK_HOST_TOKEN + "(?::\\d{1,5})?)";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?|file|content|intent|javascript|mailto):\\S+");
    private static final Pattern QUOTED_NETWORK_ENDPOINT_PATTERN = Pattern.compile(
            "([\"'])(" + NETWORK_ENDPOINT_TOKEN + ")\\1");
    private static final Pattern HOST_NETWORK_ENDPOINT_PATTERN = Pattern.compile(
            "(?i)(\\b(?:unable to resolve host|failed to resolve host|unknown host|host)\\s*[\"']?)("
                    + NETWORK_ENDPOINT_TOKEN + ")([\"']?)");
    private static final Pattern CONNECTION_NETWORK_ENDPOINT_PATTERN = Pattern.compile(
            "(?i)(\\b(?:failed to connect to|connect(?:ed)? to|connection to|from)\\s+)("
                    + NETWORK_ENDPOINT_TOKEN + ")");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile(
            "(?:(?<=^)|(?<=[\\s=:(\\[]))(?:/[^\\s]+|[A-Za-z]:\\\\[^\\s]+)");

    public enum Status {
        DISABLED,
        LIVE,
        DEGRADED_WITH_DATA,
        STALE,
        ERROR_WITH_CACHE,
        ERROR_NO_CACHE
    }

    private SourceIdentity sourceIdentity;
    private Status status = Status.ERROR_NO_CACHE;
    private long lastSuccessEpochMs;
    private String lastErrorMessage;

    public static SourceState forSource(SourceIdentity sourceIdentity) {
        SourceState sourceState = new SourceState();
        sourceState.setSourceIdentity(sourceIdentity);
        return sourceState;
    }

    public SourceIdentity getSourceIdentity() {
        return sourceIdentity;
    }

    public void setSourceIdentity(SourceIdentity sourceIdentity) {
        this.sourceIdentity = sourceIdentity;
    }

    public WarningSourceType getSourceType() {
        return sourceIdentity == null ? null : sourceIdentity.toWarningSourceType();
    }

    public void setSourceType(WarningSourceType sourceType) {
        this.sourceIdentity = SourceIdentity.fromWarningSourceType(sourceType);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status == null ? Status.ERROR_NO_CACHE : status;
    }

    public long getLastSuccessEpochMs() {
        return lastSuccessEpochMs;
    }

    public void setLastSuccessEpochMs(long lastSuccessEpochMs) {
        this.lastSuccessEpochMs = lastSuccessEpochMs;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = sanitizeErrorMessage(lastErrorMessage);
    }

    public boolean hasSuccessfulFetch() {
        return lastSuccessEpochMs > 0L;
    }

    public static String sanitizeErrorMessage(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.replace('\r', ' ').replace('\n', ' ');
        normalized = URL_PATTERN.matcher(normalized).replaceAll("external resource");
        normalized = sanitizeNetworkEndpoints(normalized);
        normalized = ABSOLUTE_PATH_PATTERN.matcher(normalized).replaceAll("local path");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.replace("local path" + File.separator, "local path");
    }

    private static String sanitizeNetworkEndpoints(String value) {
        String normalized = replaceMatchGroup(value, QUOTED_NETWORK_ENDPOINT_PATTERN, 2, "network endpoint");
        normalized = replaceMatchGroup(normalized, HOST_NETWORK_ENDPOINT_PATTERN, 2, "network endpoint");
        normalized = replaceMatchGroup(normalized, CONNECTION_NETWORK_ENDPOINT_PATTERN, 2, "network endpoint");
        return normalized;
    }

    private static String replaceMatchGroup(
            String value,
            Pattern pattern,
            int groupIndex,
            String replacement) {
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            int relativeStart = matcher.start(groupIndex) - matcher.start();
            int relativeEnd = matcher.end(groupIndex) - matcher.start();
            String updated = matched.substring(0, relativeStart)
                    + replacement
                    + matched.substring(relativeEnd);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(updated));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
