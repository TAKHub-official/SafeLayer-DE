package com.takhub.safelayerde.render.map;

import android.util.Pair;

import com.atakmap.android.grg.ImageOverlay;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi2;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadarImageOverlayRenderer extends AbstractGLMapItem2 {

    private static final String TAG = "SafeLayerRadar";

    public static final String META_KEY = "safeLayerRadarOverlay";
    static final String FAILURE_TOKEN_META_KEY = META_KEY + ".failureToken";
    static final String FAILURE_FLAG_META_KEY = META_KEY + ".failure";
    static final String FAILURE_MESSAGE_META_KEY = META_KEY + ".failureMessage";
    public static final GLMapItemSpi2 SPI = new GLMapItemSpi2() {
        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> argument) {
            if (argument == null
                    || !(argument.first instanceof MapRenderer)
                    || !(argument.second instanceof ImageOverlay)) {
                return null;
            }

            ImageOverlay overlay = (ImageOverlay) argument.second;
            if (!overlay.getMetaBoolean(META_KEY, false)) {
                return null;
            }
            return new RadarImageOverlayRenderer((MapRenderer) argument.first, overlay);
        }
    };

    private static final double RASTER_DRAW_THRESHOLD_MULTIPLIER = 5D;
    private static final Map<String, FailureListener> FAILURE_LISTENERS =
            new ConcurrentHashMap<>();

    private final ImageOverlay overlay;
    private final FailureState failureState;
    private boolean initialized;
    private GLMapRenderable rasterRenderer;
    private double minRasterGsd = Double.NaN;

    private RadarImageOverlayRenderer(MapRenderer context, ImageOverlay overlay) {
        super(context, overlay, GLMapView.RENDER_PASS_SURFACE);
        this.overlay = overlay;
        this.failureState = new FailureState(new FailureState.Reporter() {
            @Override
            public void onFailure(String stage, Throwable throwable) {
                handleRendererFailure(stage, throwable);
            }
        });
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass())) {
            return;
        }
        if (failureState.isInert()) {
            return;
        }

        try {
            if (!initialized) {
                initialize(view);
            }
            if (failureState.isInert()) {
                return;
            }

            double drawResolution = view.currentPass.drawMapResolution;
            if (!overlay.getMetaBoolean("mbbOnly", false)
                    && drawResolution <= minRasterGsd
                    && rasterRenderer != null) {
                rasterRenderer.draw(view);
            }
        } catch (RuntimeException exception) {
            failureState.fail("draw", exception);
        } catch (LinkageError error) {
            failureState.fail("draw", error);
        }
    }

    @Override
    public void release() {
        releaseRasterRendererSafely("release");
        minRasterGsd = Double.NaN;
        initialized = false;
    }

    @Override
    public void startObserving() {
        if (failureState.isInert()) {
            return;
        }
        try {
            super.startObserving();
            initialized = false;
            overlay.getBounds(bounds);
            dispatchOnBoundsChanged();
        } catch (RuntimeException exception) {
            failureState.fail("startObserving", exception);
        } catch (LinkageError error) {
            failureState.fail("startObserving", error);
        }
    }

    @Override
    public void stopObserving() {
        try {
            super.stopObserving();
        } catch (RuntimeException exception) {
            failureState.fail("stopObserving", exception);
        } catch (LinkageError error) {
            failureState.fail("stopObserving", error);
        } finally {
            release();
        }
    }

    private void initialize(GLMapView view) {
        if (failureState.isInert()) {
            return;
        }
        try {
            DatasetDescriptor layerInfo = overlay.getLayerInfo();
            rasterRenderer = GLMapLayerFactory.create3(view, layerInfo);

            double maxResolution = layerInfo.getMaxResolution(null);
            minRasterGsd = maxResolution * RASTER_DRAW_THRESHOLD_MULTIPLIER;
            initialized = true;
        } catch (RuntimeException exception) {
            failureState.fail("initialize", exception);
        } catch (LinkageError error) {
            failureState.fail("initialize", error);
        }
    }

    private void releaseRasterRendererSafely(String stage) {
        try {
            releaseRasterRendererInternal();
        } catch (RuntimeException exception) {
            failureState.fail(stage, exception);
        } catch (LinkageError error) {
            failureState.fail(stage, error);
        }
    }

    private void releaseRasterRendererInternal() {
        if (rasterRenderer != null) {
            rasterRenderer.release();
            rasterRenderer = null;
        }
    }

    private void handleRendererFailure(String stage, Throwable throwable) {
        minRasterGsd = Double.NaN;
        initialized = false;
        try {
            releaseRasterRendererInternal();
        } catch (RuntimeException releaseException) {
            SafeLayerDebugLog.e(TAG, "radar-custom-renderer-release-failed stage=" + stage, releaseException);
        } catch (LinkageError releaseError) {
            SafeLayerDebugLog.e(TAG, "radar-custom-renderer-release-failed stage=" + stage, releaseError);
        }

        String message = "radar-custom-renderer-failed stage=" + stage;
        SafeLayerDebugLog.e(TAG, message, throwable);
        MapItemCompat.setMetaBoolean(overlay, FAILURE_FLAG_META_KEY, true);
        MapItemCompat.setMetaString(
                overlay,
                FAILURE_MESSAGE_META_KEY,
                buildFailureMessage(stage, throwable));

        String failureToken = MapItemCompat.getMetaString(overlay, FAILURE_TOKEN_META_KEY, null);
        FailureListener failureListener = failureToken == null ? null : FAILURE_LISTENERS.get(failureToken);
        if (failureListener != null) {
            try {
                failureListener.onFailure(buildFailureMessage(stage, throwable), throwable);
            } catch (RuntimeException ignored) {
                SafeLayerDebugLog.w(TAG, "radar-custom-renderer-listener-failed");
            }
        }
    }

    private static String buildFailureMessage(String stage, Throwable throwable) {
        String reason = throwable == null ? null : throwable.getMessage();
        if (reason == null || reason.trim().isEmpty()) {
            reason = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        }
        return "Custom radar renderer failed at " + stage + ": " + reason;
    }

    static String registerFailureListener(Object overlay, FailureListener failureListener) {
        if (overlay == null || failureListener == null) {
            return null;
        }
        String failureToken = UUID.randomUUID().toString();
        FAILURE_LISTENERS.put(failureToken, failureListener);
        MapItemCompat.setMetaString(overlay, FAILURE_TOKEN_META_KEY, failureToken);
        MapItemCompat.setMetaBoolean(overlay, FAILURE_FLAG_META_KEY, false);
        MapItemCompat.setMetaString(overlay, FAILURE_MESSAGE_META_KEY, "");
        return failureToken;
    }

    static void unregisterFailureListener(String failureToken) {
        if (failureToken != null) {
            FAILURE_LISTENERS.remove(failureToken);
        }
    }

    interface FailureListener {
        void onFailure(String message, Throwable throwable);
    }

    static final class FailureState {

        interface Reporter {
            void onFailure(String stage, Throwable throwable);
        }

        private final Reporter reporter;
        private boolean inert;

        FailureState(Reporter reporter) {
            this.reporter = reporter;
        }

        boolean isInert() {
            return inert;
        }

        void fail(String stage, Throwable throwable) {
            if (inert) {
                return;
            }
            inert = true;
            if (reporter != null) {
                reporter.onFailure(stage, throwable);
            }
        }
    }
}
