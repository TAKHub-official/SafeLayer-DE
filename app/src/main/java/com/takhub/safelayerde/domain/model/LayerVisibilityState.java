package com.takhub.safelayerde.domain.model;

public class LayerVisibilityState {

    private final boolean bbkVisible;
    private final boolean dwdVisible;
    private final boolean radarVisible;

    public LayerVisibilityState(boolean bbkVisible, boolean dwdVisible, boolean radarVisible) {
        this.bbkVisible = bbkVisible;
        this.dwdVisible = dwdVisible;
        this.radarVisible = radarVisible;
    }

    public boolean isBbkVisible() {
        return bbkVisible;
    }

    public boolean isDwdVisible() {
        return dwdVisible;
    }

    public boolean isRadarVisible() {
        return radarVisible;
    }
}
