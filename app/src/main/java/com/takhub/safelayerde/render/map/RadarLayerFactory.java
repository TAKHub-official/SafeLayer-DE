package com.takhub.safelayerde.render.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.render.model.RadarRenderSpec;
import com.takhub.safelayerde.util.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

public class RadarLayerFactory {

    private static final String TAG = "SafeLayerRadar";
    private static final int OVERLAY_STROKE_COLOR_TRANSPARENT = 0x00000000;
    private static final double OVERLAY_STROKE_WEIGHT_NONE = 0D;
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final String DATASET_DESCRIPTOR_CLASS =
            "com.atakmap.map.layer.raster.DatasetDescriptor";
    private static final String IMAGE_DATASET_DESCRIPTOR_CLASS =
            "com.atakmap.map.layer.raster.ImageDatasetDescriptor";
    private static final String IMAGE_OVERLAY_CLASS =
            "com.atakmap.android.grg.ImageOverlay";
    private static final String GL_IMAGE_OVERLAY_CLASS =
            "com.atakmap.android.grg.GLImageOverlay";
    private static final String GL_MAP_ITEM_FACTORY_CLASS =
            "com.atakmap.android.maps.graphics.GLMapItemFactory";
    private static final String GL_MAP_ITEM_SPI2_CLASS =
            "com.atakmap.android.maps.graphics.GLMapItemSpi2";
    private static final Object GL_RENDERER_REGISTRATION_LOCK = new Object();
    private static volatile boolean customRadarOverlayRendererRegistered;
    private static volatile boolean glImageOverlayRendererRegistered;
    private final RendererFailureListener rendererFailureListener;

    public RadarLayerFactory() {
        this(null);
    }

    public RadarLayerFactory(RendererFailureListener rendererFailureListener) {
        this.rendererFailureListener = rendererFailureListener;
    }

    public RadarLayerHandle createLayer(RadarRenderSpec spec) {
        if (spec == null || !spec.hasRenderableImage()) {
            return null;
        }

        File sourceImageFile = new File(spec.getImagePath());
        if (!sourceImageFile.isFile()) {
            throw new RadarLayerCreationException("Radar image file missing: " + spec.getImagePath());
        }

        try {
            File imageFile = prepareImageFile(spec, sourceImageFile);
            RendererPath rendererPath = ensureHostRendererRegistered(spec);
            Object datasetDescriptor = createDatasetDescriptor(spec, imageFile);
            Object overlay = createOverlay(spec, datasetDescriptor);
            configureOverlay(spec, overlay, rendererPath);
            RadarLayerHandle handle = createHandle(spec, overlay, rendererPath);
            handle.setTransparencyPercent(spec.getTransparencyPercent());
            handle.setVisible(spec.isVisible());
            return handle;
        } catch (RadarLayerCreationException exception) {
            SafeLayerDebugLog.e(TAG, "radar-layer-create-failed frameId=" + spec.getFrameId(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            SafeLayerDebugLog.e(TAG, "radar-layer-create-failed frameId=" + spec.getFrameId(), exception);
            throw new RadarLayerCreationException(
                    "Unable to create radar overlay for frame " + spec.getFrameId(),
                    exception);
        } catch (LinkageError error) {
            SafeLayerDebugLog.e(TAG, "radar-layer-create-failed frameId=" + spec.getFrameId(), error);
            throw new RadarLayerCreationException(
                    "Unable to create radar overlay for frame " + spec.getFrameId(),
                    error);
        }
    }

    protected RadarLayerHandle createHandle(
            RadarRenderSpec spec,
            Object overlay,
            RendererPath rendererPath) {
        String failureToken = registerRendererFailureListener(overlay, rendererPath);
        return new ImageOverlayHandle(overlay, failureToken);
    }

    boolean canReuseLayer(RadarRenderSpec currentSpec, RadarRenderSpec nextSpec) {
        if (currentSpec == null || nextSpec == null) {
            return false;
        }

        String currentRenderablePath = resolveRenderableImagePath(currentSpec);
        String nextRenderablePath = resolveRenderableImagePath(nextSpec);
        if (currentRenderablePath == null) {
            return nextRenderablePath == null;
        }
        return currentRenderablePath.equals(nextRenderablePath);
    }

    protected RendererPath ensureHostRendererRegistered(RadarRenderSpec spec) {
        synchronized (GL_RENDERER_REGISTRATION_LOCK) {
            try {
                Class<?> spi2Class = resolveClass(GL_MAP_ITEM_SPI2_CLASS);
                Class<?> mapItemFactoryClass = resolveClass(GL_MAP_ITEM_FACTORY_CLASS);
                Method registerMethod = mapItemFactoryClass.getMethod("registerSpi", spi2Class);

                if (!glImageOverlayRendererRegistered) {
                    Class<?> glImageOverlayClass = resolveClass(GL_IMAGE_OVERLAY_CLASS);
                    Object spi = readStaticField(glImageOverlayClass, "SPI2");
                    if (spi == null || !spi2Class.isInstance(spi)) {
                        throw new IllegalStateException("GLImageOverlay SPI2 unavailable.");
                    }

                    registerMethod.invoke(null, spi);
                    glImageOverlayRendererRegistered = true;
                    SafeLayerDebugLog.i(TAG, "radar-layer-glspi-registered");
                }

                if (!shouldUseCustomRenderer(spec)) {
                    return RendererPath.STOCK;
                }

                if (!customRadarOverlayRendererRegistered) {
                    try {
                        registerMethod.invoke(null, RadarImageOverlayRenderer.SPI);
                        customRadarOverlayRendererRegistered = true;
                        SafeLayerDebugLog.i(TAG, "radar-layer-custom-glspi-registered");
                    } catch (ReflectiveOperationException exception) {
                        SafeLayerDebugLog.w(TAG, "radar-layer-custom-glspi-fallback reason="
                                + exception.getClass().getSimpleName());
                        return RendererPath.STOCK;
                    } catch (RuntimeException exception) {
                        SafeLayerDebugLog.w(TAG, "radar-layer-custom-glspi-fallback reason="
                                + exception.getClass().getSimpleName());
                        return RendererPath.STOCK;
                    } catch (LinkageError error) {
                        SafeLayerDebugLog.w(TAG, "radar-layer-custom-glspi-fallback reason="
                                + error.getClass().getSimpleName());
                        return RendererPath.STOCK;
                    }
                }
                return RendererPath.CUSTOM;
            } catch (ReflectiveOperationException exception) {
                throw creationFailure(spec, "register GL image overlay renderer", exception);
            } catch (RuntimeException exception) {
                throw creationFailure(spec, "register GL image overlay renderer", exception);
            } catch (LinkageError error) {
                throw creationFailure(spec, "register GL image overlay renderer", error);
            }
        }
    }

    protected boolean shouldUseCustomRenderer(RadarRenderSpec spec) {
        return true;
    }

    protected Object createDatasetDescriptor(RadarRenderSpec spec, File imageFile) {
        try {
            Class<?> descriptorClass = resolveClass(IMAGE_DATASET_DESCRIPTOR_CLASS);
            Constructor<?> constructor = descriptorClass.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    GeoPoint.class,
                    GeoPoint.class,
                    GeoPoint.class,
                    GeoPoint.class,
                    int.class,
                    boolean.class,
                    File.class,
                    Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    resolveDatasetName(spec, imageFile),
                    resolveDatasetUri(spec, imageFile),
                    "SafeLayerRadar",
                    "native",
                    "radar",
                    spec.getWidth(),
                    spec.getHeight(),
                    4326,
                    new GeoPoint(spec.getMaxLatitude(), spec.getMinLongitude()),
                    new GeoPoint(spec.getMaxLatitude(), spec.getMaxLongitude()),
                    new GeoPoint(spec.getMinLatitude(), spec.getMaxLongitude()),
                    new GeoPoint(spec.getMinLatitude(), spec.getMinLongitude()),
                    0,
                    false,
                    resolveWorkingDirectory(spec, imageFile),
                    Collections.singletonMap("product", spec.getProductLabel()));
        } catch (ReflectiveOperationException exception) {
            throw creationFailure(spec, "create dataset descriptor", exception);
        } catch (LinkageError error) {
            throw creationFailure(spec, "link dataset descriptor", error);
        }
    }

    protected File prepareImageFile(RadarRenderSpec spec, File imageFile) {
        if (spec == null || imageFile == null) {
            return imageFile;
        }

        int transparencyPercent = spec.getTransparencyPercent();
        File transparencyImageFile = resolveRenderableImageFile(spec, imageFile);
        if (transparencyImageFile == null) {
            return imageFile;
        }
        if (transparencyImageFile.equals(imageFile)) {
            return imageFile;
        }
        if (transparencyImageFile.isFile()
                && transparencyImageFile.lastModified() >= imageFile.lastModified()) {
            return transparencyImageFile;
        }

        try {
            writeTransparencyImageFile(imageFile, transparencyImageFile, transparencyPercent);
            return transparencyImageFile;
        } catch (IOException exception) {
            throw new RadarLayerCreationException(
                    "Unable to materialize radar transparency overlay for frame " + spec.getFrameId(),
                    exception);
        }
    }

    private String resolveRenderableImagePath(RadarRenderSpec spec) {
        if (spec == null || spec.getImagePath() == null || spec.getImagePath().trim().isEmpty()) {
            return null;
        }

        File renderableImageFile = resolveRenderableImageFile(spec, new File(spec.getImagePath()));
        return renderableImageFile == null ? null : renderableImageFile.getAbsolutePath();
    }

    private File resolveRenderableImageFile(RadarRenderSpec spec, File imageFile) {
        if (spec == null || imageFile == null) {
            return imageFile;
        }

        int transparencyPercent = spec.getTransparencyPercent();
        if (transparencyPercent <= 0 || !isPngFile(imageFile)) {
            return imageFile;
        }

        File transparencyImageFile = resolveTransparencyImageFile(spec, imageFile);
        return transparencyImageFile == null ? imageFile : transparencyImageFile;
    }

    protected File resolveTransparencyImageFile(RadarRenderSpec spec, File imageFile) {
        if (spec == null || imageFile == null) {
            return null;
        }

        File parent = imageFile.getParentFile();
        if (parent == null) {
            return null;
        }

        String fileName = imageFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return new File(parent, baseName + "-transparency-" + spec.getTransparencyPercent() + ".png");
    }

    protected void writeTransparencyImageFile(
            File sourceImageFile,
            File targetImageFile,
            int transparencyPercent) throws IOException {
        Bitmap sourceBitmap = BitmapFactory.decodeFile(sourceImageFile.getAbsolutePath());
        if (sourceBitmap == null) {
            throw new IOException("Unable to decode radar image for transparency rewrite.");
        }

        Bitmap adjustedBitmap = null;
        try {
            int width = sourceBitmap.getWidth();
            int height = sourceBitmap.getHeight();
            int[] pixels = new int[width * height];
            sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            int targetAlpha = 255 - Math.round((Math.max(0, Math.min(100, transparencyPercent)) / 100f) * 255f);
            for (int index = 0; index < pixels.length; index++) {
                int pixel = pixels[index];
                int alpha = (pixel >>> 24) & 0xff;
                int scaledAlpha = (alpha * targetAlpha + 127) / 255;
                pixels[index] = (scaledAlpha << 24) | (pixel & 0x00ffffff);
            }

            adjustedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            adjustedBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (!adjustedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("Unable to encode transparency-adjusted radar image.");
            }
            IoUtils.atomicWriteBytes(targetImageFile, outputStream.toByteArray());
        } finally {
            if (adjustedBitmap != null) {
                adjustedBitmap.recycle();
            }
            sourceBitmap.recycle();
        }
    }

    protected String resolveDatasetName(RadarRenderSpec spec, File imageFile) {
        if (spec != null && spec.getFrameId() != null && !spec.getFrameId().trim().isEmpty()) {
            return spec.getFrameId();
        }
        return imageFile == null ? null : imageFile.getName();
    }

    protected String resolveDatasetUri(RadarRenderSpec spec, File imageFile) {
        if (imageFile == null) {
            return null;
        }
        return imageFile.getAbsolutePath();
    }

    protected File resolveWorkingDirectory(RadarRenderSpec spec, File imageFile) {
        if (imageFile == null) {
            return null;
        }
        File parent = imageFile.getParentFile();
        return parent != null ? parent : imageFile.getAbsoluteFile().getParentFile();
    }

    protected Object createOverlay(RadarRenderSpec spec, Object datasetDescriptor) {
        try {
            Class<?> datasetDescriptorClass = resolveClass(DATASET_DESCRIPTOR_CLASS);
            Class<?> overlayClass = resolveClass(IMAGE_OVERLAY_CLASS);
            Constructor<?> constructor = overlayClass.getDeclaredConstructor(
                    datasetDescriptorClass,
                    String.class,
                    boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(datasetDescriptor, resolveOverlayIdentifier(spec), false);
        } catch (ReflectiveOperationException exception) {
            throw creationFailure(spec, "create overlay", exception);
        } catch (LinkageError error) {
            throw creationFailure(spec, "link overlay", error);
        }
    }

    protected Class<?> resolveClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    protected Object readStaticField(Class<?> type, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = type.getField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    protected void configureOverlay(RadarRenderSpec spec, Object overlay, RendererPath rendererPath) {
        if (overlay instanceof MapItem) {
            MapItem mapItem = (MapItem) overlay;
            mapItem.setClickable(false);
            mapItem.setMetaString("sourceIdentity", "RADAR");
            mapItem.setMetaString("sourceType", "RADAR");
            mapItem.setMetaString("frameId", spec.getFrameId());
            mapItem.setMetaString("georeferenceId", spec.getGeoreferenceId());
            mapItem.setMetaBoolean(RadarImageOverlayRenderer.META_KEY, rendererPath == RendererPath.CUSTOM);
            mapItem.setMetaBoolean(RadarImageOverlayRenderer.FAILURE_FLAG_META_KEY, false);
            mapItem.setMetaString(RadarImageOverlayRenderer.FAILURE_MESSAGE_META_KEY, "");
        }
        invokeOptionalMethod(
                overlay,
                "setStrokeColor",
                spec,
                new Class<?>[]{int.class},
                OVERLAY_STROKE_COLOR_TRANSPARENT);
        invokeOptionalMethod(
                overlay,
                "setStrokeWeight",
                spec,
                new Class<?>[]{double.class},
                OVERLAY_STROKE_WEIGHT_NONE);
        invokeOptionalMethod(
                overlay,
                "enableMapTouch",
                spec,
                new Class<?>[]{boolean.class},
                Boolean.FALSE);
    }

    protected String resolveOverlayIdentifier(RadarRenderSpec spec) {
        if (spec == null) {
            return "safelayer-radar";
        }

        String productId = sanitizeIdentifierToken(spec.getProductLabel(), "radar");
        if (spec.getFrameEpochMs() > 0L) {
            return "safelayer-radar-" + productId + '-' + spec.getFrameEpochMs();
        }

        String frameId = sanitizeIdentifierToken(spec.getFrameId(), null);
        if (frameId != null) {
            return "safelayer-radar-" + frameId;
        }
        return "safelayer-radar-" + productId;
    }

    private String sanitizeIdentifierToken(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return fallback;
        }

        String sanitized = normalized.replaceAll("[^A-Za-z0-9._-]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static boolean invokeOptionalMethod(
            Object target,
            String methodName,
            RadarRenderSpec spec,
            Class<?>[] parameterTypes,
            Object... arguments) {
        if (target == null || methodName == null) {
            return false;
        }

        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return false;
        }

        try {
            method.setAccessible(true);
            method.invoke(target, arguments);
            return true;
        } catch (IllegalAccessException exception) {
            throw creationFailure(spec, "invoke " + methodName, exception);
        } catch (InvocationTargetException exception) {
            throw creationFailure(spec, "invoke " + methodName, exception);
        } catch (RuntimeException exception) {
            throw creationFailure(spec, "invoke " + methodName, exception);
        } catch (LinkageError error) {
            throw creationFailure(spec, "link " + methodName, error);
        }
    }

    private boolean isPngFile(File imageFile) {
        if (imageFile == null || !imageFile.isFile() || imageFile.length() < PNG_SIGNATURE.length) {
            return false;
        }

        try (FileInputStream inputStream = new FileInputStream(imageFile)) {
            for (byte signatureByte : PNG_SIGNATURE) {
                int actual = inputStream.read();
                if (actual < 0 || ((byte) actual) != signatureByte) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            SafeLayerDebugLog.w(TAG, "radar-layer-png-signature-read-failed imageFile="
                    + imageFile.getName()
                    + ", reason="
                    + exception.getMessage());
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }

        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return type.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static RadarLayerCreationException creationFailure(
            RadarRenderSpec spec,
            String action,
            Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof RadarLayerCreationException) {
            return (RadarLayerCreationException) cause;
        }
        String frameId = spec == null ? "unknown" : spec.getFrameId();
        return new RadarLayerCreationException(
                "Unable to " + action + " for radar frame " + frameId,
                cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) throwable).getTargetException();
            return target == null ? throwable : unwrap(target);
        }
        return throwable;
    }

    protected String registerRendererFailureListener(Object overlay, RendererPath rendererPath) {
        if (rendererPath != RendererPath.CUSTOM) {
            return null;
        }
        if (rendererFailureListener == null) {
            return null;
        }
        return RadarImageOverlayRenderer.registerFailureListener(
                overlay,
                new RadarImageOverlayRenderer.FailureListener() {
                    @Override
                    public void onFailure(String message, Throwable throwable) {
                        rendererFailureListener.onFailure(message, throwable);
                    }
                });
    }

    static void resetRendererRegistrationStateForTest() {
        customRadarOverlayRendererRegistered = false;
        glImageOverlayRendererRegistered = false;
    }

    protected enum RendererPath {
        STOCK,
        CUSTOM
    }

    public interface RadarLayerHandle {
        Object rawHandle();
        void setVisible(boolean visible);
        void setTransparencyPercent(int transparencyPercent);
        boolean hasRendererFailure();
        String getRendererFailureMessage();
        void dispose();
    }

    public interface RendererFailureListener {
        void onFailure(String message, Throwable throwable);
    }

    private static final class ImageOverlayHandle implements RadarLayerHandle {

        private final Object overlay;
        private final String failureToken;

        private ImageOverlayHandle(Object overlay, String failureToken) {
            this.overlay = overlay;
            this.failureToken = failureToken;
        }

        @Override
        public Object rawHandle() {
            return overlay;
        }

        @Override
        public void setVisible(boolean visible) {
            if (overlay instanceof MapItem) {
                ((MapItem) overlay).setVisible(visible);
            }
        }

        @Override
        public void setTransparencyPercent(int transparencyPercent) {
            int clamped = Math.max(0, Math.min(100, transparencyPercent));
            int alpha = 255 - Math.round((clamped / 100f) * 255f);
            RadarRenderSpec spec = null;
            invokeOptionalMethod(overlay, "setFillAlpha", spec, new Class<?>[]{int.class}, alpha);
            if (!invokeOptionalMethod(
                    overlay,
                    "setColor",
                    spec,
                    new Class<?>[]{int.class, boolean.class},
                    (alpha << 24) | 0x00ffffff,
                    Boolean.TRUE)) {
                invokeOptionalMethod(
                        overlay,
                        "setColor",
                        spec,
                    new Class<?>[]{int.class},
                    (alpha << 24) | 0x00ffffff);
            }
        }

        @Override
        public boolean hasRendererFailure() {
            return MapItemCompat.getMetaBoolean(
                    overlay,
                    RadarImageOverlayRenderer.FAILURE_FLAG_META_KEY,
                    false);
        }

        @Override
        public String getRendererFailureMessage() {
            return MapItemCompat.getMetaString(
                    overlay,
                    RadarImageOverlayRenderer.FAILURE_MESSAGE_META_KEY,
                    null);
        }

        @Override
        public void dispose() {
            RadarImageOverlayRenderer.unregisterFailureListener(failureToken);
            if (overlay instanceof MapItem) {
                ((MapItem) overlay).removeFromGroup();
            }
        }
    }
}
