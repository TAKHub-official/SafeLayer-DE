package com.takhub.safelayerde.render.model;

public final class WarningRenderChangeDetector {

    private WarningRenderChangeDetector() {
    }

    public static boolean shouldRecreate(WarningRenderSpec existing, WarningRenderSpec incoming) {
        if (existing == null || incoming == null) {
            return true;
        }
        if (existing.getRenderMode() != incoming.getRenderMode()) {
            return true;
        }
        if (existing.getSeverity() != incoming.getSeverity()) {
            return true;
        }
        if (existing.isApproximate() != incoming.isApproximate()) {
            return true;
        }
        if (existing.getGeometryConfidence() != incoming.getGeometryConfidence()) {
            return true;
        }
        if (!stringEquals(existing.getGeometrySignature(), incoming.getGeometrySignature())) {
            return true;
        }
        if (existing.getPolygonCount() != incoming.getPolygonCount()) {
            return true;
        }
        if (Double.compare(existing.getCentroidLat(), incoming.getCentroidLat()) != 0) {
            return true;
        }
        return Double.compare(existing.getCentroidLon(), incoming.getCentroidLon()) != 0;
    }

    private static boolean stringEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
