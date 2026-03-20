package com.takhub.safelayerde.domain.model;

public class RadarFrame {

    private String frameId;
    private String productId;
    private String productLabel;
    private long frameEpochMs;
    private long fetchedAtEpochMs;
    private String imageFormat;
    private String imagePath;
    private String requestUrl;
    private String crs;
    private String georeferenceId;
    private double minLatitude;
    private double minLongitude;
    private double maxLatitude;
    private double maxLongitude;
    private int width;
    private int height;
    private byte[] dataBytes = new byte[0];
    private boolean valid;

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        this.frameId = frameId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductLabel() {
        return productLabel;
    }

    public void setProductLabel(String productLabel) {
        this.productLabel = productLabel;
    }

    public long getFrameEpochMs() {
        return frameEpochMs;
    }

    public void setFrameEpochMs(long frameEpochMs) {
        this.frameEpochMs = frameEpochMs;
    }

    public long getFetchedAtEpochMs() {
        return fetchedAtEpochMs;
    }

    public void setFetchedAtEpochMs(long fetchedAtEpochMs) {
        this.fetchedAtEpochMs = fetchedAtEpochMs;
    }

    public String getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(String imageFormat) {
        this.imageFormat = imageFormat;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getCrs() {
        return crs;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }

    public String getGeoreferenceId() {
        return georeferenceId;
    }

    public void setGeoreferenceId(String georeferenceId) {
        this.georeferenceId = georeferenceId;
    }

    public double getMinLatitude() {
        return minLatitude;
    }

    public void setMinLatitude(double minLatitude) {
        this.minLatitude = minLatitude;
    }

    public double getMinLongitude() {
        return minLongitude;
    }

    public void setMinLongitude(double minLongitude) {
        this.minLongitude = minLongitude;
    }

    public double getMaxLatitude() {
        return maxLatitude;
    }

    public void setMaxLatitude(double maxLatitude) {
        this.maxLatitude = maxLatitude;
    }

    public double getMaxLongitude() {
        return maxLongitude;
    }

    public void setMaxLongitude(double maxLongitude) {
        this.maxLongitude = maxLongitude;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public byte[] getDataBytes() {
        return dataBytes.clone();
    }

    public void setDataBytes(byte[] dataBytes) {
        this.dataBytes = dataBytes == null ? new byte[0] : dataBytes.clone();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean hasImageBytes() {
        return dataBytes != null && dataBytes.length > 0;
    }

    public boolean hasRenderableImage() {
        return valid
                && width > 0
                && height > 0
                && imagePath != null
                && !imagePath.trim().isEmpty();
    }
}
