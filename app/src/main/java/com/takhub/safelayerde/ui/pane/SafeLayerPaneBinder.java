package com.takhub.safelayerde.ui.pane;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.takhub.safelayerde.BuildConfig;
import com.takhub.safelayerde.R;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.ui.model.RadarStatusVm;
import com.takhub.safelayerde.ui.model.SourceStatusVm;
import com.takhub.safelayerde.ui.model.WarningDetailVm;
import com.takhub.safelayerde.ui.model.WarningListItemVm;
import com.takhub.safelayerde.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SafeLayerPaneBinder {

    private enum Screen {
        OVERVIEW,
        DETAIL,
        HELP
    }

    private final View paneView;
    private final ScrollView scrollView;
    private final View manualRefreshView;
    private final ProgressBar manualRefreshProgressView;
    private final TextView manualRefreshTextView;
    private final View overviewView;
    private final View ninaTabView;
    private final View dwdTabView;
    private final View radarTabView;
    private final View ninaContentView;
    private final View dwdContentView;
    private final View radarContentView;
    private final View bbkInfoButtonView;
    private final View dwdInfoButtonView;
    private final View radarInfoButtonView;
    private final ToggleBinding bbkToggleView;
    private final ToggleBinding dwdToggleView;
    private final ToggleBinding radarToggleView;
    private final Map<WarningSourceType, SourceBinding> sourceBindings =
            new EnumMap<>(WarningSourceType.class);
    private final TextView radarStatusView;
    private final TextView radarDataAgeView;
    private final TextView radarProductView;
    private final View radarTransparencyControlsView;
    private final SeekBar radarTransparencySeekBar;
    private final TextView radarTransparencyValueView;
    private final TextView radarTransparencyInactiveView;
    private final LinearLayout detailView;
    private final TextView detailTitleView;
    private final TextView detailSourceSeverityView;
    private final TextView detailTimeframeView;
    private final TextView detailLocationView;
    private final TextView detailAccuracyView;
    private final TextView detailDescriptionView;
    private final TextView detailInstructionView;
    private final TextView detailDataAgeView;
    private final Button detailBackButton;
    private final View helpView;
    private final TextView helpLinkView;
    private final TextView helpVersionView;
    private final Button helpBackButton;

    public SafeLayerPaneBinder(View paneView) {
        this.paneView = paneView;
        this.scrollView = paneView.findViewById(R.id.sv_main);
        this.manualRefreshView = paneView.findViewById(R.id.ll_manual_refresh);
        this.manualRefreshProgressView = paneView.findViewById(R.id.pb_manual_refresh);
        this.manualRefreshTextView = paneView.findViewById(R.id.tv_manual_refresh);
        this.overviewView = paneView.findViewById(R.id.ll_overview);
        this.ninaTabView = paneView.findViewById(R.id.btn_tab_nina);
        this.dwdTabView = paneView.findViewById(R.id.btn_tab_dwd);
        this.radarTabView = paneView.findViewById(R.id.btn_tab_radar);
        this.ninaContentView = paneView.findViewById(R.id.ll_tab_nina_content);
        this.dwdContentView = paneView.findViewById(R.id.ll_tab_dwd_content);
        this.radarContentView = paneView.findViewById(R.id.ll_tab_radar_content);
        this.bbkInfoButtonView = paneView.findViewById(R.id.btn_bbk_info);
        this.dwdInfoButtonView = paneView.findViewById(R.id.btn_dwd_info);
        this.radarInfoButtonView = paneView.findViewById(R.id.btn_radar_info);
        this.bbkToggleView = createToggleBinding(
                R.id.tb_bbk_layer,
                R.id.tv_bbk_layer_off,
                R.id.tv_bbk_layer_on);
        this.dwdToggleView = createToggleBinding(
                R.id.tb_dwd_layer,
                R.id.tv_dwd_layer_off,
                R.id.tv_dwd_layer_on);
        this.radarToggleView = createToggleBinding(
                R.id.tb_radar_layer,
                R.id.tv_radar_layer_off,
                R.id.tv_radar_layer_on);
        sourceBindings.put(
                WarningSourceType.BBK,
                createSourceBinding(
                        R.id.tv_bbk_status,
                        R.id.tv_bbk_data_age,
                        R.id.ll_bbk_warning_list,
                        R.id.tv_bbk_no_warnings));
        sourceBindings.put(
                WarningSourceType.DWD,
                createSourceBinding(
                        R.id.tv_dwd_status,
                        R.id.tv_dwd_data_age,
                        R.id.ll_dwd_warning_list,
                        R.id.tv_dwd_no_warnings));
        this.radarStatusView = paneView.findViewById(R.id.tv_radar_status);
        this.radarDataAgeView = paneView.findViewById(R.id.tv_radar_data_age);
        this.radarProductView = paneView.findViewById(R.id.tv_radar_product);
        this.radarTransparencyControlsView = paneView.findViewById(R.id.ll_radar_transparency_controls);
        this.radarTransparencySeekBar = paneView.findViewById(R.id.sb_radar_transparency);
        this.radarTransparencyValueView = paneView.findViewById(R.id.tv_radar_transparency_value);
        this.radarTransparencyInactiveView = paneView.findViewById(R.id.tv_radar_transparency_inactive);
        this.detailView = paneView.findViewById(R.id.ll_detail);
        this.detailTitleView = paneView.findViewById(R.id.tv_detail_title);
        this.detailSourceSeverityView = paneView.findViewById(R.id.tv_detail_source_severity);
        this.detailTimeframeView = paneView.findViewById(R.id.tv_detail_timeframe);
        this.detailLocationView = paneView.findViewById(R.id.tv_detail_location);
        this.detailAccuracyView = paneView.findViewById(R.id.tv_detail_accuracy);
        this.detailDescriptionView = paneView.findViewById(R.id.tv_detail_description);
        this.detailInstructionView = paneView.findViewById(R.id.tv_detail_instruction);
        this.detailDataAgeView = paneView.findViewById(R.id.tv_detail_data_age);
        this.detailBackButton = paneView.findViewById(R.id.btn_detail_back);
        this.helpView = paneView.findViewById(R.id.ll_help);
        this.helpLinkView = paneView.findViewById(R.id.tv_help_takhub_link);
        this.helpVersionView = paneView.findViewById(R.id.tv_help_version);
        this.helpBackButton = paneView.findViewById(R.id.btn_help_back);
    }

    public void bindTabNavigation(
            SafeLayerPaneState.Tab activeTab,
            View.OnClickListener ninaListener,
            View.OnClickListener dwdListener,
            View.OnClickListener radarListener) {
        bindTab(ninaTabView, activeTab == SafeLayerPaneState.Tab.NINA, ninaListener);
        bindTab(dwdTabView, activeTab == SafeLayerPaneState.Tab.DWD, dwdListener);
        bindTab(radarTabView, activeTab == SafeLayerPaneState.Tab.RADAR, radarListener);
    }

    public void bindTabContent(SafeLayerPaneState.Tab activeTab) {
        SafeLayerPaneState.Tab resolvedTab =
                activeTab == null ? SafeLayerPaneState.Tab.NINA : activeTab;
        setVisible(ninaContentView, resolvedTab == SafeLayerPaneState.Tab.NINA);
        setVisible(dwdContentView, resolvedTab == SafeLayerPaneState.Tab.DWD);
        setVisible(radarContentView, resolvedTab == SafeLayerPaneState.Tab.RADAR);
    }

    public void bindManualRefreshActive(boolean active) {
        setVisible(manualRefreshView, active);
        setVisible(manualRefreshProgressView, active);
        setVisible(manualRefreshTextView, active);
    }

    public void bindInfoButtons(View.OnClickListener listener) {
        bindAction(bbkInfoButtonView, listener);
        bindAction(dwdInfoButtonView, listener);
        bindAction(radarInfoButtonView, listener);
    }

    public void bindPullToRefresh(final int thresholdPx, final OnPullToRefreshListener listener) {
        if (scrollView == null) {
            return;
        }
        if (listener == null) {
            scrollView.setOnTouchListener(null);
            return;
        }

        final PullToRefreshGestureTracker tracker = new PullToRefreshGestureTracker(thresholdPx);
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent == null) {
                    return false;
                }
                boolean shouldTrigger = tracker.onTouchEvent(
                        motionEvent.getActionMasked(),
                        motionEvent.getY(),
                        scrollView.getScrollY() <= 0);
                if (shouldTrigger) {
                    listener.onPullToRefresh();
                }
                return false;
            }
        });
    }

    public void bindLayerToggles(
            boolean bbkVisible,
            boolean dwdVisible,
            boolean radarVisible,
            CompoundButton.OnCheckedChangeListener bbkListener,
            CompoundButton.OnCheckedChangeListener dwdListener,
            CompoundButton.OnCheckedChangeListener radarListener) {
        bindToggle(bbkToggleView, bbkVisible, bbkListener);
        bindToggle(dwdToggleView, dwdVisible, dwdListener);
        bindToggle(radarToggleView, radarVisible, radarListener);
    }

    public void bindSourceStatus(WarningSourceType sourceType, SourceStatusVm vm) {
        if (vm == null) {
            return;
        }
        SourceBinding sourceBinding = sourceBindingFor(sourceType);
        if (sourceBinding == null) {
            return;
        }
        bindOptionalText(sourceBinding.statusView, vm.getStatusLabel());
        bindStatusDetail(sourceBinding.dataAgeView, vm.getDataAgeLabel(), vm.getLastErrorMessage());
    }

    public void bindWarningList(
            WarningSourceType sourceType,
            List<WarningListItemVm> items,
            final OnItemClickListener listener) {
        SourceBinding sourceBinding = sourceBindingFor(sourceType);
        if (sourceBinding == null) {
            return;
        }

        List<WarningListItemVm> safeItems = items == null ? new ArrayList<WarningListItemVm>() : items;
        String listSignature = buildListSignature(safeItems);
        if (TextUtils.equals(sourceBinding.lastListSignature, listSignature)) {
            bindWarningDataAges(sourceType, safeItems);
            return;
        }

        sourceBinding.lastListSignature = listSignature;
        sourceBinding.warningDataAgeViews.clear();
        sourceBinding.listView.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(paneView.getContext());
        for (final WarningListItemVm item : safeItems) {
            View row = inflater.inflate(R.layout.list_item_warning, sourceBinding.listView, false);
            View severityBar = row.findViewById(R.id.view_severity_bar);
            TextView titleView = row.findViewById(R.id.tv_warning_title);
            TextView severityView = row.findViewById(R.id.tv_warning_severity);
            TextView timeframeView = row.findViewById(R.id.tv_warning_timeframe);
            TextView locationView = row.findViewById(R.id.tv_warning_location);
            TextView dataAgeView = row.findViewById(R.id.tv_warning_data_age);

            GradientDrawable background = new GradientDrawable();
            background.setColor(item.getSeverityColor());
            severityBar.setBackground(background);
            titleView.setText(item.getTitle());
            bindOptionalText(severityView, item.getSeverityLabel());
            bindOptionalText(timeframeView, item.getTimeframeLabel());
            bindOptionalText(locationView, item.getLocationLabel());
            bindOptionalText(dataAgeView, item.getDataAgeLabel());
            if (!TextUtils.isEmpty(item.getStableId())) {
                sourceBinding.warningDataAgeViews.put(item.getStableId(), dataAgeView);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onItemClick(item.getStableId());
                    }
                }
            });
            sourceBinding.listView.addView(row);
        }
    }

    public void bindWarningDataAges(WarningSourceType sourceType, List<WarningListItemVm> items) {
        SourceBinding sourceBinding = sourceBindingFor(sourceType);
        if (sourceBinding == null || items == null || sourceBinding.warningDataAgeViews.isEmpty()) {
            return;
        }

        for (WarningListItemVm item : items) {
            if (item == null || TextUtils.isEmpty(item.getStableId())) {
                continue;
            }
            bindOptionalText(sourceBinding.warningDataAgeViews.get(item.getStableId()), item.getDataAgeLabel());
        }
    }

    public void showDetail(WarningDetailVm vm, final Runnable onBack) {
        if (vm == null) {
            return;
        }
        detailTitleView.setText(vm.getTitle());
        detailSourceSeverityView.setText(vm.getSourceLabel() + " | " + vm.getSeverityLabel());
        detailSourceSeverityView.setTextColor(vm.getSeverityColor());
        bindOptionalText(detailTimeframeView, vm.getTimeframeLabel());
        bindOptionalText(detailLocationView, vm.getLocationLabel());
        bindOptionalText(detailAccuracyView, vm.getDisplayAccuracyLabel());
        detailDescriptionView.setText(vm.getDescriptionText());
        detailInstructionView.setText(vm.getInstructionText());
        bindDetailDataAge(vm.getDataAgeLabel());
        bindAction(detailBackButton, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onBack != null) {
                    onBack.run();
                }
            }
        });
        applyScreen(Screen.DETAIL);
    }

    public void showList() {
        applyScreen(Screen.OVERVIEW);
    }

    public void showHelp(final Runnable onBack) {
        bindHelpLink();
        if (helpVersionView != null) {
            helpVersionView.setText(
                    paneView.getContext().getString(
                            R.string.safelayer_help_version_format,
                            BuildConfig.VERSION_NAME));
        }
        bindAction(helpBackButton, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onBack != null) {
                    onBack.run();
                }
            }
        });
        applyScreen(Screen.HELP);
    }

    public void bindDetailDataAge(String dataAgeLabel) {
        bindOptionalText(detailDataAgeView, dataAgeLabel);
    }

    public void bindRadarStatus(RadarStatusVm vm) {
        if (vm == null) {
            return;
        }
        bindOptionalText(radarStatusView, vm.getStatusLabel());
        bindStatusDetail(radarDataAgeView, vm.getDataAgeLabel(), vm.getErrorMessage());
        bindOptionalText(radarProductView, vm.getProductLabel());
        bindRadarControls(vm.isInactive());
    }

    public void bindRadarTransparency(int transparencyPercent, SeekBar.OnSeekBarChangeListener listener) {
        if (radarTransparencySeekBar == null) {
            return;
        }

        int clamped = Math.max(0, Math.min(100, transparencyPercent));
        radarTransparencySeekBar.setOnSeekBarChangeListener(null);
        radarTransparencySeekBar.setMax(100);
        radarTransparencySeekBar.setProgress(clamped);
        bindRadarTransparencyValue(clamped);
        radarTransparencySeekBar.setOnSeekBarChangeListener(listener);
    }

    public void bindRadarTransparencyValue(int transparencyPercent) {
        if (radarTransparencyValueView == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(100, transparencyPercent));
        radarTransparencyValueView.setText(clamped + "%");
    }

    public void showNoData(WarningSourceType sourceType, SourceState sourceState, int recordCount) {
        SourceBinding sourceBinding = sourceBindingFor(sourceType);
        if (sourceBinding == null) {
            return;
        }
        boolean show = recordCount <= 0;
        if (show) {
            sourceBinding.emptyView.setText(resolveEmptyStateText(sourceState));
        }
        setVisible(sourceBinding.emptyView, show);
    }

    private void bindRadarControls(boolean inactive) {
        if (radarTransparencyControlsView != null) {
            radarTransparencyControlsView.setAlpha(inactive ? 0.5f : 1f);
        }
        if (radarTransparencySeekBar != null) {
            radarTransparencySeekBar.setEnabled(!inactive);
        }
        if (radarTransparencyValueView != null) {
            radarTransparencyValueView.setEnabled(!inactive);
            radarTransparencyValueView.setAlpha(inactive ? 0.5f : 1f);
        }
        if (radarTransparencyInactiveView != null) {
            radarTransparencyInactiveView.setVisibility(inactive ? View.VISIBLE : View.GONE);
        }
        if (radarProductView != null) {
            radarProductView.setEnabled(!inactive);
            radarProductView.setAlpha(inactive ? 0.5f : 1f);
        }
    }

    private void bindStatusDetail(TextView view, String primary, String secondary) {
        if (view == null) {
            return;
        }
        String firstLine = StringUtils.trimToNull(primary);
        String secondLine = StringUtils.trimToNull(secondary);
        if (firstLine == null && secondLine == null) {
            view.setText(null);
            view.setVisibility(View.GONE);
            return;
        }
        if (firstLine == null) {
            view.setText(secondLine);
        } else if (secondLine == null) {
            view.setText(firstLine);
        } else {
            view.setText(firstLine + "\n" + secondLine);
        }
        view.setVisibility(View.VISIBLE);
    }

    private void applyScreen(Screen screen) {
        setVisible(overviewView, screen == Screen.OVERVIEW);
        setVisible(detailView, screen == Screen.DETAIL);
        setVisible(helpView, screen == Screen.HELP);
        resetScrollPosition();
    }

    private void bindHelpLink() {
        if (helpLinkView == null) {
            return;
        }
        final String helpUrl = StringUtils.trimToNull(helpLinkView.getText() == null
                ? null
                : helpLinkView.getText().toString());
        bindAction(helpLinkView, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openExternalUrl(helpUrl);
            }
        });
    }

    private void bindToggle(
            final ToggleBinding toggleBinding,
            boolean checked,
            final CompoundButton.OnCheckedChangeListener listener) {
        if (toggleBinding == null || toggleBinding.button == null) {
            return;
        }
        toggleBinding.button.setOnCheckedChangeListener(null);
        toggleBinding.button.setChecked(checked);
        syncToggleBinding(toggleBinding, checked);
        toggleBinding.button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                syncToggleBinding(toggleBinding, isChecked);
                if (listener != null) {
                    listener.onCheckedChanged(buttonView, isChecked);
                }
            }
        });
    }

    private void bindTab(View tabView, boolean active, View.OnClickListener listener) {
        if (tabView == null) {
            return;
        }
        tabView.setSelected(active);
        tabView.setActivated(active);
        tabView.setOnClickListener(listener);
    }

    private void bindAction(View actionView, View.OnClickListener listener) {
        if (actionView == null) {
            return;
        }
        actionView.setOnClickListener(listener);
    }

    private void openExternalUrl(String rawUrl) {
        String normalizedUrl = normalizeExternalUrl(rawUrl);
        if (normalizedUrl == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(paneView.getContext().getPackageManager()) == null) {
            return;
        }
        try {
            paneView.getContext().startActivity(intent);
        } catch (RuntimeException ignored) {
            // The host may restrict external launches; fail closed instead of crashing ATAK.
        }
    }

    static String normalizeExternalUrl(String rawUrl) {
        String normalized = StringUtils.trimToNull(rawUrl);
        if (normalized == null) {
            return null;
        }

        try {
            URI rawUri = new URI(normalized);
            String rawScheme = StringUtils.trimToNull(rawUri.getScheme());
            if (rawScheme != null && !"https".equalsIgnoreCase(rawScheme)) {
                return null;
            }

            URI resolvedUri = rawScheme == null
                    ? new URI("https://" + normalized)
                    : rawUri;
            String scheme = StringUtils.trimToNull(resolvedUri.getScheme());
            String host = StringUtils.trimToNull(resolvedUri.getHost());
            if (!"https".equalsIgnoreCase(scheme) || host == null) {
                return null;
            }
            return resolvedUri.toString();
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private void bindOptionalText(TextView textView, String value) {
        if (textView == null) {
            return;
        }
        String text = StringUtils.trimToNull(value);
        textView.setText(text);
        textView.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private CharSequence resolveEmptyStateText(SourceState sourceState) {
        SourceState.Status status = sourceState == null ? SourceState.Status.ERROR_NO_CACHE : sourceState.getStatus();
        String detail = sourceState == null ? null : StringUtils.trimToNull(sourceState.getLastErrorMessage());
        if (status == SourceState.Status.LIVE) {
            return paneView.getContext().getString(R.string.safelayer_no_warnings_live);
        }
        if (status == SourceState.Status.DEGRADED_WITH_DATA
                || status == SourceState.Status.ERROR_WITH_CACHE) {
            return appendDetail(
                    paneView.getContext().getString(R.string.safelayer_no_warnings_degraded),
                    detail);
        }
        if (status == SourceState.Status.STALE) {
            return appendDetail(
                    paneView.getContext().getString(R.string.safelayer_no_warnings_stale),
                    detail);
        }
        return appendDetail(
                paneView.getContext().getString(R.string.safelayer_no_successful_fetch),
                detail);
    }

    private CharSequence appendDetail(String message, String detail) {
        if (detail == null) {
            return message;
        }
        return message + "\n" + detail;
    }

    private String buildListSignature(List<WarningListItemVm> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        StringBuilder signature = new StringBuilder(items.size() * 96);
        for (WarningListItemVm item : items) {
            if (item == null) {
                signature.append("null;");
                continue;
            }
            appendSignaturePart(signature, item.getStableId());
            appendSignaturePart(signature, item.getTitle());
            appendSignaturePart(signature, item.getSeverityLabel());
            signature.append(item.getSeverityColor()).append('|');
            appendSignaturePart(signature, item.getTimeframeLabel());
            appendSignaturePart(signature, item.getLocationLabel());
            appendSignaturePart(signature, item.getDisplayAccuracyLabel());
            signature.append(';');
        }
        return signature.toString();
    }

    private void appendSignaturePart(StringBuilder signature, String value) {
        signature.append(value == null ? "" : value).append('|');
    }

    private void resetScrollPosition() {
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
    }

    private void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private SourceBinding createSourceBinding(int statusId, int dataAgeId, int listId, int emptyId) {
        return new SourceBinding(
                (TextView) paneView.findViewById(statusId),
                (TextView) paneView.findViewById(dataAgeId),
                (LinearLayout) paneView.findViewById(listId),
                (TextView) paneView.findViewById(emptyId));
    }

    private SourceBinding sourceBindingFor(WarningSourceType sourceType) {
        return sourceBindings.get(sourceType);
    }

    private ToggleBinding createToggleBinding(int toggleId, int offLabelId, int onLabelId) {
        return new ToggleBinding(
                (CompoundButton) paneView.findViewById(toggleId),
                (TextView) paneView.findViewById(offLabelId),
                (TextView) paneView.findViewById(onLabelId));
    }

    private void syncToggleBinding(ToggleBinding toggleBinding, boolean checked) {
        toggleBinding.button.setActivated(checked);
        toggleBinding.button.setSelected(checked);
        bindToggleLabel(toggleBinding.offLabel, !checked);
        bindToggleLabel(toggleBinding.onLabel, checked);
    }

    private void bindToggleLabel(TextView labelView, boolean active) {
        if (labelView == null) {
            return;
        }
        labelView.setActivated(active);
        labelView.setSelected(active);
        labelView.setAlpha(active ? 1f : 0.55f);
    }

    private static final class ToggleBinding {

        private final CompoundButton button;
        private final TextView offLabel;
        private final TextView onLabel;

        private ToggleBinding(CompoundButton button, TextView offLabel, TextView onLabel) {
            this.button = button;
            this.offLabel = offLabel;
            this.onLabel = onLabel;
        }
    }

    private static final class SourceBinding {

        private final TextView statusView;
        private final TextView dataAgeView;
        private final LinearLayout listView;
        private final TextView emptyView;
        private final Map<String, TextView> warningDataAgeViews = new LinkedHashMap<>();
        private String lastListSignature;

        private SourceBinding(
                TextView statusView,
                TextView dataAgeView,
                LinearLayout listView,
                TextView emptyView) {
            this.statusView = statusView;
            this.dataAgeView = dataAgeView;
            this.listView = listView;
            this.emptyView = emptyView;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(String stableId);
    }

    public interface OnPullToRefreshListener {
        void onPullToRefresh();
    }
}
