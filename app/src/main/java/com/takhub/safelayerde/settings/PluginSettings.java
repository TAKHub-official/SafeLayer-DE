package com.takhub.safelayerde.settings;

import com.takhub.safelayerde.domain.model.LayerVisibilityState;

public class PluginSettings {

    private final LayerVisibilityState layerVisibilityState;
    private final int radarTransparencyPercent;

    public PluginSettings(LayerVisibilityState layerVisibilityState, int radarTransparencyPercent) {
        this.layerVisibilityState = layerVisibilityState;
        this.radarTransparencyPercent = radarTransparencyPercent;
    }

    public LayerVisibilityState getLayerVisibilityState() {
        return layerVisibilityState;
    }

    public int getRadarTransparencyPercent() {
        return radarTransparencyPercent;
    }
}
