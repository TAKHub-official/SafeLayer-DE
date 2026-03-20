package com.takhub.safelayerde.render.model;

import com.takhub.safelayerde.domain.model.RadarFrame;

public class RadarRenderSpec {

    private final String frameId;
    private final long frameEpochMs;
    private final String productLabel;
    private final String imagePath;
    private final String crs;
    private final String georeferenceId;
    private final double minLatitude;
    private final double minLongitude;
    private final double maxLatitude;
    private final double maxLongitude;
    private final int width;
    private final int height;
    private final boolean visible;
    private final int transparencyPercent;

    public RadarRenderSpec(
            String frameId,
            long frameEpochMs,
            String productLabel,
            String imagePath,
            String crs,
            String georeferenceId,
            double minLatitude,
            double minLongitude,
            double maxLatitude,
            double maxLongitude,
            int width,
            int height,
            boolean visible,
            int transparencyPercent) {
        this.frameId = frameId;
        this.frameEpochMs = frameEpochMs;
        this.productLabel = productLabel;
        this.imagePath = imagePath;
        this.crs = crs;
        this.georeferenceId = georeferenceId;
        this.minLatitude = minLatitude;
        this.minLongitude = minLongitude;
        this.maxLatitude = maxLatitude;
        this.maxLongitude = maxLongitude;
        this.width = width;
        this.height = height;
        this.visible = visible;
        this.transparencyPercent = Math.max(0, Math.min(100, transparencyPercent));
    }

    public static RadarRenderSpec from(RadarFrame radarFrame, boolean visible, int transparencyPercent) {
        if (radarFrame == null) {
            return null;
        }
        return new RadarRenderSpec(
                radarFrame.getFrameId(),
                radarFrame.getFrameEpochMs(),
                radarFrame.getProductLabel(),
                radarFrame.getImagePath(),
                radarFrame.getCrs(),
                radarFrame.getGeoreferenceId(),
                radarFrame.getMinLatitude(),
                radarFrame.getMinLongitude(),
                radarFrame.getMaxLatitude(),
                radarFrame.getMaxLongitude(),
                radarFrame.getWidth(),
                radarFrame.getHeight(),
                visible,
                transparencyPercent);
    }

    public String getFrameId() {
        return frameId;
    }

    public long getFrameEpochMs() {
        return frameEpochMs;
    }

    public String getProductLabel() {
        return productLabel;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getCrs() {
        return crs;
    }

    public String getGeoreferenceId() {
        return georeferenceId;
    }

    public double getMinLatitude() {
        return minLatitude;
    }

    public double getMinLongitude() {
        return minLongitude;
    }

    public double getMaxLatitude() {
        return maxLatitude;
    }

    public double getMaxLongitude() {
        return maxLongitude;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getTransparencyPercent() {
        return transparencyPercent;
    }

    public RadarRenderSpec withVisible(boolean visible) {
        return new RadarRenderSpec(
                frameId,
                frameEpochMs,
                productLabel,
                imagePath,
                crs,
                georeferenceId,
                minLatitude,
                minLongitude,
                maxLatitude,
                maxLongitude,
                width,
                height,
                visible,
                transparencyPercent);
    }

    public RadarRenderSpec withTransparencyPercent(int transparencyPercent) {
        return new RadarRenderSpec(
                frameId,
                frameEpochMs,
                productLabel,
                imagePath,
                crs,
                georeferenceId,
                minLatitude,
                minLongitude,
                maxLatitude,
                maxLongitude,
                width,
                height,
                visible,
                transparencyPercent);
    }

    public boolean hasRenderableImage() {
        return imagePath != null
                && !imagePath.trim().isEmpty()
                && width > 0
                && height > 0
                && frameId != null
                && !frameId.trim().isEmpty()
                && crs != null
                && !crs.trim().isEmpty();
    }
}
