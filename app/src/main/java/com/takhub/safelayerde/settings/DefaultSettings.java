package com.takhub.safelayerde.settings;

import com.takhub.safelayerde.domain.model.LayerVisibilityState;
import com.takhub.safelayerde.plugin.SafeLayerConstants;

public final class DefaultSettings {

    private DefaultSettings() {
    }

    public static PluginSettings create() {
        return new PluginSettings(
                new LayerVisibilityState(true, true, false),
                SafeLayerConstants.DEFAULT_RADAR_TRANSPARENCY_PERCENT);
    }
}
