package com.takhub.safelayerde.platform;

import com.atakmap.android.maps.MapView;

import gov.tak.api.plugin.IServiceController;

public class MapViewProvider {

    private final IServiceController serviceController;

    public MapViewProvider(IServiceController serviceController) {
        this.serviceController = serviceController;
    }

    public MapView get() {
        MapView resolved = serviceController == null ? null : serviceController.getService(MapView.class);
        if (resolved != null) {
            return resolved;
        }

        try {
            return MapView.getMapView();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
