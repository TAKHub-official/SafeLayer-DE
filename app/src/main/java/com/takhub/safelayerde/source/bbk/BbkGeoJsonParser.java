package com.takhub.safelayerde.source.bbk;

import com.takhub.safelayerde.domain.model.GeometryConfidence;
import com.takhub.safelayerde.domain.model.GeometryKind;
import com.takhub.safelayerde.domain.model.WarningGeometry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BbkGeoJsonParser {

    public WarningGeometry parse(String geoJsonString) {
        if (geoJsonString == null || geoJsonString.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject feature = new JSONObject(geoJsonString);
            JSONObject geometryJson = feature.optJSONObject("geometry");
            if (geometryJson == null) {
                return null;
            }

            String type = geometryJson.optString("type", "");
            JSONArray coordinates = geometryJson.optJSONArray("coordinates");
            if (coordinates == null) {
                return null;
            }

            WarningGeometry geometry = new WarningGeometry();
            geometry.setGeoJsonGeometry(geometryJson.toString());
            geometry.setGeometrySource("BBK_GEOJSON");
            geometry.setGeometryConfidence(GeometryConfidence.CONFIRMED);
            geometry.setApproximate(false);

            if ("Polygon".equals(type)) {
                geometry.setKind(GeometryKind.POLYGON);
                double[] centroid = centroidForRing(coordinates.optJSONArray(0));
                geometry.setCentroidLat(centroid[0]);
                geometry.setCentroidLon(centroid[1]);
                geometry.setBbox(feature.optJSONArray("bbox") != null
                        ? bboxFromArray(feature.optJSONArray("bbox"))
                        : bboxFromPolygonCoordinates(coordinates));
                return geometry;
            }

            if ("MultiPolygon".equals(type)) {
                geometry.setKind(GeometryKind.MULTI_POLYGON);
                double[] centroid = centroidForMultiPolygon(coordinates);
                geometry.setCentroidLat(centroid[0]);
                geometry.setCentroidLon(centroid[1]);
                geometry.setBbox(feature.optJSONArray("bbox") != null
                        ? bboxFromArray(feature.optJSONArray("bbox"))
                        : bboxFromMultiPolygonCoordinates(coordinates));
                return geometry;
            }
        } catch (JSONException ignored) {
            return null;
        }

        return null;
    }

    private double[] centroidForMultiPolygon(JSONArray polygons) {
        double latTotal = 0D;
        double lonTotal = 0D;
        int count = 0;
        for (int polygonIndex = 0; polygonIndex < polygons.length(); polygonIndex++) {
            JSONArray polygon = polygons.optJSONArray(polygonIndex);
            if (polygon == null) {
                continue;
            }
            double[] centroid = centroidForRing(polygon.optJSONArray(0));
            if (centroid[0] == 0D && centroid[1] == 0D) {
                continue;
            }
            latTotal += centroid[0];
            lonTotal += centroid[1];
            count++;
        }
        return count == 0 ? new double[] {0D, 0D} : new double[] {latTotal / count, lonTotal / count};
    }

    private double[] centroidForRing(JSONArray ring) {
        if (ring == null || ring.length() == 0) {
            return new double[] {0D, 0D};
        }

        double latTotal = 0D;
        double lonTotal = 0D;
        int count = 0;
        for (int pointIndex = 0; pointIndex < ring.length(); pointIndex++) {
            JSONArray point = ring.optJSONArray(pointIndex);
            if (point == null || point.length() < 2) {
                continue;
            }
            lonTotal += point.optDouble(0, 0D);
            latTotal += point.optDouble(1, 0D);
            count++;
        }

        return count == 0 ? new double[] {0D, 0D} : new double[] {latTotal / count, lonTotal / count};
    }

    private double[] bboxFromArray(JSONArray bboxArray) {
        double[] bbox = new double[4];
        for (int index = 0; index < bbox.length && index < bboxArray.length(); index++) {
            bbox[index] = bboxArray.optDouble(index, 0D);
        }
        return bbox;
    }

    private double[] bboxFromPolygonCoordinates(JSONArray coordinates) {
        return accumulateBbox(new double[] {
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
        }, coordinates.optJSONArray(0));
    }

    private double[] bboxFromMultiPolygonCoordinates(JSONArray polygons) {
        double[] bbox = new double[] {
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        for (int polygonIndex = 0; polygonIndex < polygons.length(); polygonIndex++) {
            JSONArray polygon = polygons.optJSONArray(polygonIndex);
            if (polygon == null) {
                continue;
            }
            bbox = accumulateBbox(bbox, polygon.optJSONArray(0));
        }
        return sanitizeBbox(bbox);
    }

    private double[] accumulateBbox(double[] bbox, JSONArray ring) {
        if (ring == null) {
            return sanitizeBbox(bbox);
        }

        for (int pointIndex = 0; pointIndex < ring.length(); pointIndex++) {
            JSONArray point = ring.optJSONArray(pointIndex);
            if (point == null || point.length() < 2) {
                continue;
            }
            double lon = point.optDouble(0, 0D);
            double lat = point.optDouble(1, 0D);
            bbox[0] = Math.min(bbox[0], lon);
            bbox[1] = Math.min(bbox[1], lat);
            bbox[2] = Math.max(bbox[2], lon);
            bbox[3] = Math.max(bbox[3], lat);
        }
        return sanitizeBbox(bbox);
    }

    private double[] sanitizeBbox(double[] bbox) {
        if (Double.isInfinite(bbox[0]) || Double.isInfinite(bbox[1])
                || Double.isInfinite(bbox[2]) || Double.isInfinite(bbox[3])) {
            return new double[4];
        }
        return bbox;
    }
}
