package com.takhub.safelayerde.ui.pane;

import android.content.Context;

import com.takhub.safelayerde.R;
import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningAreaRef;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.model.WarningSeverity;
import com.takhub.safelayerde.domain.policy.SeverityColorPolicy;
import com.takhub.safelayerde.ui.model.RadarStatusVm;
import com.takhub.safelayerde.ui.model.SourceStatusVm;
import com.takhub.safelayerde.ui.model.WarningDetailVm;
import com.takhub.safelayerde.ui.model.WarningListItemVm;
import com.takhub.safelayerde.util.StringUtils;
import com.takhub.safelayerde.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class DetailViewModelMapper {

    private static final Pattern AREA_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final String[] TECHNICAL_AREA_PREFIXES = new String[] {
            "polygonial event area",
            "polygonal event area",
            "event area",
            "warning area"
    };

    private final SeverityColorPolicy colorPolicy = new SeverityColorPolicy();

    public WarningListItemVm toListItem(Context context, WarningRecord record) {
        return toListItem(context, record, TimeUtils.nowEpochMs());
    }

    WarningListItemVm toListItem(Context context, WarningRecord record, long nowEpochMs) {
        String primaryTitle = listPrimaryTitle(record);
        return new WarningListItemVm(
                record == null ? null : record.getStableId(),
                primaryTitle,
                severityLabel(context, record == null ? null : record.getSeverity()),
                colorPolicy.colorForSeverity(record == null ? null : record.getSeverity()),
                formatTimeframe(record == null ? 0L : record.getExpiresAtEpochMs(), nowEpochMs),
                null,
                listLocationLabel(record),
                null,
                prefixedDataAgeLabel(context, record == null ? 0L : record.getLastFetchedAtEpochMs(), nowEpochMs, false));
    }

    public WarningDetailVm toDetail(Context context, WarningRecord record) {
        return toDetail(context, record, TimeUtils.nowEpochMs());
    }

    WarningDetailVm toDetail(Context context, WarningRecord record, long nowEpochMs) {
        String sourceLabel = sourceLabel(context, record == null ? null : record.getSourceType(), record == null ? null : record.getSourceLabel());
        String displayAccuracy = displayAccuracyLabel(context, record);
        return new WarningDetailVm(
                record.getStableId(),
                record.getTitle(),
                context.getString(R.string.safelayer_detail_source_format, sourceLabel),
                severityLabel(context, record.getSeverity()),
                colorPolicy.colorForSeverity(record.getSeverity()),
                formatTimeframe(record.getExpiresAtEpochMs(), nowEpochMs),
                detailAreaSummaryLabel(context, record),
                record.getDescriptionText() == null ? "" : record.getDescriptionText(),
                record.getInstructionText() == null ? "" : record.getInstructionText(),
                prefixedDataAgeLabel(context, record.getLastFetchedAtEpochMs(), nowEpochMs, true),
                context.getString(R.string.safelayer_detail_accuracy_format, displayAccuracy));
    }

    public SourceStatusVm toSourceStatus(Context context, WarningSourceType sourceType, SourceState state) {
        return toSourceStatus(context, sourceType, state, TimeUtils.nowEpochMs());
    }

    SourceStatusVm toSourceStatus(Context context, WarningSourceType sourceType, SourceState state, long nowEpochMs) {
        SourceState.Status status = state == null ? SourceState.Status.ERROR_NO_CACHE : state.getStatus();
        long lastSuccessEpochMs = lastSuccessEpochMs(state);
        boolean degraded = status == SourceState.Status.DEGRADED_WITH_DATA
                || status == SourceState.Status.ERROR_WITH_CACHE
                || status == SourceState.Status.STALE;
        boolean failed = status == SourceState.Status.ERROR_NO_CACHE;
        return new SourceStatusVm(
                sourceLabel(context, sourceType, null),
                statusLabel(context, status, false),
                lastSuccessEpochMs <= 0L
                        ? context.getString(R.string.safelayer_data_age_unknown)
                        : prefixedDataAgeLabel(context, lastSuccessEpochMs, nowEpochMs, false),
                status == SourceState.Status.LIVE,
                degraded,
                failed,
                null);
    }

    public RadarStatusVm toRadarStatus(Context context, RadarFrame radarFrame, SourceState state) {
        return toRadarStatus(context, radarFrame, state, TimeUtils.nowEpochMs());
    }

    RadarStatusVm toRadarStatus(Context context, RadarFrame radarFrame, SourceState state, long nowEpochMs) {
        SourceState.Status status = state == null ? SourceState.Status.DISABLED : state.getStatus();
        boolean inactive = status == SourceState.Status.DISABLED;
        long lastSuccessEpochMs = radarFrame != null && radarFrame.getFrameEpochMs() > 0L
                ? radarFrame.getFrameEpochMs()
                : (state == null ? 0L : state.getLastSuccessEpochMs());
        String productLabel = radarFrame == null ? null : radarFrame.getProductLabel();
        String defaultProductLabel = context.getString(R.string.safelayer_radar_product_rv);
        String resolvedProduct = inactive
                ? context.getString(R.string.safelayer_radar_product_placeholder)
                : (productLabel == null || productLabel.trim().isEmpty()
                ? defaultProductLabel
                : productLabel);
        boolean degraded = status == SourceState.Status.DEGRADED_WITH_DATA
                || status == SourceState.Status.ERROR_WITH_CACHE;
        return new RadarStatusVm(
                statusLabel(context, status, true),
                inactive
                        ? context.getString(R.string.safelayer_radar_data_age_inactive)
                        : lastSuccessEpochMs <= 0L
                        ? context.getString(R.string.safelayer_data_age_unknown)
                        : prefixedDataAgeLabel(context, lastSuccessEpochMs, nowEpochMs, false),
                resolvedProduct,
                inactive,
                degraded || status == SourceState.Status.ERROR_NO_CACHE
                        ? StringUtils.trimToNull(state == null ? null : state.getLastErrorMessage())
                        : null);
    }

    private long lastSuccessEpochMs(SourceState state) {
        return state == null ? 0L : state.getLastSuccessEpochMs();
    }

    private String statusLabel(Context context, SourceState.Status status, boolean allowInactive) {
        if (allowInactive && status == SourceState.Status.DISABLED) {
            return context.getString(R.string.safelayer_status_inactive);
        }
        switch (status == null ? SourceState.Status.ERROR_NO_CACHE : status) {
            case LIVE:
                return context.getString(R.string.safelayer_status_live);
            case DEGRADED_WITH_DATA:
            case ERROR_WITH_CACHE:
                return context.getString(R.string.safelayer_status_degraded);
            case STALE:
                return context.getString(R.string.safelayer_status_stale);
            case DISABLED:
            case ERROR_NO_CACHE:
            default:
                return context.getString(R.string.safelayer_status_error_no_cache);
        }
    }

    private String sourceLabel(Context context, WarningSourceType sourceType, String fallback) {
        if (sourceType == WarningSourceType.BBK) {
            return context.getString(R.string.safelayer_bbk_label);
        }
        String normalizedFallback = StringUtils.trimToNull(fallback);
        if (normalizedFallback != null) {
            return normalizedFallback;
        }
        if (sourceType == WarningSourceType.DWD) {
            return context.getString(R.string.safelayer_dwd_label);
        }
        return context.getString(R.string.safelayer_bbk_label);
    }

    private String severityLabel(Context context, WarningSeverity severity) {
        WarningSeverity resolvedSeverity = severity == null ? WarningSeverity.UNKNOWN : severity;
        switch (resolvedSeverity) {
            case EXTREME:
                return context.getString(R.string.safelayer_severity_extreme);
            case SEVERE:
                return context.getString(R.string.safelayer_severity_severe);
            case MODERATE:
                return context.getString(R.string.safelayer_severity_moderate);
            case MINOR:
                return context.getString(R.string.safelayer_severity_minor);
            case UNKNOWN:
            default:
                return context.getString(R.string.safelayer_severity_unknown);
        }
    }

    private String prefixedDataAgeLabel(Context context, long epochMs, long nowEpochMs, boolean detailVariant) {
        int unknownLabelResId = detailVariant
                ? R.string.safelayer_detail_data_age_unknown
                : R.string.safelayer_data_age_unknown;
        int prefixedFormatResId = detailVariant
                ? R.string.safelayer_detail_data_age_format
                : R.string.safelayer_data_age_format;

        if (epochMs <= 0L) {
            return context.getString(unknownLabelResId);
        }

        return context.getString(prefixedFormatResId, relativeDataAgeLabel(context, epochMs, nowEpochMs));
    }

    private String relativeDataAgeLabel(Context context, long epochMs, long nowEpochMs) {
        TimeUtils.RelativeDataAgeBucket bucket = TimeUtils.relativeDataAgeBucket(epochMs, nowEpochMs);
        switch (bucket.getType()) {
            case JUST_NOW:
                return context.getString(R.string.safelayer_relative_age_just_now);
            case UNDER_THIRTY_MINUTES:
                return context.getString(R.string.safelayer_relative_age_under_30_minutes);
            case UNDER_ONE_HOUR:
                return context.getString(R.string.safelayer_relative_age_under_1_hour);
            case UNDER_HOURS:
                return context.getString(
                        R.string.safelayer_relative_age_under_hours_format,
                        bucket.getUpperBoundHours());
            case OVER_TWELVE_HOURS:
            default:
                return context.getString(R.string.safelayer_relative_age_over_12_hours);
        }
    }

    private String displayAccuracyLabel(Context context, WarningRecord record) {
        RenderMode renderMode = record == null ? null : record.getRenderMode();
        GeometryConfidence confidence = record == null || record.getGeometry() == null
                ? GeometryConfidence.NONE
                : record.getGeometry().getGeometryConfidence();
        boolean approximate = record != null
                && record.getGeometry() != null
                && record.getGeometry().isApproximate();

        if (renderMode == RenderMode.POLYGON && confidence == GeometryConfidence.CONFIRMED && !approximate) {
            return context.getString(R.string.safelayer_accuracy_confirmed_polygon);
        }
        if (renderMode == RenderMode.POLYGON) {
            return context.getString(R.string.safelayer_accuracy_approximate_area);
        }
        if (renderMode == RenderMode.MARKER) {
            if (confidence == GeometryConfidence.APPROXIMATE || approximate) {
                return context.getString(R.string.safelayer_accuracy_approximate_area)
                        + " · "
                        + context.getString(R.string.safelayer_accuracy_marker_fallback);
            }
            return context.getString(R.string.safelayer_accuracy_marker_fallback);
        }
        return context.getString(R.string.safelayer_accuracy_list_only);
    }

    private String detailAreaSummaryLabel(Context context, WarningRecord record) {
        String summary = detailAreaSummary(record);
        if (summary == null) {
            return "";
        }
        return context.getString(R.string.safelayer_detail_location_format, summary);
    }

    String detailAreaSummary(WarningRecord record) {
        String summary = namedAreaSummary(record, 3);
        if (summary != null) {
            return summary;
        }
        summary = areaSummary(record, 3);
        if (summary != null) {
            return summary;
        }
        return StringUtils.trimToNull(record == null ? null : record.getTitle());
    }

    private String listPrimaryTitle(WarningRecord record) {
        String title = StringUtils.trimToNull(record == null ? null : record.getTitle());
        if (title != null) {
            return title;
        }
        return listLocationLabel(record);
    }

    private String listLocationLabel(WarningRecord record) {
        String namedArea = firstNamedAreaLabel(record);
        if (namedArea != null) {
            return namedArea;
        }

        String areaFallback = firstAreaLabel(record);
        if (areaFallback != null) {
            return areaFallback;
        }

        return null;
    }

    private String areaSummary(WarningRecord record, int maxEntries) {
        if (record == null || record.getAreaRefs() == null || record.getAreaRefs().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int included = 0;
        int namedAreas = 0;
        for (WarningAreaRef areaRef : record.getAreaRefs()) {
            String label = areaLabel(areaRef);
            if (label == null) {
                continue;
            }
            namedAreas++;
            if (included >= maxEntries) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label);
            included++;
        }
        if (namedAreas == 0) {
            return null;
        }
        if (namedAreas > included) {
            builder.append(" +").append(namedAreas - included).append(" weitere");
        }
        return builder.toString();
    }

    private String areaLabel(WarningAreaRef areaRef) {
        if (areaRef == null) {
            return null;
        }
        String areaName = namedAreaLabel(areaRef);
        if (areaName != null) {
            return areaName;
        }
        if (areaRef.getWarnCellId() != null && !areaRef.getWarnCellId().trim().isEmpty()) {
            return "Warnzelle " + areaRef.getWarnCellId().trim();
        }
        return null;
    }

    private String namedAreaSummary(WarningRecord record, int maxEntries) {
        if (record == null || record.getAreaRefs() == null || record.getAreaRefs().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int included = 0;
        int namedAreas = 0;
        for (WarningAreaRef areaRef : record.getAreaRefs()) {
            String label = namedAreaLabel(areaRef);
            if (label == null) {
                continue;
            }
            namedAreas++;
            if (included >= maxEntries) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label);
            included++;
        }
        if (namedAreas == 0) {
            return null;
        }
        if (namedAreas > included) {
            builder.append(" +").append(namedAreas - included).append(" weitere");
        }
        return builder.toString();
    }

    private String firstNamedAreaLabel(WarningRecord record) {
        if (record == null || record.getAreaRefs() == null) {
            return null;
        }
        for (WarningAreaRef areaRef : record.getAreaRefs()) {
            String label = namedAreaLabel(areaRef);
            if (label != null) {
                return label;
            }
        }
        return null;
    }

    private String firstAreaLabel(WarningRecord record) {
        if (record == null || record.getAreaRefs() == null) {
            return null;
        }
        for (WarningAreaRef areaRef : record.getAreaRefs()) {
            String label = areaLabel(areaRef);
            if (label != null) {
                return label;
            }
        }
        return null;
    }

    private String namedAreaLabel(WarningAreaRef areaRef) {
        return cleanAreaLabel(areaRef == null ? null : areaRef.getAreaName());
    }

    private String formatTimeframe(long expiresAtEpochMs) {
        return formatTimeframe(expiresAtEpochMs, TimeUtils.nowEpochMs());
    }

    String formatTimeframe(long expiresAtEpochMs, long nowEpochMs) {
        if (expiresAtEpochMs <= 0L) {
            return "";
        }
        if (TimeUtils.expiredMoreThan(expiresAtEpochMs, nowEpochMs, TimeUtils.TWELVE_HOURS_MS)) {
            return "";
        }
        String pattern = isSameCalendarDay(expiresAtEpochMs, nowEpochMs) ? "HH:mm" : "dd.MM HH:mm";
        return "bis " + new SimpleDateFormat(pattern, Locale.GERMANY).format(new Date(expiresAtEpochMs));
    }

    private boolean isSameCalendarDay(long leftEpochMs, long rightEpochMs) {
        Calendar left = Calendar.getInstance();
        left.setTimeInMillis(leftEpochMs);
        Calendar right = Calendar.getInstance();
        right.setTimeInMillis(rightEpochMs);
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private String cleanAreaLabel(String rawValue) {
        String trimmed = StringUtils.trimToNull(rawValue);
        if (trimmed == null) {
            return null;
        }

        String[] segments = AREA_SPLIT_PATTERN.split(trimmed);
        int startIndex = 0;
        while (startIndex < segments.length && isTechnicalAreaSegment(segments[startIndex])) {
            startIndex++;
        }
        if (startIndex >= segments.length) {
            return null;
        }
        if (startIndex == 0) {
            return trimmed;
        }

        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < segments.length; index++) {
            String segment = StringUtils.trimToNull(segments[index]);
            if (segment == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(segment);
        }
        return StringUtils.trimToNull(builder.toString());
    }

    private boolean isTechnicalAreaSegment(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return true;
        }

        String normalized = trimmed.toLowerCase(Locale.GERMANY);
        for (String technicalPrefix : TECHNICAL_AREA_PREFIXES) {
            if (normalized.equals(technicalPrefix)) {
                return true;
            }
        }
        return false;
    }

}
