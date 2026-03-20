package com.takhub.safelayerde.plugin;

import android.content.Context;
import android.util.Log;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;
import com.takhub.safelayerde.cache.CachePaths;
import com.takhub.safelayerde.cache.CacheStore;
import com.takhub.safelayerde.cache.JsonCacheStore;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.LayerVisibilityState;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.SeverityColorPolicy;
import com.takhub.safelayerde.domain.service.DataAgeService;
import com.takhub.safelayerde.domain.service.RadarRepository;
import com.takhub.safelayerde.domain.service.RefreshCoordinator;
import com.takhub.safelayerde.domain.service.WarningDiffService;
import com.takhub.safelayerde.domain.service.WarningRepository;
import com.takhub.safelayerde.platform.MapViewProvider;
import com.takhub.safelayerde.platform.UiThreadRunner;
import com.takhub.safelayerde.render.map.MapOverlayController;
import com.takhub.safelayerde.render.map.RadarLayerFactory;
import com.takhub.safelayerde.render.map.RadarRenderController;
import com.takhub.safelayerde.render.map.RadarRenderException;
import com.takhub.safelayerde.render.map.RenderRegistry;
import com.takhub.safelayerde.render.map.WarningMarkerFactory;
import com.takhub.safelayerde.render.map.WarningRenderController;
import com.takhub.safelayerde.render.map.WarningShapeFactory;
import com.takhub.safelayerde.render.model.RadarRenderSpec;
import com.takhub.safelayerde.render.model.WarningRenderSpec;
import com.takhub.safelayerde.settings.PluginSettings;
import com.takhub.safelayerde.settings.SettingsRepository;
import com.takhub.safelayerde.source.bbk.BbkApiClient;
import com.takhub.safelayerde.source.bbk.BbkGeoJsonParser;
import com.takhub.safelayerde.source.bbk.BbkNormalizer;
import com.takhub.safelayerde.source.bbk.BbkWarningSource;
import com.takhub.safelayerde.source.common.HttpClient;
import com.takhub.safelayerde.source.common.SourceClock;
import com.takhub.safelayerde.source.common.SourceRefreshFinalizer;
import com.takhub.safelayerde.source.common.SourceRefreshResult;
import com.takhub.safelayerde.source.dwd.DwdCapNormalizer;
import com.takhub.safelayerde.source.dwd.DwdCapParser;
import com.takhub.safelayerde.source.dwd.DwdCapWarningSource;
import com.takhub.safelayerde.source.dwd.DwdCapZipFetcher;
import com.takhub.safelayerde.source.radar.DwdRadarDecoder;
import com.takhub.safelayerde.source.radar.DwdRadarFetcher;
import com.takhub.safelayerde.source.radar.DwdRadarProduct;
import com.takhub.safelayerde.source.radar.DwdRadarSource;
import com.takhub.safelayerde.ui.actions.UiActionRouter;
import com.takhub.safelayerde.ui.pane.SafeLayerPaneController;
import com.takhub.safelayerde.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gov.tak.api.ui.IHostUIService;

public class SafeLayerRuntime {

    private static final String TAG = "SafeLayerRuntime";
    private static final long MAP_BIND_RETRY_DELAY_MS = 5000L;
    private static final long NO_ACTIVE_SESSION = -1L;

    private final Map<WarningSourceType, WarningSourceRuntimeState> warningStates = createWarningStates();
    private final SourceRefreshFinalizer warningStateRestoreFinalizer =
            new SourceRefreshFinalizer(null, new DataAgeService());
    private final RadarRuntime radarRuntime = new RadarRuntime();
    private final WarningFocusController.WarningRecordResolver warningRecordResolver =
            new WarningFocusController.WarningRecordResolver() {
                @Override
                public WarningRecord findRecord(String stableId) {
                    return warningRepository == null ? null : warningRepository.findByStableId(stableId);
                }
            };
    private final WarningFocusController.FocusExecutor warningFocusExecutor =
            new WarningFocusRuntimeAdapter();
    private final WarningFocusController focusController = new WarningFocusController();

    private SafeLayerPaneController paneController;
    private RefreshCoordinator refreshCoordinator;
    private MapOverlayController mapOverlayController;
    private CacheStore cacheStore;
    private WarningRepository warningRepository;
    private RadarRepository radarRepository;
    private SettingsRepository settingsRepository;
    private Context pluginContext;
    private IHostUIService uiService;
    private MapViewProvider mapViewProvider;
    private UiThreadRunner uiThreadRunner;
    private boolean mapBindRetryScheduled;
    private Runnable mapBindRetryRunnable;
    private long activeSessionId = NO_ACTIVE_SESSION;
    private long nextSessionId = 1L;
    private final RadarLayerFactory.RendererFailureListener radarRendererFailureListener =
            new RadarLayerFactory.RendererFailureListener() {
                @Override
                public void onFailure(String message, Throwable throwable) {
                    radarRuntime.onRendererFailure("gl-thread", message, throwable);
                    refreshPaneStateAfterRadarFailure();
                }
            };

    public synchronized void start(
            Context pluginContext,
            IHostUIService uiService,
            MapViewProvider mapViewProvider) {
        if (isRuntimeActive()) {
            return;
        }

        long sessionId = NO_ACTIVE_SESSION;
        try {
            this.pluginContext = pluginContext;
            this.uiService = uiService;
            this.mapViewProvider = mapViewProvider;
            this.uiThreadRunner = new UiThreadRunner();

            bootstrapRuntime(pluginContext);
            SafeLayerDebugLog.i(TAG, "runtime-start cacheStore=" + (cacheStore != null)
                    + ", pluginContext=" + describeContext(pluginContext)
                    + ", uiService=" + (uiService != null));

            initializeUiControllers(pluginContext, uiService);
            WarningSources warningSources = prepareWarningSources(pluginContext);
            restoreCachedState(warningSources.bbkSource, warningSources.dwdSource);
            sessionId = activateSession();
            registerRefreshLifecycle(sessionId, warningSources.bbkSource, warningSources.dwdSource);
            postUiUpdate(sessionId);
            requestStartupRefresh();
        } catch (RuntimeException exception) {
            rollbackFailedStart();
            throw exception;
        } catch (Error error) {
            rollbackFailedStart();
            throw error;
        }
    }

    public synchronized void stop() {
        if (!hasSession()) {
            return;
        }

        SafeLayerDebugLog.i(TAG, "runtime-stop");
        shutdownRuntimeState();
    }

    public void onToolbarClick() {
        if (!isRuntimeActive() || paneController == null) {
            return;
        }
        paneController.showPane(pluginContext, uiService);
    }

    UiActionRouter wireUiActions(SafeLayerPaneController paneController) {
        return wireUiActions(paneController, null, null);
    }

    UiActionRouter wireUiActions(
            SafeLayerPaneController paneController,
            Context pluginContext,
            IHostUIService uiService) {
        final UiActionRouter actionRouter = new UiActionRouter(
                paneController,
                pluginContext,
                uiService,
                new UiActionRouter.DetailFocusListener() {
                    @Override
                    public void onDetailFocusRequested(String stableId) {
                        requestWarningFocus(stableId);
                    }
                });
        if (paneController != null) {
            paneController.setExplicitListTapListener(new SafeLayerPaneController.ExplicitListTapListener() {
                @Override
                public void onExplicitListTap(String stableId) {
                    actionRouter.openDetailAndFocus(stableId);
                }
            });
        }
        return actionRouter;
    }

    void requestWarningFocus(final String stableId) {
        if (!isRuntimeActive()) {
            return;
        }

        final long sessionId = activeSessionId;
        focusController.request(stableId, uiThreadRunner, new Runnable() {
            @Override
            public void run() {
                if (isActiveSession(sessionId)) {
                    MapBindingOutcome bindingOutcome = ensureMapBinding();
                    if (bindingOutcome.isBound()) {
                        applyWarningRenderState();
                    }
                    replayPendingWarningFocus(bindingOutcome);
                }
            }
        });
    }

    void applyUiState() {
        if (!isRuntimeActive()) {
            return;
        }

        MapBindingOutcome bindingOutcome = ensureMapBinding();
        SourceState paneRadarState = applyRenderState(bindingOutcome);
        applyPaneState(paneRadarState);
        replayPendingWarningFocus(bindingOutcome);
        SafeLayerDebugLog.i(TAG, "ui-state mapBound=" + bindingOutcome.isBound()
                + ", bbkVisible=" + warningState(WarningSourceType.BBK).isLayerVisible()
                + ", dwdVisible=" + warningState(WarningSourceType.DWD).isLayerVisible()
                + ", radarVisible=" + radarRuntime.isLayerVisible()
                + ", pendingFocus=" + StringUtils.trimToNull(focusController.pendingStableId())
                + ", bbkRecords=" + warningState(WarningSourceType.BBK).records(warningRepository).size()
                + ", dwdRecords=" + warningState(WarningSourceType.DWD).records(warningRepository).size()
                + ", bbkState=" + describeState(warningState(WarningSourceType.BBK).sourceState())
                + ", dwdState=" + describeState(warningState(WarningSourceType.DWD).sourceState())
                + ", radarState=" + describeState(paneRadarState));
    }

    DwdCapWarningSource createDwdWarningSource(HttpClient httpClient, CacheStore cacheStore) {
        return new DwdCapWarningSource(
                new DwdCapZipFetcher(httpClient),
                new DwdCapParser(),
                new DwdCapNormalizer(),
                cacheStore,
                new SourceClock(),
                SafeLayerConstants.DWD_CAP_COMMUNEUNION_LATEST_DIRECTORY_URL);
    }

    BbkWarningSource createBbkWarningSource(
            HttpClient httpClient,
            CacheStore cacheStore,
            final OperationalAreaArsResolver arsResolver) {
        return new BbkWarningSource(
                new BbkApiClient(httpClient, SafeLayerConstants.BBK_BASE_URL),
                new BbkNormalizer(),
                new BbkGeoJsonParser(),
                new WarningDiffService(),
                cacheStore,
                new SourceClock(),
                new BbkWarningSource.ArsSetProvider() {
                    @Override
                    public Set<String> resolve() {
                        return arsResolver.resolve();
                    }
                });
    }

    DwdRadarSource createRadarSource(HttpClient httpClient, CacheStore cacheStore) {
        return new DwdRadarSource(
                new DwdRadarFetcher(httpClient),
                new DwdRadarDecoder(),
                cacheStore,
                new SourceClock(),
                DwdRadarProduct.RV);
    }

    void setWarningRepositoryForTest(WarningRepository warningRepository) {
        this.warningRepository = warningRepository;
    }

    void setPaneControllerForTest(SafeLayerPaneController paneController) {
        this.paneController = paneController;
    }

    void setRefreshCoordinatorForTest(RefreshCoordinator refreshCoordinator) {
        this.refreshCoordinator = refreshCoordinator;
    }

    void setRadarRepositoryForTest(RadarRepository radarRepository) {
        this.radarRepository = radarRepository;
    }

    void setRadarRenderControllerForTest(RadarRenderController radarRenderController) {
        radarRuntime.setRenderControllerForTest(radarRenderController);
    }

    void setRadarSourceForTest(DwdRadarSource radarSource) {
        radarRuntime.setSourceForTest(radarSource);
    }

    void setMapOverlayControllerForTest(MapOverlayController mapOverlayController) {
        this.mapOverlayController = mapOverlayController;
    }

    void setRadarLayerVisibleForTest(boolean visible) {
        radarRuntime.setLayerVisibleForTest(visible);
    }

    SafeLayerPaneController.LayerToggleListener createLayerToggleListener() {
        return new SafeLayerPaneController.LayerToggleListener() {
            @Override
            public void onBbkLayerVisibleChanged(boolean visible) {
                handleWarningLayerVisibleChanged(WarningSourceType.BBK, visible);
            }

            @Override
            public void onDwdLayerVisibleChanged(boolean visible) {
                handleWarningLayerVisibleChanged(WarningSourceType.DWD, visible);
            }

            @Override
            public void onRadarLayerVisibleChanged(boolean visible) {
                SafeLayerRuntime.this.onRadarLayerVisibleChanged(visible);
            }
        };
    }

    void setSourceLayerVisibleForTest(WarningSourceType sourceType, boolean visible) {
        setSourceLayerVisible(sourceType, visible);
    }

    boolean requestManualRefresh() {
        if (!isRuntimeActive() || refreshCoordinator == null) {
            return false;
        }
        boolean accepted = refreshCoordinator.requestImmediateRefresh();
        if (accepted) {
            updateManualRefreshIndicator(true);
        }
        return accepted;
    }

    void handleRefreshCycleFinished(
            RefreshCoordinator.RefreshTrigger trigger,
            boolean manualRefreshChainActive) {
        if (!isRuntimeActive()) {
            return;
        }
        if (trigger == RefreshCoordinator.RefreshTrigger.MANUAL && !manualRefreshChainActive) {
            updateManualRefreshIndicator(false);
        }
    }

    boolean focusWarningOnMap(String stableId) {
        if (!isRuntimeActive()) {
            return false;
        }
        return focusController.focus(stableId, warningRecordResolver, warningFocusExecutor)
                == WarningFocusController.FocusResult.APPLIED;
    }

    void replayPendingWarningFocus() {
        replayPendingWarningFocus(ensureMapBinding());
    }

    boolean hasRadarRenderController() {
        return radarRuntime.hasRenderController();
    }

    void ensureRadarReady(String stage) {
        radarRuntime.ensureRenderReady(stage, ensureMapBinding().bindingResult());
    }

    void onRadarLayerVisibleChanged(boolean visible) {
        if (!isRuntimeActive()) {
            return;
        }
        radarRuntime.onLayerVisibleChanged(visible);
    }

    boolean registerRadarSourceIfNeeded() {
        return radarRuntime.registerSourceIfNeeded();
    }

    DwdRadarSource ensureRadarSourceInitialized() {
        return radarRuntime.ensureSourceInitialized();
    }

    boolean canPanToWarning(MapView mapView) {
        return mapView != null && mapView.getRenderer3() != null;
    }

    void panToWarning(MapView mapView, WarningRenderSpec spec) {
        if (mapView == null || spec == null) {
            return;
        }
        CameraController.Programmatic.panTo(
                mapView.getRenderer3(),
                new GeoPoint(spec.getCentroidLat(), spec.getCentroidLon()),
                false);
    }

    private void bootstrapRuntime(Context pluginContext) {
        resetWarningStates();
        focusController.reset();
        warningRepository = new WarningRepository();
        radarRepository = new RadarRepository();
        paneController = new SafeLayerPaneController(warningRepository);
        refreshCoordinator = new RefreshCoordinator();
        cacheStore = createCacheStore(pluginContext);
        settingsRepository = new SettingsRepository(resolvePreferredStorageContext(pluginContext));
        radarRuntime.resetForStart();
        applySettings(settingsRepository.read());
    }

    private void initializeUiControllers(Context pluginContext, IHostUIService uiService) {
        UiActionRouter actionRouter = wireUiActions(paneController, pluginContext, uiService);
        mapOverlayController = new MapOverlayController(actionRouter);
        wirePaneActions();
    }

    private WarningSources prepareWarningSources(Context pluginContext) {
        Context storageContext = resolvePreferredStorageContext(pluginContext);
        OperationalAreaArsResolver arsResolver = new OperationalAreaArsResolver(
                mapViewProvider,
                storageContext);
        HttpClient httpClient = new HttpClient();
        BbkWarningSource bbkSource = createBbkWarningSource(httpClient, cacheStore, arsResolver);
        DwdCapWarningSource dwdSource = createDwdWarningSource(httpClient, cacheStore);
        radarRuntime.ensureSourceInitialized();
        return new WarningSources(bbkSource, dwdSource);
    }

    private void restoreCachedState(BbkWarningSource bbkSource, DwdCapWarningSource dwdSource) {
        long nowEpochMs = nowEpochMs();
        WarningSnapshot cachedBbkSnapshot = bbkSource.loadFromCache();
        if (cachedBbkSnapshot != null) {
            warningState(WarningSourceType.BBK).updateRepository(warningRepository, cachedBbkSnapshot);
        }

        WarningSnapshot cachedDwdSnapshot = dwdSource.loadFromCache();
        if (cachedDwdSnapshot != null) {
            warningState(WarningSourceType.DWD).updateRepository(warningRepository, cachedDwdSnapshot);
        }

        warningState(WarningSourceType.BBK).setSourceState(restoreCachedWarningState(
                SourceIdentity.BBK,
                readCachedSourceState(SourceIdentity.BBK),
                cachedBbkSnapshot,
                nowEpochMs));
        warningState(WarningSourceType.DWD).setSourceState(restoreCachedWarningState(
                SourceIdentity.DWD,
                readCachedSourceState(SourceIdentity.DWD),
                cachedDwdSnapshot,
                nowEpochMs));
        radarRuntime.restoreCacheIfEnabled("start");
    }

    private void registerRefreshLifecycle(
            long sessionId,
            BbkWarningSource bbkSource,
            DwdCapWarningSource dwdSource) {
        if (refreshCoordinator == null) {
            return;
        }

        refreshCoordinator.registerSource(
                SourceIdentity.BBK,
                bbkSource,
                SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS);
        refreshCoordinator.registerSource(
                SourceIdentity.DWD,
                dwdSource,
                SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS);
        radarRuntime.registerSourceIfNeeded();
        refreshCoordinator.setListener(createRefreshListener(sessionId));
        refreshCoordinator.start();
    }

    private RefreshCoordinator.RefreshListener createRefreshListener(final long sessionId) {
        return new RefreshCoordinator.RefreshListener() {
            @Override
            public void onRefreshResult(SourceRefreshResult result) {
                if (!isActiveSession(sessionId)) {
                    return;
                }
                if (result != null) {
                    SafeLayerDebugLog.i(TAG, "refresh-applied source="
                            + (result.getSourceState() == null
                            ? "unknown"
                            : result.getSourceState().getSourceIdentity())
                            + ", success=" + result.isSuccess()
                            + ", snapshotRecords=" + snapshotSize(result.getSnapshot())
                            + ", state=" + (result.getSourceState() == null
                            ? "null"
                            : result.getSourceState().getStatus())
                            + ", error=" + StringUtils.trimToNull(result.getErrorMessage()));
                    updateRepository(result);
                }
                postUiUpdate(sessionId);
            }

            @Override
            public void onRefreshCycleFinished(
                    RefreshCoordinator.RefreshTrigger trigger,
                    boolean manualRefreshChainActive) {
                if (!isActiveSession(sessionId)) {
                    return;
                }
                handleRefreshCycleFinished(trigger, manualRefreshChainActive);
            }
        };
    }

    private void detachRefreshLifecycle() {
        if (refreshCoordinator == null) {
            return;
        }
        refreshCoordinator.setListener(null);
        refreshCoordinator.stop();
    }

    private static Map<WarningSourceType, WarningSourceRuntimeState> createWarningStates() {
        Map<WarningSourceType, WarningSourceRuntimeState> states =
                new EnumMap<>(WarningSourceType.class);
        states.put(WarningSourceType.BBK, new WarningSourceRuntimeState(WarningSourceType.BBK));
        states.put(WarningSourceType.DWD, new WarningSourceRuntimeState(WarningSourceType.DWD));
        return states;
    }

    private WarningSourceRuntimeState warningState(WarningSourceType sourceType) {
        return warningStates.get(sourceType);
    }

    private WarningSourceRuntimeState warningState(SourceIdentity sourceIdentity) {
        for (WarningSourceRuntimeState warningState : warningStates.values()) {
            if (warningState.sourceIdentity() == sourceIdentity) {
                return warningState;
            }
        }
        return null;
    }

    private void resetWarningStates() {
        for (WarningSourceRuntimeState warningState : warningStates.values()) {
            warningState.reset();
        }
    }

    private void wirePaneActions() {
        if (paneController == null) {
            return;
        }
        paneController.setLayerToggleListener(createLayerToggleListener());
        paneController.setRadarTransparencyListener(createRadarTransparencyListener());
        paneController.setManualRefreshRequestListener(createManualRefreshRequestListener());
    }

    private SafeLayerPaneController.RadarTransparencyListener createRadarTransparencyListener() {
        return new SafeLayerPaneController.RadarTransparencyListener() {
            @Override
            public void onRadarTransparencyChanged(int transparencyPercent) {
                if (!isRuntimeActive()) {
                    return;
                }
                radarRuntime.onTransparencyChanged(transparencyPercent);
            }
        };
    }

    private SafeLayerPaneController.ManualRefreshRequestListener createManualRefreshRequestListener() {
        return new SafeLayerPaneController.ManualRefreshRequestListener() {
            @Override
            public void onManualRefreshRequested() {
                requestManualRefresh();
            }
        };
    }

    private CacheStore createCacheStore(Context pluginContext) {
        CacheLocation cacheLocation = resolveCacheLocation(pluginContext);
        if (cacheLocation == null) {
            return null;
        }

        return new JsonCacheStore(new CachePaths(
                cacheLocation.primaryBaseDirectory,
                cacheLocation.legacyBaseDirectories));
    }

    private CacheLocation resolveCacheLocation(Context pluginContext) {
        List<File> baseDirectories = collectCacheBaseDirectories(pluginContext);
        if (baseDirectories.isEmpty()) {
            return null;
        }

        File primaryBaseDirectory = baseDirectories.get(0);
        List<File> legacyBaseDirectories = new ArrayList<>();
        for (int index = 1; index < baseDirectories.size(); index++) {
            legacyBaseDirectories.add(baseDirectories.get(index));
        }
        return new CacheLocation(primaryBaseDirectory, legacyBaseDirectories);
    }

    private List<File> collectCacheBaseDirectories(Context pluginContext) {
        List<File> baseDirectories = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        Context preferredStorageContext = resolvePreferredStorageContext(pluginContext);
        MapView mapView = currentMapView();
        Context mapContext = mapView == null ? null : mapView.getContext();

        addCacheBaseDirectory(baseDirectories, seenPaths, preferredStorageContext);
        addCacheBaseDirectory(baseDirectories, seenPaths, mapContext == null ? null : mapContext.getApplicationContext());
        addCacheBaseDirectory(baseDirectories, seenPaths, mapContext);
        addCacheBaseDirectory(baseDirectories, seenPaths, pluginContext == null ? null : pluginContext.getApplicationContext());
        addCacheBaseDirectory(baseDirectories, seenPaths, pluginContext);

        return baseDirectories;
    }

    private void addCacheBaseDirectory(List<File> baseDirectories, Set<String> seenPaths, Context context) {
        File baseDirectory = extractCacheBaseDirectory(context);
        if (baseDirectory == null) {
            return;
        }

        String absolutePath = baseDirectory.getAbsolutePath();
        if (seenPaths.add(absolutePath)) {
            baseDirectories.add(baseDirectory);
        }
    }

    private File extractCacheBaseDirectory(Context context) {
        if (context == null) {
            return null;
        }

        File filesDirectory = context.getFilesDir();
        if (filesDirectory != null) {
            return filesDirectory;
        }

        File cacheDirectory = context.getCacheDir();
        if (cacheDirectory != null) {
            return cacheDirectory;
        }

        return context.getExternalFilesDir(null);
    }

    private Context resolvePreferredStorageContext(Context pluginContext) {
        MapView mapView = currentMapView();
        Context mapContext = mapView == null ? null : mapView.getContext();
        return StorageContextResolver.resolve(pluginContext, mapContext);
    }

    private boolean isHostStorageContext(Context context) {
        return context != null
                && context.getPackageName() != null
                && !SafeLayerConstants.PLUGIN_PACKAGE.equals(context.getPackageName());
    }

    long nowEpochMs() {
        return new SourceClock().nowMs();
    }

    private SourceState restoreCachedWarningState(
            SourceIdentity sourceIdentity,
            SourceState persistedState,
            WarningSnapshot restoredSnapshot,
            long nowEpochMs) {
        return warningStateRestoreFinalizer.restoreWarningState(
                sourceIdentity,
                persistedState,
                restoredSnapshot,
                nowEpochMs);
    }

    private SourceState readCachedSourceState(SourceIdentity sourceIdentity) {
        if (cacheStore == null) {
            return fallbackSourceState(sourceIdentity);
        }

        List<SourceState> states = cacheStore.readSourceState();
        for (SourceState state : states) {
            if (state != null && state.getSourceIdentity() == sourceIdentity) {
                return state;
            }
        }
        return fallbackSourceState(sourceIdentity);
    }

    private SourceState fallbackSourceState(SourceIdentity sourceIdentity) {
        SourceState state = SourceState.forSource(sourceIdentity);
        long lastSuccessEpochMs = lastSuccessEpochMsFromCache(sourceIdentity);
        if (lastSuccessEpochMs > 0L) {
            state.setStatus(SourceState.Status.STALE);
            state.setLastSuccessEpochMs(lastSuccessEpochMs);
        } else if (sourceIdentity == SourceIdentity.RADAR) {
            state.setStatus(SourceState.Status.DISABLED);
        } else {
            state.setStatus(SourceState.Status.ERROR_NO_CACHE);
        }
        return state;
    }

    private SourceState inactiveRadarState() {
        SourceState state = SourceState.forSource(SourceIdentity.RADAR);
        state.setStatus(SourceState.Status.DISABLED);
        return state;
    }

    private SourceState radarFailureState(String message) {
        SourceState state = SourceState.forSource(SourceIdentity.RADAR);
        RadarFrame currentFrame = radarRepository == null ? null : radarRepository.getCurrentFrame();
        long lastSuccessEpochMs = currentFrame != null
                ? currentFrame.getFrameEpochMs()
                : Math.max(0L, radarRuntime.sourceStateLastSuccessEpochMs());
        state.setLastSuccessEpochMs(lastSuccessEpochMs);
        state.setLastErrorMessage(message);
        state.setStatus(lastSuccessEpochMs > 0L
                ? SourceState.Status.ERROR_WITH_CACHE
                : SourceState.Status.ERROR_NO_CACHE);
        return state;
    }

    private SourceState radarRendererFailureState(String message) {
        SourceState state = SourceState.forSource(SourceIdentity.RADAR);
        RadarFrame currentFrame = radarRepository == null ? null : radarRepository.getCurrentFrame();
        long lastSuccessEpochMs = currentFrame != null
                ? currentFrame.getFrameEpochMs()
                : Math.max(0L, radarRuntime.sourceStateLastSuccessEpochMs());
        state.setLastSuccessEpochMs(lastSuccessEpochMs);
        state.setLastErrorMessage(message);
        state.setStatus(lastSuccessEpochMs > 0L
                ? SourceState.Status.DEGRADED_WITH_DATA
                : SourceState.Status.ERROR_NO_CACHE);
        return state;
    }

    private long lastSuccessEpochMsFromCache(SourceIdentity sourceIdentity) {
        WarningSourceRuntimeState warningState = warningState(sourceIdentity);
        if (warningState != null) {
            return warningState.lastSuccessEpochMsFromCache(warningRepository);
        }
        RadarFrame currentFrame = radarRepository == null ? null : radarRepository.getCurrentFrame();
        return currentFrame == null ? 0L : currentFrame.getFrameEpochMs();
    }

    private void updateRepository(SourceRefreshResult result) {
        SourceIdentity sourceIdentity = null;
        if (result.getSnapshot() != null) {
            sourceIdentity = SourceIdentity.fromWarningSourceType(result.getSnapshot().getSourceType());
        } else if (result.getRadarFrame() != null) {
            sourceIdentity = SourceIdentity.RADAR;
        } else if (result.getSourceState() != null) {
            sourceIdentity = result.getSourceState().getSourceIdentity();
        }

        WarningSourceRuntimeState warningState = warningState(sourceIdentity);
        if (warningState != null) {
            if (result.isSuccess() && result.getSnapshot() != null) {
                warningState.updateRepository(warningRepository, result.getSnapshot());
            }
            warningState.setSourceState(result.getSourceState());
        } else if (sourceIdentity == SourceIdentity.RADAR) {
            radarRuntime.applyRefreshResult(result);
        }

        SafeLayerDebugLog.i(TAG, "repository-updated source=" + sourceIdentity
                + ", bbkRecords=" + warningState(WarningSourceType.BBK).records(warningRepository).size()
                + ", dwdRecords=" + warningState(WarningSourceType.DWD).records(warningRepository).size()
                + ", radarFrame=" + (radarRepository != null && radarRepository.getCurrentFrame() != null));
    }

    private SourceState applyRenderState(MapBindingOutcome bindingOutcome) {
        applyWarningRenderState();
        return radarRuntime.applyRenderState("ui-state", bindingOutcome);
    }

    private void applyWarningRenderState() {
        for (WarningSourceRuntimeState warningState : warningStates.values()) {
            WarningRenderController renderController = warningState.renderController();
            if (renderController == null) {
                continue;
            }
            renderController.setLayerVisible(warningState.isLayerVisible());
            renderController.applySnapshot(warningState.records(warningRepository));
        }
    }

    private void applyPaneState(SourceState paneRadarState) {
        if (paneController == null) {
            return;
        }
        paneController.applyRuntimeState(
                warningState(WarningSourceType.BBK).records(warningRepository),
                warningState(WarningSourceType.BBK).sourceState(),
                warningState(WarningSourceType.DWD).records(warningRepository),
                warningState(WarningSourceType.DWD).sourceState(),
                radarRuntime.currentFrame(),
                paneRadarState,
                warningState(WarningSourceType.BBK).isLayerVisible(),
                warningState(WarningSourceType.DWD).isLayerVisible(),
                radarRuntime.isLayerVisible(),
                radarRuntime.transparencyPercent());
    }

    private void postUiUpdate() {
        postUiUpdate(activeSessionId);
    }

    private void postUiUpdate(final long sessionId) {
        if (uiThreadRunner == null || !isActiveSession(sessionId)) {
            return;
        }
        uiThreadRunner.run(new Runnable() {
            @Override
            public void run() {
                if (!isActiveSession(sessionId)) {
                    return;
                }
                applyUiState();
            }
        });
    }

    private void refreshPaneStateAfterRadarFailure() {
        if (paneController == null) {
            return;
        }
        if (uiThreadRunner != null && hasSession()) {
            postUiUpdate();
            return;
        }
        applyPaneState(radarRuntime.currentUiState());
    }

    private void updateManualRefreshIndicator(final boolean active) {
        final long sessionId = activeSessionId;
        if (paneController == null || !isActiveSession(sessionId)) {
            return;
        }
        if (uiThreadRunner == null) {
            paneController.setManualRefreshActive(active);
            return;
        }
        uiThreadRunner.run(new Runnable() {
            @Override
            public void run() {
                if (paneController != null && isActiveSession(sessionId)) {
                    paneController.setManualRefreshActive(active);
                }
            }
        });
    }

    private MapBindingOutcome ensureMapBinding() {
        if (!isRuntimeActive() || mapOverlayController == null) {
            return MapBindingOutcome.pending(MapOverlayController.BindingResult.pending(currentMapView()), false);
        }

        MapOverlayController.BindingResult bindingResult = bindOverlayToMap();
        boolean releasedBinding = bindingResult.didReleasePreviousBinding();
        if (releasedBinding) {
            clearRenderControllers();
            SafeLayerDebugLog.i(TAG, "map-binding-reset mapView="
                    + (bindingResult.getMapView() == null ? "null" : bindingResult.getMapView().hashCode()));
        }
        if (!bindingResult.isBound()) {
            scheduleMapBindingRetry(bindingResult);
            return MapBindingOutcome.pending(bindingResult, false);
        }

        resetMapBindingRetryState();
        if (bindingResult.didRebind()) {
            SafeLayerDebugLog.i(TAG, "map-rebind mapView="
                    + (bindingResult.getMapView() == null ? "null" : bindingResult.getMapView().hashCode()));
        }

        boolean renderControllersReady = attachRenderControllersToBinding(bindingResult);
        SafeLayerDebugLog.i(TAG, "map-bind-ready bbkGroup=" + (mapOverlayController.getBbkMapGroup() != null)
                + ", dwdGroup=" + (mapOverlayController.getDwdMapGroup() != null)
                + ", radarGroup=" + (mapOverlayController.getRadarMapGroup() != null)
                + ", warningControllers=" + renderControllersReady);
        return MapBindingOutcome.bound(bindingResult, renderControllersReady);
    }

    private MapOverlayController.BindingResult bindOverlayToMap() {
        MapView mapView = currentMapView();
        MapOverlayController.BindingResult bindingResult = mapOverlayController.ensureBound(mapView);
        if (!bindingResult.isBound()) {
            SafeLayerDebugLog.w(TAG, "map-bind-pending mapView=" + (mapView != null));
        }
        return bindingResult;
    }

    private boolean attachRenderControllersToBinding(MapOverlayController.BindingResult bindingResult) {
        if (!bindingResult.isBound()) {
            return false;
        }
        createWarningRenderControllersIfNeeded();
        radarRuntime.ensureRenderReady("map-bind", bindingResult);
        return hasAllWarningRenderControllers();
    }

    private boolean hasAllWarningRenderControllers() {
        for (WarningSourceRuntimeState warningState : warningStates.values()) {
            if (warningState.renderController() == null) {
                return false;
            }
        }
        return true;
    }

    private void createWarningRenderControllersIfNeeded() {
        if (mapOverlayController == null
                || mapOverlayController.getBbkMapGroup() == null
                || mapOverlayController.getDwdMapGroup() == null) {
            return;
        }

        SeverityColorPolicy colorPolicy = new SeverityColorPolicy();
        WarningMarkerFactory markerFactory = new WarningMarkerFactory(colorPolicy);
        WarningShapeFactory shapeFactory = new WarningShapeFactory(
                mapOverlayController.getBoundMapView(),
                colorPolicy);
        createWarningRenderControllerIfNeeded(
                WarningSourceType.BBK,
                mapOverlayController.getBbkMapGroup(),
                markerFactory,
                shapeFactory,
                colorPolicy);
        createWarningRenderControllerIfNeeded(
                WarningSourceType.DWD,
                mapOverlayController.getDwdMapGroup(),
                markerFactory,
                shapeFactory,
                colorPolicy);
    }

    private void createWarningRenderControllerIfNeeded(
            WarningSourceType sourceType,
            MapGroup mapGroup,
            WarningMarkerFactory markerFactory,
            WarningShapeFactory shapeFactory,
            SeverityColorPolicy colorPolicy) {
        WarningSourceRuntimeState warningState = warningState(sourceType);
        if (warningState == null || warningState.renderController() != null) {
            return;
        }
        warningState.setRenderController(new WarningRenderController(
                mapGroup,
                sourceType,
                new RenderRegistry(),
                markerFactory,
                shapeFactory,
                colorPolicy));
    }

    private void clearRenderControllers() {
        clearWarningRenderControllers();
        radarRuntime.clearRenderController();
        SafeLayerDebugLog.i(TAG, "render-controllers-cleared");
    }

    private void clearWarningRenderControllers() {
        for (WarningSourceRuntimeState warningState : warningStates.values()) {
            warningState.clearRenderController();
        }
    }

    private void applySettings(PluginSettings settings) {
        if (settings == null || settings.getLayerVisibilityState() == null) {
            return;
        }
        warningState(WarningSourceType.BBK).setLayerVisible(settings.getLayerVisibilityState().isBbkVisible());
        warningState(WarningSourceType.DWD).setLayerVisible(settings.getLayerVisibilityState().isDwdVisible());
        radarRuntime.applySettings(settings);
        if (paneController != null) {
            applyPaneState(radarRuntime.currentUiState());
        }
    }

    private void persistSettings() {
        if (settingsRepository == null) {
            return;
        }
        settingsRepository.write(new PluginSettings(
                new LayerVisibilityState(
                        warningState(WarningSourceType.BBK).isLayerVisible(),
                        warningState(WarningSourceType.DWD).isLayerVisible(),
                        radarRuntime.isLayerVisible()),
                radarRuntime.transparencyPercent()));
    }

    private void handleWarningLayerVisibleChanged(WarningSourceType sourceType, boolean visible) {
        boolean changed = setSourceLayerVisible(sourceType, visible);
        WarningSourceRuntimeState warningState = warningState(sourceType);
        if (warningState == null || !changed) {
            return;
        }
        WarningRenderController renderController = warningState.renderController();
        if (renderController != null) {
            renderController.setLayerVisible(visible);
        }
    }

    private WarningRenderController renderControllerFor(WarningSourceType sourceType) {
        WarningSourceRuntimeState warningState = warningState(sourceType);
        return warningState == null ? null : warningState.renderController();
    }

    private boolean isSourceLayerVisible(WarningSourceType sourceType) {
        WarningSourceRuntimeState warningState = warningState(sourceType);
        return warningState != null && warningState.isLayerVisible();
    }

    private boolean setSourceLayerVisible(WarningSourceType sourceType, boolean visible) {
        WarningSourceRuntimeState warningState = warningState(sourceType);
        if (warningState == null) {
            return false;
        }
        if (warningState.isLayerVisible() == visible) {
            return false;
        }
        warningState.setLayerVisible(visible);
        if (visible) {
            requestRefresh(SourceIdentity.fromWarningSourceType(sourceType));
        }
        persistSettings();
        return true;
    }

    private void ensureWarningFocusSourceVisible(WarningSourceType sourceType) {
        if (sourceType == null || isSourceLayerVisible(sourceType)) {
            return;
        }

        boolean changed = setSourceLayerVisible(sourceType, true);
        WarningRenderController renderController = renderControllerFor(sourceType);
        if (renderController != null) {
            renderController.setLayerVisible(true);
        }
        if (changed && paneController != null) {
            paneController.setSourceLayerVisible(sourceType, true);
        }
    }

    private void replayPendingWarningFocus(MapBindingOutcome bindingOutcome) {
        if (!isRuntimeActive()
                || bindingOutcome == null
                || !bindingOutcome.isBound()
                || !bindingOutcome.hasStableRenderControllers()) {
            return;
        }
        focusController.replayPending(warningRecordResolver, warningFocusExecutor);
    }

    private void scheduleMapBindingRetry(MapOverlayController.BindingResult bindingResult) {
        if (!isRuntimeActive() || uiThreadRunner == null) {
            return;
        }

        MapView targetMapView = bindingResult == null ? currentMapView() : bindingResult.getMapView();
        if (bindingResult != null && bindingResult.isBound()) {
            resetMapBindingRetryState();
            return;
        }
        if (mapBindRetryScheduled || isMapBindingResolved(targetMapView)) {
            return;
        }

        final long sessionId = activeSessionId;
        mapBindRetryScheduled = true;
        mapBindRetryRunnable = new Runnable() {
            @Override
            public void run() {
                Runnable runningRetry = mapBindRetryRunnable;
                clearScheduledRetryReference(runningRetry);
                if (!isActiveSession(sessionId)) {
                    return;
                }
                if (isMapBindingResolved(currentMapView())) {
                    return;
                }
                MapBindingOutcome bindingOutcome = ensureMapBinding();
                if (bindingOutcome.isBound()) {
                    postUiUpdate(sessionId);
                } else {
                    scheduleMapBindingRetry(bindingOutcome.bindingResult());
                }
            }
        };
        SafeLayerDebugLog.w(TAG, "map-bind-retry scheduled delayMs=" + MAP_BIND_RETRY_DELAY_MS
                + ", mapView=" + (targetMapView == null ? "null" : targetMapView.hashCode()));
        uiThreadRunner.postDelayed(mapBindRetryRunnable, MAP_BIND_RETRY_DELAY_MS);
    }

    private void resetMapBindingRetryState() {
        if (uiThreadRunner != null && mapBindRetryRunnable != null) {
            uiThreadRunner.removeCallbacks(mapBindRetryRunnable);
        }
        clearScheduledRetryReference(mapBindRetryRunnable);
    }

    private void clearScheduledRetryReference(Runnable runnable) {
        if (mapBindRetryRunnable == null || runnable != mapBindRetryRunnable) {
            return;
        }
        mapBindRetryScheduled = false;
        mapBindRetryRunnable = null;
    }

    private boolean isMapBindingResolved(MapView mapView) {
        return mapView != null
                && mapOverlayController != null
                && mapOverlayController.getBoundMapView() == mapView
                && mapOverlayController.getBbkMapGroup() != null
                && mapOverlayController.getDwdMapGroup() != null
                && mapOverlayController.getRadarMapGroup() != null;
    }

    private int snapshotSize(WarningSnapshot snapshot) {
        return snapshot == null || snapshot.getRecords() == null ? 0 : snapshot.getRecords().size();
    }

    private String describeContext(Context context) {
        if (context == null) {
            return "null";
        }
        return context.getPackageName();
    }

    private String describeState(SourceState sourceState) {
        return sourceState == null
                ? "null"
                : sourceState.getSourceIdentity() + ":" + sourceState.getStatus();
    }

    private SourceState normalizeRestoredRadarState(SourceState sourceState, RadarFrame radarFrame) {
        return normalizeActiveRadarState(sourceState, null, radarFrame);
    }

    private SourceState copySourceState(SourceState sourceState) {
        SourceState copy = SourceState.forSource(SourceIdentity.RADAR);
        if (sourceState == null) {
            return copy;
        }
        copy.setSourceIdentity(sourceState.getSourceIdentity());
        copy.setStatus(sourceState.getStatus());
        copy.setLastSuccessEpochMs(sourceState.getLastSuccessEpochMs());
        copy.setLastErrorMessage(sourceState.getLastErrorMessage());
        return copy;
    }

    private SourceState normalizeActiveRadarState(
            SourceState runtimeState,
            SourceState repositoryState,
            RadarFrame radarFrame) {
        SourceState normalizedState = copySourceState(preferredActiveRadarState(runtimeState, repositoryState));
        normalizedState.setSourceIdentity(SourceIdentity.RADAR);

        long lastSuccessEpochMs = maxRadarLastSuccessEpochMs(runtimeState, repositoryState, radarFrame);
        if (lastSuccessEpochMs > 0L) {
            normalizedState.setLastSuccessEpochMs(lastSuccessEpochMs);
        }

        String lastErrorMessage = firstNonBlank(
                normalizedState.getLastErrorMessage(),
                runtimeState == null ? null : runtimeState.getLastErrorMessage(),
                repositoryState == null ? null : repositoryState.getLastErrorMessage());
        normalizedState.setLastErrorMessage(lastErrorMessage);

        boolean hasSuccessfulData = radarFrame != null || lastSuccessEpochMs > 0L;
        SourceState.Status status = normalizedState.getStatus();
        if (status == SourceState.Status.DISABLED || status == SourceState.Status.ERROR_NO_CACHE) {
            normalizedState.setStatus(hasSuccessfulData
                    ? (lastErrorMessage == null
                    ? SourceState.Status.STALE
                    : SourceState.Status.ERROR_WITH_CACHE)
                    : SourceState.Status.ERROR_NO_CACHE);
        }
        return normalizedState;
    }

    private SourceState preferredActiveRadarState(SourceState runtimeState, SourceState repositoryState) {
        if (isActiveRadarState(repositoryState)) {
            return repositoryState;
        }
        if (isActiveRadarState(runtimeState)) {
            return runtimeState;
        }
        if (isNonDisabledRadarState(repositoryState)) {
            return repositoryState;
        }
        if (isNonDisabledRadarState(runtimeState)) {
            return runtimeState;
        }
        return repositoryState != null ? repositoryState : runtimeState;
    }

    private boolean isActiveRadarState(SourceState state) {
        if (state == null || state.getStatus() == null) {
            return false;
        }
        switch (state.getStatus()) {
            case LIVE:
            case DEGRADED_WITH_DATA:
            case STALE:
            case ERROR_WITH_CACHE:
                return true;
            case DISABLED:
            case ERROR_NO_CACHE:
            default:
                return false;
        }
    }

    private boolean isNonDisabledRadarState(SourceState state) {
        return state != null
                && state.getStatus() != null
                && state.getStatus() != SourceState.Status.DISABLED;
    }

    private long maxRadarLastSuccessEpochMs(
            SourceState runtimeState,
            SourceState repositoryState,
            RadarFrame radarFrame) {
        long lastSuccessEpochMs = 0L;
        if (runtimeState != null) {
            lastSuccessEpochMs = Math.max(lastSuccessEpochMs, runtimeState.getLastSuccessEpochMs());
        }
        if (repositoryState != null) {
            lastSuccessEpochMs = Math.max(lastSuccessEpochMs, repositoryState.getLastSuccessEpochMs());
        }
        if (radarFrame != null) {
            lastSuccessEpochMs = Math.max(lastSuccessEpochMs, radarFrame.getFrameEpochMs());
        }
        return lastSuccessEpochMs;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = StringUtils.trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private void normalizeRadarFrameProductLabel(RadarFrame radarFrame) {
        if (radarFrame == null) {
            return;
        }
        if (SafeLayerConstants.RADAR_RV_PRODUCT_ID.equals(StringUtils.trimToNull(radarFrame.getProductId()))) {
            radarFrame.setProductLabel(SafeLayerConstants.RADAR_RV_PRODUCT_LABEL);
        }
    }

    private void requestStartupRefresh() {
        if (!isRuntimeActive() || refreshCoordinator == null) {
            return;
        }
        boolean accepted = refreshCoordinator.requestRefreshAll();
        if (!accepted) {
            SafeLayerDebugLog.w(TAG, "startup-refresh-skipped");
        }
    }

    private void requestRefresh(SourceIdentity sourceIdentity) {
        if (!isRuntimeActive() || refreshCoordinator == null || sourceIdentity == null) {
            return;
        }
        boolean accepted = refreshCoordinator.requestSourceRefresh(sourceIdentity);
        if (!accepted) {
            SafeLayerDebugLog.w(TAG, "source-refresh-skipped source=" + sourceIdentity);
        }
    }

    private long activateSession() {
        activeSessionId = nextSessionId++;
        return activeSessionId;
    }

    private void rollbackFailedStart() {
        SafeLayerDebugLog.w(TAG, "runtime-start-rollback");
        shutdownRuntimeState();
    }

    private void shutdownRuntimeState() {
        deactivateSession();
        resetMapBindingRetryState();
        detachRefreshLifecycle();
        focusController.reset();
        radarRuntime.deactivate();
        clearWarningRenderControllers();
        stopOverlay();
        stopPane();
        resetRuntimeReferences();
    }

    private void deactivateSession() {
        activeSessionId = NO_ACTIVE_SESSION;
    }

    private boolean hasSession() {
        return activeSessionId != NO_ACTIVE_SESSION
                || paneController != null
                || refreshCoordinator != null
                || mapOverlayController != null
                || uiThreadRunner != null;
    }

    private boolean isRuntimeActive() {
        return activeSessionId != NO_ACTIVE_SESSION;
    }

    private boolean isActiveSession(long sessionId) {
        return sessionId != NO_ACTIVE_SESSION && activeSessionId == sessionId;
    }

    private MapView currentMapView() {
        return mapViewProvider == null ? null : mapViewProvider.get();
    }

    private void stopOverlay() {
        if (mapOverlayController != null) {
            mapOverlayController.stop();
        }
    }

    private void stopPane() {
        if (paneController != null) {
            paneController.stop();
        }
    }

    private void resetRuntimeReferences() {
        cacheStore = null;
        if (radarRepository != null) {
            radarRepository.clear();
        }
        radarRepository = null;
        warningRepository = null;
        refreshCoordinator = null;
        paneController = null;
        mapOverlayController = null;
        settingsRepository = null;
        pluginContext = null;
        uiService = null;
        mapViewProvider = null;
        uiThreadRunner = null;
        resetWarningStates();
        radarRuntime.resetAfterStop();
    }

    private final class WarningFocusRuntimeAdapter implements WarningFocusController.FocusExecutor {

        @Override
        public boolean canFocus(WarningRecord record, WarningRenderSpec spec) {
            if (record == null || spec == null || !spec.hasFocusableLocation()) {
                return false;
            }

            ensureWarningFocusSourceVisible(record.getSourceType());
            MapBindingOutcome bindingOutcome = ensureMapBinding();
            WarningRenderController renderController = renderControllerFor(record.getSourceType());
            boolean hasRenderController = renderController != null;
            MapView mapView = bindingOutcome.getMapView();
            boolean canFocus = bindingOutcome.isBound()
                    && SafeLayerRuntime.this.canPanToWarning(mapView);
            if (!canFocus) {
                logPendingFocus(record.getStableId(), hasRenderController);
            }
            return canFocus;
        }

        @Override
        public void focus(WarningRecord record, WarningRenderSpec spec) {
            panToWarning(mapOverlayController == null ? null : mapOverlayController.getBoundMapView(), spec);
        }

        @Override
        public void onDeferred(String stableId, WarningRecord record) {
            // Deferred focus is already logged from the readiness check.
        }
    }

    private void logPendingFocus(String stableId, boolean hasRenderController) {
        SafeLayerDebugLog.w(TAG, "focus-pending stableId=" + stableId
                + ", renderController=" + hasRenderController);
    }

    private final class RadarRuntime {

        private final RadarActionRunner actionRunner = new RadarActionRunner();
        private DwdRadarSource radarSource;
        private RadarRenderController radarRenderController;
        private boolean radarSourceRegistered;
        private boolean radarCacheRestored;
        private SourceState radarSourceState = inactiveRadarState();
        private boolean radarLayerVisible;
        private int radarTransparencyPercent = SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT;

        private void resetForStart() {
            radarSource = null;
            radarRenderController = null;
            radarSourceRegistered = false;
            radarCacheRestored = false;
            radarSourceState = inactiveRadarState();
            radarLayerVisible = false;
            radarTransparencyPercent = SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT;
        }

        private void resetAfterStop() {
            resetForStart();
        }

        private void deactivate() {
            clearRenderController();
            radarSource = null;
            radarSourceRegistered = false;
            radarCacheRestored = false;
            radarSourceState = inactiveRadarState();
            radarLayerVisible = false;
            radarTransparencyPercent = SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT;
        }

        private void applySettings(PluginSettings settings) {
            if (settings == null || settings.getLayerVisibilityState() == null) {
                return;
            }
            radarLayerVisible = settings.getLayerVisibilityState().isRadarVisible();
            radarTransparencyPercent = settings.getRadarTransparencyPercent();
        }

        private void onTransparencyChanged(final int transparencyPercent) {
            radarTransparencyPercent = transparencyPercent;
            if (radarRenderController != null) {
                boolean applied = handleRenderOutcome("transparency", actionRunner.runRender(
                        new RadarActionRunner.RenderAction() {
                            @Override
                            public void run() throws RadarRenderException {
                                radarRenderController.setTransparencyPercent(transparencyPercent);
                            }
                        }));
                if (applied && detectRendererFailure("transparency")) {
                    applied = false;
                }
                if (!applied) {
                    postUiUpdate();
                }
            }
            persistSettings();
        }

        private DwdRadarSource ensureSourceInitialized() {
            if (radarSource == null) {
                radarSource = createRadarSource(new HttpClient(), cacheStore);
            }
            return radarSource;
        }

        private boolean registerSourceIfNeeded() {
            if (refreshCoordinator == null || radarSource == null || radarSourceRegistered) {
                return false;
            }
            refreshCoordinator.registerSource(
                    SourceIdentity.RADAR,
                    radarSource,
                    SafeLayerConstants.RADAR_REFRESH_INTERVAL_MS);
            radarSourceRegistered = true;
            return true;
        }

        private void restoreCacheIfEnabled(String stage) {
            if (!radarLayerVisible) {
                radarSourceState = inactiveRadarState();
                return;
            }
            handleActionOutcome(stage, actionRunner.run(new RadarActionRunner.Action() {
                @Override
                public void run() {
                    restoreCacheIfNeededInternal();
                }
            }));
        }

        private void restoreCacheIfNeededInternal() {
            if (radarCacheRestored || radarRepository == null) {
                return;
            }

            RadarFrame cachedRadarFrame = ensureSourceInitialized().loadFromCache();
            normalizeRadarFrameProductLabel(cachedRadarFrame);
            radarSourceState = normalizeRestoredRadarState(
                    readCachedSourceState(SourceIdentity.RADAR),
                    cachedRadarFrame);
            radarRepository.restore(cachedRadarFrame, radarSourceState);
            radarCacheRestored = true;
        }

        private void ensureRenderReady(
                final String stage,
                final MapOverlayController.BindingResult bindingResult) {
            if (!radarLayerVisible) {
                return;
            }
            handleActionOutcome(stage, actionRunner.run(new RadarActionRunner.Action() {
                @Override
                public void run() {
                    ensureSourceInitialized();
                    restoreCacheIfNeededInternal();
                    registerSourceIfNeeded();
                    if (bindingResult == null
                            || !bindingResult.isBound()
                            || mapOverlayController == null
                            || mapOverlayController.getRadarMapGroup() == null) {
                        SafeLayerDebugLog.i(TAG, "radar-init-deferred stage=" + stage + ", mapReady=false");
                        return;
                    }
                    if (radarRenderController == null) {
                        radarRenderController = createRadarRenderController();
                    }
                }
            }));
        }

        private SourceState applyRenderState(String stage, MapBindingOutcome bindingOutcome) {
            if (radarLayerVisible && radarRenderController == null) {
                ensureRenderReady(stage, bindingOutcome.bindingResult());
            }
            if (radarRenderController != null) {
                handleRenderOutcome(stage, actionRunner.runRender(new RadarActionRunner.RenderAction() {
                    @Override
                    public void run() throws RadarRenderException {
                        radarRenderController.setLayerVisible(radarLayerVisible);
                        radarRenderController.setTransparencyPercent(radarTransparencyPercent);
                        radarRenderController.apply(RadarRenderSpec.from(
                                radarRepository == null ? null : radarRepository.getCurrentFrame(),
                                radarLayerVisible,
                                radarTransparencyPercent));
                    }
                }));
                detectRendererFailure(stage);
            }
            return currentUiState();
        }

        private void applyRefreshResult(SourceRefreshResult result) {
            if (result == null) {
                return;
            }
            if (result.isSuccess() && result.getRadarFrame() != null && radarRepository != null) {
                normalizeRadarFrameProductLabel(result.getRadarFrame());
                radarRepository.updateFrame(result.getRadarFrame());
            }
            if (result.getSourceState() != null) {
                radarSourceState = result.getSourceState();
            }
            if (radarRepository != null) {
                radarRepository.updateSourceState(radarSourceState);
            }
        }

        private void onLayerVisibleChanged(boolean visible) {
            boolean wasVisible = radarLayerVisible;
            if (wasVisible == visible) {
                if (radarRenderController != null) {
                    radarRenderController.setLayerVisible(visible);
                }
                postUiUpdate();
                return;
            }

            radarLayerVisible = visible;
            if (visible) {
                ensureRenderReady("toggle", ensureMapBinding().bindingResult());
                if (!radarLayerVisible) {
                    persistSettings();
                    postUiUpdate();
                    return;
                }
                normalizeActiveStateForVisibility();
                requestRefresh(SourceIdentity.RADAR);
            } else {
                if (radarRenderController != null) {
                    radarRenderController.setLayerVisible(false);
                }
                radarSourceState = inactiveRadarState();
                if (radarRepository != null) {
                    radarRepository.updateSourceState(radarSourceState);
                }
            }
            persistSettings();
            postUiUpdate();
        }

        private void clearRenderController() {
            if (radarRenderController != null) {
                radarRenderController.clear();
                radarRenderController = null;
            }
        }

        private boolean hasRenderController() {
            return radarRenderController != null;
        }

        private boolean isLayerVisible() {
            return radarLayerVisible;
        }

        private int transparencyPercent() {
            return radarTransparencyPercent;
        }

        private RadarFrame currentFrame() {
            return radarRepository == null ? null : radarRepository.getCurrentFrame();
        }

        private long sourceStateLastSuccessEpochMs() {
            return radarSourceState == null ? 0L : radarSourceState.getLastSuccessEpochMs();
        }

        private SourceState currentUiState() {
            if (!radarLayerVisible) {
                return radarSourceState == null ? inactiveRadarState() : radarSourceState;
            }
            return normalizeActiveStateForVisibility();
        }

        private SourceState normalizeActiveStateForVisibility() {
            SourceState normalizedState = normalizeActiveRadarState(
                    radarSourceState,
                    radarRepository == null ? null : radarRepository.getSourceState(),
                    radarRepository == null ? null : radarRepository.getCurrentFrame());
            radarSourceState = normalizedState;
            if (radarRepository != null) {
                radarRepository.updateSourceState(normalizedState);
            }
            return normalizedState;
        }

        private boolean handleActionOutcome(String stage, RadarActionRunner.Outcome outcome) {
            if (outcome == null || outcome.isSuccess()) {
                return true;
            }
            if (outcome.isRenderFailure()) {
                handleRenderFailure(stage, outcome.getRenderFailure());
                return false;
            }
            disable(stage, outcome.getFatalFailure());
            return false;
        }

        private boolean handleRenderOutcome(String stage, RadarActionRunner.Outcome outcome) {
            return handleActionOutcome(stage, outcome);
        }

        private void handleRenderFailure(String stage, Throwable exception) {
            onRendererFailure(stage, null, exception);
        }

        private boolean detectRendererFailure(String stage) {
            if (radarRenderController == null || !radarRenderController.hasRendererFailure()) {
                return false;
            }
            onRendererFailure(stage, radarRenderController.getRendererFailureMessage(), null);
            return true;
        }

        private void onRendererFailure(String stage, String message, Throwable throwable) {
            String resolvedMessage = StringUtils.trimToNull(message);
            if (resolvedMessage == null) {
                resolvedMessage = "Radar rendering failed at " + stage + ": "
                        + StringUtils.trimToNull(throwable == null ? null : throwable.getMessage());
            }
            SafeLayerDebugLog.e(TAG, resolvedMessage, throwable);
            radarLayerVisible = false;
            clearRenderController();
            radarSourceState = radarRendererFailureState(resolvedMessage);
            if (radarRepository != null) {
                radarRepository.updateSourceState(radarSourceState);
            }
            persistSettings();
        }

        private void disable(String stage, Throwable exception) {
            String message = "Radar disabled at " + stage + ": "
                    + StringUtils.trimToNull(exception == null ? null : exception.getMessage());
            SafeLayerDebugLog.w(TAG, message);
            try {
                if (exception == null) {
                    Log.w(TAG, message);
                } else {
                    Log.w(TAG, message, exception);
                }
            } catch (RuntimeException ignored) {
                // Host-side unit tests may not provide a full Android logging runtime.
            }

            radarLayerVisible = false;
            if (!radarSourceRegistered) {
                radarSource = null;
            }
            radarCacheRestored = false;
            clearRenderController();
            radarSourceState = radarFailureState(message);
            if (radarRepository != null) {
                radarRepository.updateSourceState(radarSourceState);
            }
            if (paneController != null) {
                paneController.setRadarLayerVisible(false);
            }
            persistSettings();
        }

        private void setRenderControllerForTest(RadarRenderController radarRenderController) {
            this.radarRenderController = radarRenderController;
        }

        private void setSourceForTest(DwdRadarSource radarSource) {
            this.radarSource = radarSource;
        }

        private void setLayerVisibleForTest(boolean visible) {
            this.radarLayerVisible = visible;
        }
    }

    RadarRenderController createRadarRenderController() {
        if (mapOverlayController == null || mapOverlayController.getRadarMapGroup() == null) {
            throw new IllegalStateException("Radar map group unavailable.");
        }
        return new RadarRenderController(
                mapOverlayController.getRadarMapGroup(),
                new RadarLayerFactory(radarRendererFailureListener));
    }

    SourceState currentRadarUiStateForTest() {
        return radarRuntime.currentUiState();
    }

    void notifyRadarRendererFailureForTest(String message) {
        radarRuntime.onRendererFailure("test", message, null);
        refreshPaneStateAfterRadarFailure();
    }

    private static final class WarningSources {
        private final BbkWarningSource bbkSource;
        private final DwdCapWarningSource dwdSource;

        private WarningSources(BbkWarningSource bbkSource, DwdCapWarningSource dwdSource) {
            this.bbkSource = bbkSource;
            this.dwdSource = dwdSource;
        }
    }

    private static final class MapBindingOutcome {
        private final MapOverlayController.BindingResult bindingResult;
        private final boolean stableRenderControllers;

        private MapBindingOutcome(
                MapOverlayController.BindingResult bindingResult,
                boolean stableRenderControllers) {
            this.bindingResult = bindingResult;
            this.stableRenderControllers = stableRenderControllers;
        }

        private static MapBindingOutcome pending(
                MapOverlayController.BindingResult bindingResult,
                boolean stableRenderControllers) {
            return new MapBindingOutcome(bindingResult, stableRenderControllers);
        }

        private static MapBindingOutcome bound(
                MapOverlayController.BindingResult bindingResult,
                boolean stableRenderControllers) {
            return new MapBindingOutcome(bindingResult, stableRenderControllers);
        }

        private boolean isBound() {
            return bindingResult != null && bindingResult.isBound();
        }

        private boolean hasStableRenderControllers() {
            return stableRenderControllers;
        }

        private MapView getMapView() {
            return bindingResult == null ? null : bindingResult.getMapView();
        }

        private MapOverlayController.BindingResult bindingResult() {
            return bindingResult;
        }
    }

    private static final class CacheLocation {

        private final File primaryBaseDirectory;
        private final List<File> legacyBaseDirectories;

        private CacheLocation(File primaryBaseDirectory, List<File> legacyBaseDirectories) {
            this.primaryBaseDirectory = primaryBaseDirectory;
            this.legacyBaseDirectories = legacyBaseDirectories == null
                    ? new ArrayList<File>()
                    : legacyBaseDirectories;
        }
    }
}
