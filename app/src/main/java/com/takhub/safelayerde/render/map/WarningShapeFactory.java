package com.takhub.safelayerde.render.map;

import android.util.Log;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.SeverityColorPolicy;
import com.takhub.safelayerde.render.model.WarningRenderSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;

public class WarningShapeFactory {

    private static final String TAG = "SafeLayerShape";

    private final MapView mapView;
    private final SeverityColorPolicy colorPolicy;

    public WarningShapeFactory(MapView mapView, SeverityColorPolicy colorPolicy) {
        this.mapView = mapView;
        this.colorPolicy = colorPolicy;
    }

    public List<MapItem> createShapes(MapGroup mapGroup, WarningRenderSpec spec) {
        List<GeoPoint[]> outerRings = extractOuterRings(spec == null ? null : spec.getGeoJsonGeometry());
        List<MapItem> shapes = new ArrayList<>();
        if (mapView == null || mapGroup == null) {
            Log.w(TAG, "Cannot create DrawingShape without mapView/mapGroup.");
            return shapes;
        }
        for (int index = 0; index < outerRings.size(); index++) {
            GeoPoint[] outerRing = outerRings.get(index);
            if (outerRing.length == 0) {
                continue;
            }

            DrawingShape shape = instantiateShape(mapGroup, spec.getStableId() + "#" + index);
            if (shape == null) {
                continue;
            }

            setPoints(shape, outerRing);
            MapItemCompat.setTitle(shape, spec.getTitle());
            MapItemCompat.setMetaString(shape, "stableId", spec.getStableId());
            MapItemCompat.setMetaString(shape, "sourceType",
                    spec.getSourceType() == null ? null : spec.getSourceType().name());
            MapItemCompat.setClickable(shape, true);
            int lineColor = strokeColorFor(spec);
            int fillColor = fillColorFor(spec);
            if (!MapItemCompat.setStrokeColor(shape, lineColor)) {
                MapItemCompat.setColor(shape, lineColor);
            }
            MapItemCompat.setFillColor(shape, fillColor);
            shapes.add(shape);
        }
        return shapes;
    }

    private DrawingShape instantiateShape(MapGroup mapGroup, String stableId) {
        try {
            Constructor<DrawingShape> constructor =
                    DrawingShape.class.getConstructor(MapView.class, MapGroup.class, String.class);
            return constructor.newInstance(mapView, mapGroup, stableId);
        } catch (Exception ignored) {
            try {
                Constructor<DrawingShape> constructor =
                        DrawingShape.class.getConstructor(MapView.class, String.class);
                return constructor.newInstance(mapView, stableId);
            } catch (Exception exception) {
                Log.e(TAG, "Failed to instantiate DrawingShape.", exception);
                return null;
            }
        }
    }

    private void setPoints(DrawingShape shape, GeoPoint[] outerRing) {
        if (!MapItemCompat.setPoints(shape, outerRing)) {
            return;
        }
        MapItemCompat.setClosed(shape, true);
    }

    private List<GeoPoint[]> extractOuterRings(String geoJsonGeometry) {
        List<GeoPoint[]> outerRings = new ArrayList<>();
        if (geoJsonGeometry == null || geoJsonGeometry.trim().isEmpty()) {
            return outerRings;
        }

        try {
            JSONObject geometry = new JSONObject(geoJsonGeometry);
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null) {
                return outerRings;
            }

            if ("Polygon".equals(type)) {
                GeoPoint[] polygon = toGeoPoints(coordinates.optJSONArray(0));
                if (polygon.length > 0) {
                    outerRings.add(polygon);
                }
                return outerRings;
            }
            if ("MultiPolygon".equals(type)) {
                for (int polygonIndex = 0; polygonIndex < coordinates.length(); polygonIndex++) {
                    JSONArray polygon = coordinates.optJSONArray(polygonIndex);
                    GeoPoint[] ring = polygon == null ? new GeoPoint[0] : toGeoPoints(polygon.optJSONArray(0));
                    if (ring.length > 0) {
                        outerRings.add(ring);
                    }
                }
                return outerRings;
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Failed to parse warning geometry.", exception);
        }

        return outerRings;
    }

    private GeoPoint[] toGeoPoints(JSONArray ring) {
        if (ring == null) {
            return new GeoPoint[0];
        }

        List<GeoPoint> points = new ArrayList<>();
        for (int index = 0; index < ring.length(); index++) {
            JSONArray coordinate = ring.optJSONArray(index);
            double lon = coordinate == null ? 0D : coordinate.optDouble(0, 0D);
            double lat = coordinate == null ? 0D : coordinate.optDouble(1, 0D);
            GeoPoint point = createGeoPoint(lat, lon);
            if (point != null) {
                points.add(point);
            }
        }
        return points.toArray(new GeoPoint[0]);
    }

    private GeoPoint createGeoPoint(double lat, double lon) {
        return new GeoPoint(lat, lon);
    }

    public int strokeColorFor(WarningRenderSpec spec) {
        int strokeColor = colorPolicy.colorForSeverity(spec == null ? null : spec.getSeverity());
        if (spec != null && spec.getSourceType() == WarningSourceType.DWD && spec.isApproximate()) {
            return (strokeColor & 0x00FFFFFF) | 0x99000000;
        }
        return strokeColor;
    }

    public int fillColorFor(WarningRenderSpec spec) {
        int fillColor = colorPolicy.fillColorForSeverity(spec == null ? null : spec.getSeverity());
        if (spec != null && spec.getSourceType() == WarningSourceType.DWD && spec.isApproximate()) {
            return (fillColor & 0x00FFFFFF) | 0x22000000;
        }
        return fillColor;
    }
}
