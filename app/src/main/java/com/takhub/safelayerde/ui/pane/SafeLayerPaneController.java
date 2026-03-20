package com.takhub.safelayerde.ui.pane;

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.takhub.safelayerde.R;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.RelevancePolicy;
import com.takhub.safelayerde.domain.service.WarningRepository;
import com.takhub.safelayerde.plugin.SafeLayerConstants;
import com.takhub.safelayerde.ui.model.RadarStatusVm;
import com.takhub.safelayerde.ui.model.SourceStatusVm;
import com.takhub.safelayerde.ui.model.WarningDetailVm;
import com.takhub.safelayerde.ui.model.WarningListItemVm;
import com.takhub.safelayerde.util.TimeUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;

public class SafeLayerPaneController {

    static final long DATA_AGE_TICK_INTERVAL_MS = 60L * 1000L;

    private final WarningRepository warningRepository;
    private final Map<WarningSourceType, SourceSectionState> sourceSections =
            new EnumMap<>(WarningSourceType.class);
    private final DetailViewModelMapper mapper = new DetailViewModelMapper();
    private final RelevancePolicy relevancePolicy = new RelevancePolicy();
    private final PaneInteractionGlue interactionGlue = new PaneInteractionGlue();
    private final DataAgeTicker dataAgeTicker = new DataAgeTicker();
    private final PaneLifecycleBridge paneLifecycleBridge = new PaneLifecycleBridge();

    private Pane pane;
    private View paneView;
    private SafeLayerPaneBinder binder;
    private SafeLayerPaneState state = SafeLayerPaneState.list();
    private SourceState radarSourceState;
    private RadarFrame radarFrame;
    private boolean radarLayerVisible;
    private int radarTransparencyPercent = SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT;
    private boolean manualRefreshActive;
    private LayerToggleListener layerToggleListener;
    private RadarTransparencyListener radarTransparencyListener;
    private ExplicitListTapListener explicitListTapListener;
    private ManualRefreshRequestListener manualRefreshRequestListener;

    public SafeLayerPaneController(WarningRepository warningRepository) {
        this.warningRepository = warningRepository;
        initializeSourceSections();
    }

    public void showPane(Context pluginContext, IHostUIService uiService) {
        if (pluginContext == null || uiService == null) {
            return;
        }

        boolean reusingExistingPane = pane != null;
        ensurePane(pluginContext);
        if (pane == null) {
            return;
        }
        if (uiService.isPaneVisible(pane)) {
            onPaneVisibilityChanged(true);
            return;
        }
        if (reusingExistingPane) {
            resetOverviewForReopen();
        }
        paneLifecycleBridge.showPane(uiService, pane);
    }

    public void updateRadar(RadarFrame radarFrame, SourceState sourceState) {
        setRadarState(radarFrame, sourceState);
        renderCurrentState();
    }

    public void applyRuntimeState(
            List<WarningRecord> bbkRecords,
            SourceState bbkSourceState,
            List<WarningRecord> dwdRecords,
            SourceState dwdSourceState,
            RadarFrame radarFrame,
            SourceState radarSourceState,
            boolean bbkLayerVisible,
            boolean dwdLayerVisible,
            boolean radarLayerVisible,
            int radarTransparencyPercent) {
        updateSourceSection(WarningSourceType.BBK, bbkRecords, bbkSourceState);
        updateSourceSection(WarningSourceType.DWD, dwdRecords, dwdSourceState);
        setSourceLayerVisibleInternal(WarningSourceType.BBK, bbkLayerVisible);
        setSourceLayerVisibleInternal(WarningSourceType.DWD, dwdLayerVisible);
        setRadarLayerVisibleInternal(radarLayerVisible);
        setRadarTransparencyPercentInternal(radarTransparencyPercent);
        setRadarState(radarFrame, radarSourceState);
        renderCurrentState();
    }

    public void setLayerToggleListener(LayerToggleListener layerToggleListener) {
        this.layerToggleListener = layerToggleListener;
    }

    public void setExplicitListTapListener(ExplicitListTapListener explicitListTapListener) {
        this.explicitListTapListener = explicitListTapListener;
    }

    public void setRadarTransparencyListener(RadarTransparencyListener radarTransparencyListener) {
        this.radarTransparencyListener = radarTransparencyListener;
    }

    public void setManualRefreshRequestListener(ManualRefreshRequestListener manualRefreshRequestListener) {
        this.manualRefreshRequestListener = manualRefreshRequestListener;
    }

    public void setManualRefreshActive(boolean manualRefreshActive) {
        this.manualRefreshActive = manualRefreshActive;
        if (binder != null) {
            binder.bindManualRefreshActive(manualRefreshActive);
        }
    }

    public void setSourceLayerVisible(WarningSourceType sourceType, boolean visible) {
        if (!setSourceLayerVisibleInternal(sourceType, visible) || binder == null) {
            return;
        }
        bindLayerToggleState();
    }

    public void setRadarLayerVisible(boolean visible) {
        if (!setRadarLayerVisibleInternal(visible) || binder == null) {
            return;
        }
        bindLayerToggleState();
    }

    public void setRadarTransparencyPercent(int radarTransparencyPercent) {
        if (!setRadarTransparencyPercentInternal(radarTransparencyPercent) || binder == null) {
            return;
        }
        binder.bindRadarTransparency(
                toRadarVisibilityPercent(this.radarTransparencyPercent),
                interactionGlue.radarTransparencyListener());
    }

    static int toRadarVisibilityPercent(int transparencyPercent) {
        return 100 - Math.max(0, Math.min(100, transparencyPercent));
    }

    static int toRadarTransparencyPercent(int visibilityPercent) {
        return 100 - Math.max(0, Math.min(100, visibilityPercent));
    }

    public boolean isSourceLayerVisible(WarningSourceType sourceType) {
        SourceSectionState sourceSection = sourceSectionStateFor(sourceType);
        return sourceSection != null && sourceSection.layerVisible;
    }

    public void openDetail(String stableId) {
        if (stableId == null) {
            return;
        }
        navigateTo(state.asDetail(stableId));
    }

    public void showList() {
        navigateTo(state.asList());
    }

    public void showHelp() {
        navigateTo(state.asHelp());
    }

    public void stop() {
        onPaneVisibilityChanged(false);
        pane = null;
        paneView = null;
        binder = null;
        paneLifecycleBridge.reset();
        resetSourceSections();
        radarSourceState = null;
        radarFrame = null;
        radarLayerVisible = false;
        radarTransparencyPercent = SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT;
        manualRefreshActive = false;
        layerToggleListener = null;
        radarTransparencyListener = null;
        explicitListTapListener = null;
        manualRefreshRequestListener = null;
        state = SafeLayerPaneState.list();
    }

    public interface LayerToggleListener {
        void onBbkLayerVisibleChanged(boolean visible);
        void onDwdLayerVisibleChanged(boolean visible);
        void onRadarLayerVisibleChanged(boolean visible);
    }

    public interface RadarTransparencyListener {
        void onRadarTransparencyChanged(int transparencyPercent);
    }

    public interface ExplicitListTapListener {
        void onExplicitListTap(String stableId);
    }

    public interface ManualRefreshRequestListener {
        void onManualRefreshRequested();
    }

    private void ensurePane(Context pluginContext) {
        if (pane != null) {
            return;
        }
        paneView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
        binder = new SafeLayerPaneBinder(paneView);
        interactionGlue.bindPullToRefresh();
        interactionGlue.bindHelpButtons();
        pane = new PaneBuilder(paneView)
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.42D)
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.55D)
                .build();
        renderCurrentState();
    }

    private void navigateTo(SafeLayerPaneState nextState) {
        SafeLayerPaneState resolvedState = nextState == null ? SafeLayerPaneState.list() : nextState;
        if (resolvedState.equals(state)) {
            return;
        }
        state = resolvedState;
        renderCurrentState();
    }

    private void renderCurrentState() {
        if (!isReadyForRender()) {
            return;
        }

        long nowEpochMs = currentTimeEpochMs();
        bindCurrentState(nowEpochMs);
        applyScreenState(resolveRenderedScreen(nowEpochMs));
    }

    boolean isReadyForRender() {
        return binder != null && paneView != null;
    }

    void bindCurrentState() {
        bindCurrentState(currentTimeEpochMs());
    }

    void bindCurrentState(long nowEpochMs) {
        bindActiveTabState();
        bindSection(WarningSourceType.BBK, nowEpochMs);
        binder.bindManualRefreshActive(manualRefreshActive);
        bindLayerToggleState();
        bindSection(WarningSourceType.DWD, nowEpochMs);
        bindRadarStatus(mapper.toRadarStatus(getPaneContext(), radarFrame, radarSourceState, nowEpochMs));
    }

    void bindActiveTabState() {
        if (binder == null) {
            return;
        }
        binder.bindTabNavigation(
                state.getActiveTab(),
                interactionGlue.tabSelectionListener(SafeLayerPaneState.Tab.NINA),
                interactionGlue.tabSelectionListener(SafeLayerPaneState.Tab.DWD),
                interactionGlue.tabSelectionListener(SafeLayerPaneState.Tab.RADAR));
        binder.bindTabContent(state.getActiveTab());
    }

    void bindLayerToggleState() {
        if (binder == null) {
            return;
        }
        binder.bindLayerToggles(
                isSourceLayerVisible(WarningSourceType.BBK),
                isSourceLayerVisible(WarningSourceType.DWD),
                radarLayerVisible,
                interactionGlue.layerToggleListener(WarningSourceType.BBK),
                interactionGlue.layerToggleListener(WarningSourceType.DWD),
                interactionGlue.radarLayerToggleListener());
        binder.bindRadarTransparency(
                toRadarVisibilityPercent(radarTransparencyPercent),
                interactionGlue.radarTransparencyListener());
    }

    void setStateForTest(SafeLayerPaneState state) {
        this.state = state == null ? SafeLayerPaneState.list() : state;
    }

    SafeLayerPaneState getStateForTest() {
        return state;
    }

    boolean isRadarLayerVisible() {
        return radarLayerVisible;
    }

    void refreshDataAgeLabels() {
        refreshDataAgeLabels(currentTimeEpochMs());
    }

    void refreshDataAgeLabels(long nowEpochMs) {
        if (!isReadyForRender()) {
            return;
        }
        bindSection(WarningSourceType.BBK, nowEpochMs);
        bindSection(WarningSourceType.DWD, nowEpochMs);
        bindRadarStatus(mapper.toRadarStatus(getPaneContext(), radarFrame, radarSourceState, nowEpochMs));
        RenderedScreen screen = resolveRenderedScreen(nowEpochMs);
        if (screen.mode == SafeLayerPaneState.Mode.DETAIL && screen.detailViewModel != null) {
            bindDetailDataAge(screen.detailViewModel.getDataAgeLabel());
        }
    }

    private RenderedScreen resolveRenderedScreen(long nowEpochMs) {
        if (state.getMode() == SafeLayerPaneState.Mode.HELP) {
            return RenderedScreen.help();
        }
        if (state.getMode() == SafeLayerPaneState.Mode.DETAIL && state.getSelectedStableId() != null) {
            WarningRecord record = findWarningRecordByStableId(state.getSelectedStableId());
            if (record != null) {
                return RenderedScreen.detail(mapper.toDetail(getPaneContext(), record, nowEpochMs));
            }
            state = state.asList();
        }
        return RenderedScreen.list();
    }

    private void applyScreenState(RenderedScreen screen) {
        if (binder == null || screen == null) {
            return;
        }
        if (screen.mode == SafeLayerPaneState.Mode.DETAIL) {
            binder.showDetail(screen.detailViewModel, new Runnable() {
                @Override
                public void run() {
                    showList();
                }
            });
            return;
        }
        if (screen.mode == SafeLayerPaneState.Mode.HELP) {
            binder.showHelp(new Runnable() {
                @Override
                public void run() {
                    showList();
                }
            });
            return;
        }
        binder.showList();
    }

    private void bindSection(WarningSourceType sourceType, long nowEpochMs) {
        SourceSectionState sourceSection = sourceSectionStateFor(sourceType);
        if (sourceSection == null || binder == null) {
            return;
        }
        Context context = getPaneContext();
        List<WarningRecord> sortedRecords = relevancePolicy.sortByRelevance(sourceSection.records);
        List<WarningListItemVm> listItems = mapListItems(context, sortedRecords, nowEpochMs);
        bindSourceStatus(sourceType, mapper.toSourceStatus(context, sourceType, sourceSection.sourceState, nowEpochMs));
        binder.bindWarningList(sourceType, listItems, new SafeLayerPaneBinder.OnItemClickListener() {
            @Override
            public void onItemClick(String stableId) {
                onWarningSelected(stableId, true);
            }
        });
        binder.showNoData(sourceType, sourceSection.sourceState, listItems.size());
    }

    Context getPaneContext() {
        return paneView == null ? null : paneView.getContext();
    }

    WarningRecord findWarningRecordByStableId(String stableId) {
        return warningRepository == null ? null : warningRepository.findByStableId(stableId);
    }

    void bindSourceStatus(WarningSourceType sourceType, SourceStatusVm vm) {
        if (binder != null) {
            binder.bindSourceStatus(sourceType, vm);
        }
    }

    void bindRadarStatus(RadarStatusVm vm) {
        if (binder != null) {
            binder.bindRadarStatus(vm);
        }
    }

    void bindDetailDataAge(String dataAgeLabel) {
        if (binder != null) {
            binder.bindDetailDataAge(dataAgeLabel);
        }
    }

    long currentTimeEpochMs() {
        return TimeUtils.nowEpochMs();
    }

    private void setRadarState(RadarFrame radarFrame, SourceState sourceState) {
        this.radarFrame = radarFrame;
        this.radarSourceState = sourceState;
    }

    private boolean setSourceLayerVisibleInternal(WarningSourceType sourceType, boolean visible) {
        SourceSectionState sourceSection = sourceSectionStateFor(sourceType);
        if (sourceSection == null || sourceSection.layerVisible == visible) {
            return false;
        }
        sourceSection.layerVisible = visible;
        return true;
    }

    private boolean setRadarLayerVisibleInternal(boolean visible) {
        if (radarLayerVisible == visible) {
            return false;
        }
        radarLayerVisible = visible;
        return true;
    }

    private boolean setRadarTransparencyPercentInternal(int transparencyPercent) {
        int clamped = Math.max(0, Math.min(100, transparencyPercent));
        if (radarTransparencyPercent == clamped) {
            return false;
        }
        radarTransparencyPercent = clamped;
        return true;
    }

    private List<WarningRecord> copyRecords(List<WarningRecord> records) {
        return records == null ? new ArrayList<WarningRecord>() : new ArrayList<>(records);
    }

    private void initializeSourceSections() {
        sourceSections.put(WarningSourceType.BBK, new SourceSectionState());
        sourceSections.put(WarningSourceType.DWD, new SourceSectionState());
    }

    private void resetSourceSections() {
        for (SourceSectionState sourceSection : sourceSections.values()) {
            sourceSection.reset();
        }
    }

    private void updateSourceSection(
            WarningSourceType sourceType,
            List<WarningRecord> records,
            SourceState sourceState) {
        SourceSectionState sourceSection = sourceSectionStateFor(sourceType);
        if (sourceSection == null) {
            return;
        }
        sourceSection.records = copyRecords(records);
        sourceSection.sourceState = sourceState;
    }

    private SourceSectionState sourceSectionStateFor(WarningSourceType sourceType) {
        return sourceSections.get(sourceType);
    }

    private List<WarningListItemVm> mapListItems(Context context, List<WarningRecord> records, long nowEpochMs) {
        List<WarningListItemVm> listItems = new ArrayList<>();
        if (context == null || records == null) {
            return listItems;
        }
        for (WarningRecord record : records) {
            listItems.add(mapper.toListItem(context, record, nowEpochMs));
        }
        return listItems;
    }

    void onPaneVisibilityChanged(boolean visible) {
        dataAgeTicker.onPaneVisibilityChanged(visible);
    }

    void resetOverviewForReopen() {
        SafeLayerPaneState reopenedState = SafeLayerPaneState.list(SafeLayerPaneState.Tab.NINA);
        if (!reopenedState.equals(state)) {
            state = reopenedState;
        }
        renderCurrentState();
    }

    boolean canScheduleDataAgeTicker() {
        return paneView != null;
    }

    void cancelScheduledDataAgeTick(Runnable ticker) {
        if (paneView != null) {
            paneView.removeCallbacks(ticker);
        }
    }

    void postDelayedDataAgeTick(Runnable ticker, long delayMs) {
        if (paneView != null) {
            paneView.postDelayed(ticker, delayMs);
        }
    }

    void onWarningSelected(String stableId, boolean explicitListTap) {
        interactionGlue.onWarningSelected(stableId, explicitListTap);
    }

    void onTabSelected(SafeLayerPaneState.Tab tab) {
        interactionGlue.onTabSelected(tab);
    }

    void onPullToRefreshTriggered() {
        interactionGlue.onPullToRefreshTriggered();
    }

    private final class PaneInteractionGlue {

        private void bindPullToRefresh() {
            if (binder == null || paneView == null) {
                return;
            }
            binder.bindPullToRefresh(
                    paneView.getResources().getDimensionPixelSize(R.dimen.safelayer_pull_to_refresh_threshold),
                    new SafeLayerPaneBinder.OnPullToRefreshListener() {
                        @Override
                        public void onPullToRefresh() {
                            onPullToRefreshTriggered();
                        }
                    });
        }

        private void bindHelpButtons() {
            if (binder == null) {
                return;
            }
            binder.bindInfoButtons(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showHelp();
                }
            });
        }

        private View.OnClickListener tabSelectionListener(final SafeLayerPaneState.Tab tab) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onTabSelected(tab);
                }
            };
        }

        private CompoundButton.OnCheckedChangeListener layerToggleListener(final WarningSourceType sourceType) {
            return new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!setSourceLayerVisibleInternal(sourceType, isChecked) || layerToggleListener == null) {
                        return;
                    }
                    if (sourceType == WarningSourceType.BBK) {
                        layerToggleListener.onBbkLayerVisibleChanged(isChecked);
                    } else if (sourceType == WarningSourceType.DWD) {
                        layerToggleListener.onDwdLayerVisibleChanged(isChecked);
                    }
                }
            };
        }

        private CompoundButton.OnCheckedChangeListener radarLayerToggleListener() {
            return new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!setRadarLayerVisibleInternal(isChecked) || layerToggleListener == null) {
                        return;
                    }
                    layerToggleListener.onRadarLayerVisibleChanged(isChecked);
                }
            };
        }

        private SeekBar.OnSeekBarChangeListener radarTransparencyListener() {
            return new SeekBar.OnSeekBarChangeListener() {
                private int pendingTransparencyPercent = radarTransparencyPercent;
                private int dispatchedTransparencyPercent = radarTransparencyPercent;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int visibilityPercent = Math.max(0, Math.min(100, progress));
                    pendingTransparencyPercent = toRadarTransparencyPercent(visibilityPercent);
                    radarTransparencyPercent = pendingTransparencyPercent;
                    if (binder != null) {
                        binder.bindRadarTransparencyValue(visibilityPercent);
                    }
                    if (!fromUser) {
                        dispatchTransparencyChangeIfNeeded();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    dispatchTransparencyChangeIfNeeded();
                }

                private void dispatchTransparencyChangeIfNeeded() {
                    if (pendingTransparencyPercent == dispatchedTransparencyPercent
                            || radarTransparencyListener == null) {
                        return;
                    }
                    dispatchedTransparencyPercent = pendingTransparencyPercent;
                    radarTransparencyListener.onRadarTransparencyChanged(pendingTransparencyPercent);
                }
            };
        }

        private void onWarningSelected(String stableId, boolean explicitListTap) {
            if (stableId == null) {
                return;
            }
            if (explicitListTap && explicitListTapListener != null) {
                explicitListTapListener.onExplicitListTap(stableId);
                return;
            }
            openDetail(stableId);
        }

        private void onTabSelected(SafeLayerPaneState.Tab tab) {
            if (tab == null) {
                return;
            }
            navigateTo(state.withActiveTab(tab));
        }

        private void onPullToRefreshTriggered() {
            if (manualRefreshActive || manualRefreshRequestListener == null) {
                return;
            }
            manualRefreshRequestListener.onManualRefreshRequested();
        }
    }

    private final class DataAgeTicker {

        private final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (!paneVisible) {
                    return;
                }
                refreshDataAgeLabels();
                scheduleNext();
            }
        };
        private boolean paneVisible;

        private void onPaneVisibilityChanged(boolean visible) {
            if (paneVisible == visible) {
                return;
            }
            paneVisible = visible;
            if (!paneVisible) {
                cancelScheduledDataAgeTick(ticker);
                return;
            }
            refreshDataAgeLabels();
            scheduleNext();
        }

        private void scheduleNext() {
            if (!paneVisible || !canScheduleDataAgeTicker()) {
                return;
            }
            cancelScheduledDataAgeTick(ticker);
            postDelayedDataAgeTick(ticker, DATA_AGE_TICK_INTERVAL_MS);
        }
    }

    private final class PaneLifecycleBridge {

        private static final String LISTENER_CLASS_NAME =
                "gov.tak.api.ui.IHostUIService$IPaneLifecycleListener";
        private static volatile Class<?> paneLifecycleListenerClass;
        private static volatile Method showPaneWithListenerMethod;
        private static volatile Class<?> resolvedUiServiceClass;
        private static volatile boolean compatibilityResolved;

        private Object paneLifecycleListener;

        private void showPane(IHostUIService uiService, Pane pane) {
            if (uiService == null || pane == null) {
                return;
            }
            if (!supportsLifecycleListener(uiService)) {
                uiService.showPane(pane, null);
                onPaneVisibilityChanged(true);
                return;
            }
            try {
                Method showPaneMethod = showPaneWithListenerMethod;
                Object lifecycleListener = getOrCreatePaneLifecycleListener();
                if (showPaneMethod == null || lifecycleListener == null) {
                    uiService.showPane(pane, null);
                    onPaneVisibilityChanged(true);
                    return;
                }
                showPaneMethod.invoke(uiService, pane, lifecycleListener);
            } catch (Exception ignored) {
                uiService.showPane(pane, null);
                onPaneVisibilityChanged(true);
            }
        }

        private void reset() {
            paneLifecycleListener = null;
        }

        private boolean supportsLifecycleListener(IHostUIService uiService) {
            Class<?> uiServiceClass = uiService.getClass();
            if (!compatibilityResolved || resolvedUiServiceClass != uiServiceClass) {
                resolveCompatibility(uiServiceClass);
            }
            return paneLifecycleListenerClass != null && showPaneWithListenerMethod != null;
        }

        private void resolveCompatibility(Class<?> uiServiceClass) {
            resolvedUiServiceClass = uiServiceClass;
            paneLifecycleListenerClass = null;
            showPaneWithListenerMethod = null;
            compatibilityResolved = true;
            try {
                Class<?> listenerClass = Class.forName(LISTENER_CLASS_NAME);
                Method showPaneMethod = uiServiceClass.getMethod("showPane", Pane.class, listenerClass);
                paneLifecycleListenerClass = listenerClass;
                showPaneWithListenerMethod = showPaneMethod;
            } catch (Exception ignored) {
                paneLifecycleListenerClass = null;
                showPaneWithListenerMethod = null;
            }
        }

        private Object getOrCreatePaneLifecycleListener() {
            if (paneLifecycleListener != null || paneLifecycleListenerClass == null) {
                return paneLifecycleListener;
            }
            paneLifecycleListener = Proxy.newProxyInstance(
                    paneLifecycleListenerClass.getClassLoader(),
                    new Class<?>[] {paneLifecycleListenerClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String methodName = method == null ? null : method.getName();
                            if ("onPaneVisible".equals(methodName)
                                    && args != null
                                    && args.length == 1
                                    && args[0] instanceof Boolean) {
                                onPaneVisibilityChanged(((Boolean) args[0]).booleanValue());
                            } else if ("onPaneClose".equals(methodName)) {
                                onPaneVisibilityChanged(false);
                            }
                            return null;
                        }
                    });
            return paneLifecycleListener;
        }
    }

    private static final class RenderedScreen {

        private final SafeLayerPaneState.Mode mode;
        private final WarningDetailVm detailViewModel;

        private RenderedScreen(SafeLayerPaneState.Mode mode, WarningDetailVm detailViewModel) {
            this.mode = mode;
            this.detailViewModel = detailViewModel;
        }

        private static RenderedScreen list() {
            return new RenderedScreen(SafeLayerPaneState.Mode.LIST, null);
        }

        private static RenderedScreen help() {
            return new RenderedScreen(SafeLayerPaneState.Mode.HELP, null);
        }

        private static RenderedScreen detail(WarningDetailVm detailViewModel) {
            return new RenderedScreen(SafeLayerPaneState.Mode.DETAIL, detailViewModel);
        }
    }

    private static final class SourceSectionState {

        private List<WarningRecord> records = new ArrayList<>();
        private SourceState sourceState;
        private boolean layerVisible = true;

        private void reset() {
            records = new ArrayList<>();
            sourceState = null;
            layerVisible = true;
        }
    }
}
