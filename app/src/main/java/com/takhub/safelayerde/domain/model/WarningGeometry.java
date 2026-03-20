package com.takhub.safelayerde.domain.model;

public class WarningGeometry {

    private GeometryKind kind = GeometryKind.NONE;
    private String geoJsonGeometry;
    private double centroidLat;
    private double centroidLon;
    private double[] bbox = new double[4];
    private boolean approximate;
    private String geometrySource;
    private GeometryConfidence geometryConfidence = GeometryConfidence.NONE;

    public GeometryKind getKind() {
        return kind;
    }

    public void setKind(GeometryKind kind) {
        this.kind = kind == null ? GeometryKind.NONE : kind;
    }

    public String getGeoJsonGeometry() {
        return geoJsonGeometry;
    }

    public void setGeoJsonGeometry(String geoJsonGeometry) {
        this.geoJsonGeometry = geoJsonGeometry;
    }

    public double getCentroidLat() {
        return centroidLat;
    }

    public void setCentroidLat(double centroidLat) {
        this.centroidLat = centroidLat;
    }

    public double getCentroidLon() {
        return centroidLon;
    }

    public void setCentroidLon(double centroidLon) {
        this.centroidLon = centroidLon;
    }

    public double[] getBbox() {
        return bbox.clone();
    }

    public void setBbox(double[] bbox) {
        if (bbox == null || bbox.length < 4) {
            this.bbox = new double[4];
            return;
        }

        this.bbox = bbox.clone();
    }

    public boolean isApproximate() {
        return approximate;
    }

    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    public String getGeometrySource() {
        return geometrySource;
    }

    public void setGeometrySource(String geometrySource) {
        this.geometrySource = geometrySource;
    }

    public GeometryConfidence getGeometryConfidence() {
        return geometryConfidence;
    }

    public void setGeometryConfidence(GeometryConfidence geometryConfidence) {
        this.geometryConfidence = geometryConfidence == null
                ? GeometryConfidence.NONE
                : geometryConfidence;
    }
}
