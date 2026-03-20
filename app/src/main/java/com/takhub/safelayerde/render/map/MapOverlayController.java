package com.takhub.safelayerde.render.map;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.ui.actions.UiActionRouter;
import com.takhub.safelayerde.util.StringUtils;

public class MapOverlayController {

    private static final String TAG = "SafeLayerOverlay";
    private static final String BBK_GROUP_NAME = "SafeLayer-BBK";
    private static final String DWD_GROUP_NAME = "SafeLayer-DWD";
    private static final String RADAR_GROUP_NAME = "SafeLayer-Radar";

    public static final class BindingResult {
        public enum State {
            PENDING,
            BOUND,
            REBOUND
        }

        private final State state;
        private final MapView mapView;
        private final boolean releasedPreviousBinding;

        private BindingResult(State state, MapView mapView, boolean releasedPreviousBinding) {
            this.state = state;
            this.mapView = mapView;
            this.releasedPreviousBinding = releasedPreviousBinding;
        }

        public static BindingResult pending(MapView mapView) {
            return pending(mapView, false);
        }

        public static BindingResult pending(MapView mapView, boolean releasedPreviousBinding) {
            return new BindingResult(State.PENDING, mapView, releasedPreviousBinding);
        }

        public static BindingResult bound(MapView mapView, boolean rebound) {
            return new BindingResult(rebound ? State.REBOUND : State.BOUND, mapView, rebound);
        }

        public boolean isBound() {
            return state != State.PENDING;
        }

        public boolean didRebind() {
            return state == State.REBOUND;
        }

        public boolean didReleasePreviousBinding() {
            return releasedPreviousBinding;
        }

        public MapView getMapView() {
            return mapView;
        }

        public State getState() {
            return state;
        }
    }

    private OverlayBinding binding;
    private final UiActionRouter uiActionRouter;
    private final MapEventDispatcher.MapEventDispatchListener mapEventListener;

    public MapOverlayController(UiActionRouter uiActionRouter) {
        this.uiActionRouter = uiActionRouter;
        this.mapEventListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                handleMapEvent(event);
            }
        };
    }

    public BindingResult ensureBound(MapView mapView) {
        if (mapView == null) {
            SafeLayerDebugLog.w(TAG, "ensureBound without mapView");
            return BindingResult.pending(null);
        }
        if (binding != null && binding.mapView == mapView) {
            if (binding.hasCompleteContract(mapView)) {
                return BindingResult.bound(mapView, false);
            }
            SafeLayerDebugLog.w(TAG, "mapView-binding-repair mapView=" + mapView.hashCode());
        }

        boolean rebound = binding != null;
        if (binding != null) {
            stopInternal();
            SafeLayerDebugLog.i(TAG, "mapView-rebound new=" + mapView.hashCode());
        }

        OverlayBinding newBinding = buildBinding(mapView);
        if (newBinding == null) {
            SafeLayerDebugLog.i(TAG, "ensureBound state=pending bbk=false, dwd=false, radar=false, listeners=false");
            return BindingResult.pending(mapView, rebound);
        }

        binding = newBinding;
        SafeLayerDebugLog.i(TAG, "ensureBound state=" + (rebound ? "rebound" : "bound")
                + " bbk=true, dwd=true, radar=true, listeners=true");
        return BindingResult.bound(mapView, rebound);
    }

    public void stop() {
        stopInternal();
    }

    public MapView getBoundMapView() {
        return binding == null ? null : binding.mapView;
    }

    private OverlayBinding buildBinding(MapView mapView) {
        MapGroup rootGroup = mapView.getRootGroup();
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        if (rootGroup == null || dispatcher == null) {
            return null;
        }

        MapGroup bbkGroup = null;
        MapGroup dwdGroup = null;
        MapGroup radarGroup = null;
        boolean listenersRegistered = false;
        try {
            bbkGroup = rootGroup.addGroup(BBK_GROUP_NAME);
            dwdGroup = rootGroup.addGroup(DWD_GROUP_NAME);
            radarGroup = rootGroup.addGroup(RADAR_GROUP_NAME);
            dispatcher.addMapEventListener(MapEvent.ITEM_PRESS, mapEventListener);
            dispatcher.addMapEventListener(MapEvent.ITEM_LONG_PRESS, mapEventListener);
            listenersRegistered = true;
            return new OverlayBinding(
                    mapView,
                    rootGroup,
                    dispatcher,
                    bbkGroup,
                    dwdGroup,
                    radarGroup,
                    listenersRegistered);
        } catch (RuntimeException exception) {
            releaseBinding(new OverlayBinding(
                    mapView,
                    rootGroup,
                    dispatcher,
                    bbkGroup,
                    dwdGroup,
                    radarGroup,
                    listenersRegistered));
            SafeLayerDebugLog.e(TAG, "overlay-bind-failed mapView=" + mapView.hashCode(), exception);
            return null;
        }
    }

    private void stopInternal() {
        releaseBinding(binding);
        binding = null;
        SafeLayerDebugLog.i(TAG, "overlay-stopped");
    }

    private void releaseBinding(OverlayBinding overlayBinding) {
        if (overlayBinding == null) {
            return;
        }

        MapEventDispatcher dispatcher = overlayBinding.dispatcher;
        if (dispatcher != null && overlayBinding.listenersRegistered) {
            try {
                dispatcher.removeMapEventListener(MapEvent.ITEM_PRESS, mapEventListener);
                dispatcher.removeMapEventListener(MapEvent.ITEM_LONG_PRESS, mapEventListener);
            } catch (RuntimeException exception) {
                SafeLayerDebugLog.w(TAG, "overlay-listener-remove-failed");
            }
        }

        MapGroup rootGroup = overlayBinding.rootGroup;
        if (rootGroup != null && overlayBinding.bbkMapGroup != null) {
            try {
                rootGroup.removeGroup(overlayBinding.bbkMapGroup);
            } catch (RuntimeException exception) {
                SafeLayerDebugLog.w(TAG, "overlay-group-remove-failed group=" + BBK_GROUP_NAME);
            }
        }
        if (rootGroup != null && overlayBinding.dwdMapGroup != null) {
            try {
                rootGroup.removeGroup(overlayBinding.dwdMapGroup);
            } catch (RuntimeException exception) {
                SafeLayerDebugLog.w(TAG, "overlay-group-remove-failed group=" + DWD_GROUP_NAME);
            }
        }
        if (rootGroup != null && overlayBinding.radarMapGroup != null) {
            try {
                rootGroup.removeGroup(overlayBinding.radarMapGroup);
            } catch (RuntimeException exception) {
                SafeLayerDebugLog.w(TAG, "overlay-group-remove-failed group=" + RADAR_GROUP_NAME);
            }
        }
    }

    public MapGroup getBbkMapGroup() {
        return binding == null ? null : binding.bbkMapGroup;
    }

    public MapGroup getDwdMapGroup() {
        return binding == null ? null : binding.dwdMapGroup;
    }

    public MapGroup getRadarMapGroup() {
        return binding == null ? null : binding.radarMapGroup;
    }

    private void handleMapEvent(MapEvent event) {
        if (event == null || uiActionRouter == null) {
            return;
        }

        MapItem mapItem = event.getItem();
        if (mapItem == null) {
            return;
        }

        String sourceType = StringUtils.trimToNull(mapItem.getMetaString("sourceType", null));
        String stableId = StringUtils.trimToNull(mapItem.getMetaString("stableId", null));
        if (sourceType == null || stableId == null) {
            SafeLayerDebugLog.i(TAG, "map-item-selection-ignored source=" + sourceType + ", stableId=" + stableId);
            return;
        }

        routeMapItemSelection(sourceType, stableId);
    }

    void routeMapItemSelection(String sourceType, String stableId) {
        MapOverlayRouting.routeMapItemSelection(uiActionRouter, sourceType, stableId);
    }

}

final class OverlayBinding {

    final MapView mapView;
    final MapGroup rootGroup;
    final MapEventDispatcher dispatcher;
    final MapGroup bbkMapGroup;
    final MapGroup dwdMapGroup;
    final MapGroup radarMapGroup;
    final boolean listenersRegistered;

    OverlayBinding(
            MapView mapView,
            MapGroup rootGroup,
            MapEventDispatcher dispatcher,
            MapGroup bbkMapGroup,
            MapGroup dwdMapGroup,
            MapGroup radarMapGroup,
            boolean listenersRegistered) {
        this.mapView = mapView;
        this.rootGroup = rootGroup;
        this.dispatcher = dispatcher;
        this.bbkMapGroup = bbkMapGroup;
        this.dwdMapGroup = dwdMapGroup;
        this.radarMapGroup = radarMapGroup;
        this.listenersRegistered = listenersRegistered;
    }

    boolean hasCompleteContract(MapView candidateMapView) {
        return candidateMapView != null
                && mapView == candidateMapView
                && rootGroup != null
                && dispatcher != null
                && rootGroup == candidateMapView.getRootGroup()
                && dispatcher == candidateMapView.getMapEventDispatcher()
                && bbkMapGroup != null
                && dwdMapGroup != null
                && radarMapGroup != null
                && listenersRegistered;
    }
}

final class MapOverlayRouting {

    private MapOverlayRouting() {
    }

    static void routeMapItemSelection(UiActionRouter uiActionRouter, String sourceType, String stableId) {
        String normalizedStableId = StringUtils.trimToNull(stableId);
        if (uiActionRouter == null
                || normalizedStableId == null
                || !isSupportedSourceType(sourceType)) {
            SafeLayerDebugLog.i(
                    "SafeLayerOverlay",
                    "map-item-selection-ignored source=" + sourceType + ", stableId=" + stableId);
            return;
        }
        uiActionRouter.openDetail(normalizedStableId);
        SafeLayerDebugLog.i(
                "SafeLayerOverlay",
                "map-item-selection-routed source=" + sourceType + ", stableId=" + normalizedStableId);
    }

    private static boolean isSupportedSourceType(String sourceType) {
        String normalizedSourceType = StringUtils.trimToNull(sourceType);
        if (normalizedSourceType == null) {
            return false;
        }
        try {
            WarningSourceType warningSourceType = WarningSourceType.valueOf(normalizedSourceType);
            return warningSourceType == WarningSourceType.BBK || warningSourceType == WarningSourceType.DWD;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
