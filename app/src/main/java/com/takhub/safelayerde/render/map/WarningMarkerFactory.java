package com.takhub.safelayerde.render.map;

import android.util.Log;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.SeverityColorPolicy;
import com.takhub.safelayerde.render.model.WarningRenderSpec;

import java.lang.reflect.Constructor;

public class WarningMarkerFactory {

    private static final String TAG = "SafeLayerMarker";

    private final SeverityColorPolicy colorPolicy;

    public WarningMarkerFactory(SeverityColorPolicy colorPolicy) {
        this.colorPolicy = colorPolicy;
    }

    public Marker createMarker(WarningRenderSpec spec) {
        if (spec == null || !spec.hasRenderableLocation()) {
            return null;
        }

        GeoPoint point = createGeoPoint(spec.getCentroidLat(), spec.getCentroidLon());
        if (point == null) {
            return null;
        }
        Marker marker = instantiateMarker(point, spec.getStableId());
        if (marker == null) {
            return null;
        }

        MapItemCompat.setTitle(marker, spec.getTitle());
        MapItemCompat.setMetaString(marker, "stableId", spec.getStableId());
        MapItemCompat.setMetaString(marker, "sourceType",
                spec.getSourceType() == null ? null : spec.getSourceType().name());
        MapItemCompat.setClickable(marker, true);
        int color = colorFor(spec);
        if (!MapItemCompat.setColor(marker, color)) {
            MapItemCompat.setIconColor(marker, color);
        }
        return marker;
    }

    public int colorFor(WarningRenderSpec spec) {
        int baseColor = colorPolicy.colorForSeverity(spec == null ? null : spec.getSeverity());
        if (spec != null && spec.getSourceType() == WarningSourceType.DWD && spec.isApproximate()) {
            return (baseColor & 0x00FFFFFF) | 0xCC000000;
        }
        return baseColor;
    }

    private GeoPoint createGeoPoint(double lat, double lon) {
        return new GeoPoint(lat, lon);
    }

    private Marker instantiateMarker(GeoPoint point, String stableId) {
        try {
            Constructor<Marker> constructor = Marker.class.getConstructor(GeoPoint.class, String.class);
            return constructor.newInstance(point, stableId);
        } catch (Exception ignored) {
            try {
                Constructor<Marker> constructor = Marker.class.getConstructor(String.class);
                Marker marker = constructor.newInstance(stableId);
                if (point != null) {
                    MapItemCompat.invoke(marker, "setPoint", new Class<?>[] {GeoPoint.class}, point);
                }
                return marker;
            } catch (Exception ignoredAgain) {
                try {
                    Constructor<Marker> constructor = Marker.class.getConstructor();
                    Marker marker = constructor.newInstance();
                    if (point != null) {
                        MapItemCompat.invoke(marker, "setPoint", new Class<?>[] {GeoPoint.class}, point);
                    }
                    return marker;
                } catch (Exception exception) {
                    Log.e(TAG, "Failed to instantiate Marker.", exception);
                    return null;
                }
            }
        }
    }

}
