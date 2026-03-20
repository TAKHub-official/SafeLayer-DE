package com.takhub.safelayerde.plugin;

import android.content.Context;

import com.atak.plugins.impl.PluginContextProvider;
import com.takhub.safelayerde.R;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.platform.MapViewProvider;

import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;

public final class SafeLayerDependencies {

    private static final String TAG = "SafeLayerDeps";

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final MapViewProvider mapViewProvider;

    public SafeLayerDependencies(IServiceController serviceController) {
        Context resolvedContext = null;
        PluginContextProvider contextProvider = serviceController == null
                ? null
                : serviceController.getService(PluginContextProvider.class);
        if (contextProvider != null) {
            resolvedContext = contextProvider.getPluginContext();
            if (resolvedContext != null) {
                resolvedContext.setTheme(R.style.ATAKPluginTheme);
            }
        }

        this.pluginContext = resolvedContext;
        this.uiService = serviceController == null
                ? null
                : serviceController.getService(IHostUIService.class);
        this.mapViewProvider = new MapViewProvider(serviceController);
    }

    public void logResolvedServices() {
        boolean hasMapView = mapViewProvider.get() != null;
        SafeLayerDebugLog.i(TAG, "services-resolved pluginContext=" + (this.pluginContext != null)
                + ", uiService=" + (this.uiService != null)
                + ", mapView=" + hasMapView);
    }

    public Context getPluginContext() {
        return pluginContext;
    }

    public IHostUIService getUiService() {
        return uiService;
    }

    public MapViewProvider getMapViewProvider() {
        return mapViewProvider;
    }
}
