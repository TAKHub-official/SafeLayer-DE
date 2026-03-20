package com.takhub.safelayerde.render.map;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.takhub.safelayerde.render.map.RadarLayerFactory.RadarLayerHandle;
import com.takhub.safelayerde.render.model.RadarRenderSpec;

public class RadarRenderController {

    private final MapGroup mapGroup;
    private final RadarLayerFactory layerFactory;
    private RadarLayerHandle activeHandle;
    private RadarRenderSpec activeSpec;
    private boolean desiredVisible = true;
    private int desiredTransparencyPercent;

    public RadarRenderController(MapGroup mapGroup, RadarLayerFactory layerFactory) {
        this.mapGroup = mapGroup;
        this.layerFactory = layerFactory == null ? new RadarLayerFactory() : layerFactory;
    }

    RadarRenderController(RadarLayerFactory layerFactory) {
        this(null, layerFactory);
    }

    public void apply(RadarRenderSpec spec) {
        if (spec != null) {
            desiredVisible = spec.isVisible();
            desiredTransparencyPercent = spec.getTransparencyPercent();
        }
        if (spec == null || !spec.hasRenderableImage()) {
            clear();
            return;
        }

        RadarRenderSpec desiredSpec = spec
                .withVisible(desiredVisible)
                .withTransparencyPercent(desiredTransparencyPercent);
        if (canReuseActiveHandle(desiredSpec)) {
            activeSpec = desiredSpec;
            applyActiveHandleState();
            return;
        }

        RadarLayerHandle newHandle;
        try {
            newHandle = layerFactory.createLayer(desiredSpec);
        } catch (RadarLayerCreationException exception) {
            throw new RadarRenderException(
                    "Unable to render radar frame " + desiredSpec.getFrameId(),
                    exception);
        }
        if (newHandle == null) {
            throw new RadarRenderException(
                    "Unable to render radar frame " + desiredSpec.getFrameId(),
                    new RadarLayerCreationException("Radar layer factory returned no handle."));
        }

        adoptNewHandle(desiredSpec, newHandle);
    }

    public void setLayerVisible(boolean visible) {
        if (desiredVisible == visible) {
            return;
        }
        desiredVisible = visible;
        if (activeSpec == null || activeHandle == null) {
            return;
        }
        activeSpec = activeSpec.withVisible(visible);
        activeHandle.setVisible(visible);
    }

    public void setTransparencyPercent(int transparencyPercent) {
        int clamped = Math.max(0, Math.min(100, transparencyPercent));
        if (desiredTransparencyPercent == clamped) {
            return;
        }

        desiredTransparencyPercent = clamped;
        if (activeSpec == null) {
            if (activeHandle != null) {
                activeHandle.setTransparencyPercent(clamped);
            }
            return;
        }
        apply(activeSpec.withTransparencyPercent(clamped));
    }

    public void clear() {
        detachHandle(activeHandle);
        activeHandle = null;
        activeSpec = null;
    }

    public Object getActiveHandle() {
        return activeHandle == null ? null : activeHandle.rawHandle();
    }

    public boolean hasRendererFailure() {
        return activeHandle != null && activeHandle.hasRendererFailure();
    }

    public String getRendererFailureMessage() {
        if (activeHandle == null) {
            return null;
        }
        return activeHandle.getRendererFailureMessage();
    }

    private boolean canReuseActiveHandle(RadarRenderSpec spec) {
        if (activeHandle == null || spec == null || activeSpec == null) {
            return false;
        }
        if (!spec.getFrameId().equals(activeSpec.getFrameId())) {
            return false;
        }
        return layerFactory.canReuseLayer(activeSpec, spec);
    }

    private void adoptNewHandle(RadarRenderSpec spec, RadarLayerHandle newHandle) {
        RadarLayerHandle previousHandle = activeHandle;
        RadarRenderSpec previousSpec = activeSpec;
        try {
            attachHandle(newHandle);
            newHandle.setVisible(desiredVisible);
            newHandle.setTransparencyPercent(desiredTransparencyPercent);
            activeHandle = newHandle;
            activeSpec = spec;
            detachHandle(previousHandle);
        } catch (RuntimeException exception) {
            detachHandle(newHandle);
            activeHandle = previousHandle;
            activeSpec = previousSpec;
            throw exception;
        }
    }

    private void applyActiveHandleState() {
        if (activeHandle == null) {
            return;
        }
        activeHandle.setVisible(desiredVisible);
        activeHandle.setTransparencyPercent(desiredTransparencyPercent);
    }

    protected void attachHandle(RadarLayerHandle handle) {
        if (handle == null || mapGroup == null || !(handle.rawHandle() instanceof MapItem)) {
            return;
        }
        mapGroup.addItem((MapItem) handle.rawHandle());
    }

    protected void detachHandle(RadarLayerHandle handle) {
        if (handle == null) {
            return;
        }
        if (mapGroup != null && handle.rawHandle() instanceof MapItem) {
            mapGroup.removeItem((MapItem) handle.rawHandle());
        }
        handle.dispose();
    }
}
