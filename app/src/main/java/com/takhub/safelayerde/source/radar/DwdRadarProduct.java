package com.takhub.safelayerde.source.radar;

import com.takhub.safelayerde.plugin.SafeLayerConstants;

public final class DwdRadarProduct {

    public static final DwdRadarProduct RV = new DwdRadarProduct(
            SafeLayerConstants.RADAR_RV_PRODUCT_ID,
            SafeLayerConstants.RADAR_RV_PRODUCT_LABEL,
            SafeLayerConstants.DWD_WMS_BASE_URL,
            SafeLayerConstants.DWD_WMS_CAPABILITIES_URL,
            SafeLayerConstants.RADAR_RV_LAYER_NAME,
            SafeLayerConstants.RADAR_RV_STYLE_NAME,
            SafeLayerConstants.RADAR_RV_IMAGE_FORMAT,
            SafeLayerConstants.RADAR_RV_CRS,
            SafeLayerConstants.RADAR_RV_GEOREFERENCE_ID,
            1200,
            1100,
            SafeLayerConstants.RADAR_REFRESH_INTERVAL_MS,
            45.68555450439453D,
            1.4656230211257935D,
            56.21059036254883D,
            18.71379280090332D);

    private final String productId;
    private final String productLabel;
    private final String wmsBaseUrl;
    private final String capabilitiesUrl;
    private final String layerName;
    private final String styleName;
    private final String imageFormat;
    private final String crs;
    private final String georeferenceId;
    private final int width;
    private final int height;
    private final long updateIntervalMs;
    private final double minLatitude;
    private final double minLongitude;
    private final double maxLatitude;
    private final double maxLongitude;

    private DwdRadarProduct(
            String productId,
            String productLabel,
            String wmsBaseUrl,
            String capabilitiesUrl,
            String layerName,
            String styleName,
            String imageFormat,
            String crs,
            String georeferenceId,
            int width,
            int height,
            long updateIntervalMs,
            double minLatitude,
            double minLongitude,
            double maxLatitude,
            double maxLongitude) {
        this.productId = productId;
        this.productLabel = productLabel;
        this.wmsBaseUrl = wmsBaseUrl;
        this.capabilitiesUrl = capabilitiesUrl;
        this.layerName = layerName;
        this.styleName = styleName;
        this.imageFormat = imageFormat;
        this.crs = crs;
        this.georeferenceId = georeferenceId;
        this.width = width;
        this.height = height;
        this.updateIntervalMs = updateIntervalMs;
        this.minLatitude = minLatitude;
        this.minLongitude = minLongitude;
        this.maxLatitude = maxLatitude;
        this.maxLongitude = maxLongitude;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductLabel() {
        return productLabel;
    }

    public String getWmsBaseUrl() {
        return wmsBaseUrl;
    }

    public String getCapabilitiesUrl() {
        return capabilitiesUrl;
    }

    public String getLayerName() {
        return layerName;
    }

    public String getStyleName() {
        return styleName;
    }

    public String getImageFormat() {
        return imageFormat;
    }

    public String getCrs() {
        return crs;
    }

    public String getGeoreferenceId() {
        return georeferenceId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getUpdateIntervalMs() {
        return updateIntervalMs;
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

    public String toWmsBbox() {
        return minLatitude + "," + minLongitude + "," + maxLatitude + "," + maxLongitude;
    }

    public String buildFrameId(long frameEpochMs) {
        return productId + ":" + frameEpochMs;
    }
}
