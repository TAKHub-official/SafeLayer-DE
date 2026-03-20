package com.takhub.safelayerde.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.takhub.safelayerde.domain.model.LayerVisibilityState;

public class SettingsRepository {

    private static final String PREF_NAME = "safelayer.settings";
    private static final String PREF_KEY_BBK_VISIBLE = "bbk_visible";
    private static final String PREF_KEY_DWD_VISIBLE = "dwd_visible";
    private static final String PREF_KEY_RADAR_VISIBLE = "radar_visible";
    private static final String PREF_KEY_RADAR_TRANSPARENCY = "radar_transparency";

    private final Context context;

    public SettingsRepository(Context context) {
        this.context = context;
    }

    public PluginSettings read() {
        PluginSettings defaults = DefaultSettings.create();
        if (context == null) {
            return defaults;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        LayerVisibilityState visibilityState = new LayerVisibilityState(
                preferences.getBoolean(
                        PREF_KEY_BBK_VISIBLE,
                        defaults.getLayerVisibilityState().isBbkVisible()),
                preferences.getBoolean(
                        PREF_KEY_DWD_VISIBLE,
                        defaults.getLayerVisibilityState().isDwdVisible()),
                preferences.getBoolean(
                        PREF_KEY_RADAR_VISIBLE,
                        defaults.getLayerVisibilityState().isRadarVisible()));
        int transparency = clampTransparency(
                preferences.getInt(
                        PREF_KEY_RADAR_TRANSPARENCY,
                        defaults.getRadarTransparencyPercent()));
        return new PluginSettings(visibilityState, transparency);
    }

    public void write(PluginSettings settings) {
        if (context == null || settings == null || settings.getLayerVisibilityState() == null) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean(PREF_KEY_BBK_VISIBLE, settings.getLayerVisibilityState().isBbkVisible())
                .putBoolean(PREF_KEY_DWD_VISIBLE, settings.getLayerVisibilityState().isDwdVisible())
                .putBoolean(PREF_KEY_RADAR_VISIBLE, settings.getLayerVisibilityState().isRadarVisible())
                .putInt(PREF_KEY_RADAR_TRANSPARENCY, clampTransparency(settings.getRadarTransparencyPercent()))
                .apply();
    }

    private int clampTransparency(int transparencyPercent) {
        return Math.max(0, Math.min(100, transparencyPercent));
    }
}
