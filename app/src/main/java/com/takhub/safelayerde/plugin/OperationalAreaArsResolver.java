package com.takhub.safelayerde.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import com.takhub.safelayerde.platform.MapViewProvider;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class OperationalAreaArsResolver {

    private static final String PREF_NAME = "safelayer.operational_area";
    private static final String PREF_KEY_ARS_SET = "last_ars_set";

    private static final Map<String, RegionDefinition> REGIONS = createRegions();

    private final CoordinateProvider coordinateProvider;
    private final ArsSetStore arsSetStore;

    public OperationalAreaArsResolver(MapViewProvider mapViewProvider, Context pluginContext) {
        this(new MapViewCenterProvider(mapViewProvider), new SharedPreferencesArsSetStore(pluginContext));
    }

    OperationalAreaArsResolver(CoordinateProvider coordinateProvider, ArsSetStore arsSetStore) {
        this.coordinateProvider = coordinateProvider == null ? new EmptyCoordinateProvider() : coordinateProvider;
        this.arsSetStore = arsSetStore == null ? new NoOpArsSetStore() : arsSetStore;
    }

    public Set<String> resolve() {
        Coordinate coordinate = coordinateProvider.get();
        if (coordinate != null) {
            Set<String> resolved = resolveBufferedArsSet(coordinate.latitude, coordinate.longitude);
            if (!resolved.isEmpty()) {
                arsSetStore.write(resolved);
                return resolved;
            }
        }
        return sanitize(arsSetStore.read());
    }

    static Set<String> resolveBufferedArsSet(double latitude, double longitude) {
        RegionDefinition region = findRegion(latitude, longitude);
        if (region == null) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        resolved.add(region.ars);
        for (String neighborArs : region.neighborArs) {
            if (neighborArs != null && !neighborArs.trim().isEmpty()) {
                resolved.add(neighborArs);
            }
        }
        return Collections.unmodifiableSet(resolved);
    }

    private static RegionDefinition findRegion(double latitude, double longitude) {
        for (RegionDefinition region : REGIONS.values()) {
            if (region.contains(latitude, longitude)) {
                return region;
            }
        }
        return null;
    }

    private Set<String> sanitize(Set<String> arsSet) {
        if (arsSet == null || arsSet.isEmpty()) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String ars : arsSet) {
            if (ars != null && !ars.trim().isEmpty()) {
                sanitized.add(ars);
            }
        }
        return Collections.unmodifiableSet(sanitized);
    }

    private static Map<String, RegionDefinition> createRegions() {
        LinkedHashMap<String, RegionDefinition> regions = new LinkedHashMap<>();
        regions.put("HAMBURG", region("020000000000", 53.39D, 53.75D, 9.73D, 10.33D, "010000000000", "030000000000"));
        regions.put("BREMEN", region("040000000000", 53.00D, 53.60D, 8.45D, 9.10D, "030000000000"));
        regions.put("BERLIN", region("110000000000", 52.33D, 52.68D, 13.08D, 13.76D, "120000000000"));
        regions.put("SAARLAND", region("100000000000", 49.10D, 49.65D, 6.35D, 7.45D, "070000000000", "080000000000"));
        regions.put("SCHLESWIG_HOLSTEIN", region("010000000000", 53.30D, 55.10D, 8.30D, 11.30D, "020000000000", "030000000000", "130000000000"));
        regions.put("MECKLENBURG_VORPOMMERN", region("130000000000", 53.10D, 54.75D, 10.40D, 14.45D, "010000000000", "030000000000", "120000000000"));
        regions.put("BRANDENBURG", region("120000000000", 51.30D, 53.60D, 11.30D, 14.75D, "110000000000", "130000000000", "150000000000", "140000000000", "030000000000"));
        regions.put("LOWER_SAXONY", region("030000000000", 51.20D, 53.95D, 6.40D, 11.65D, "010000000000", "020000000000", "040000000000", "050000000000", "060000000000", "120000000000", "130000000000", "150000000000"));
        regions.put("NORTH_RHINE_WESTPHALIA", region("050000000000", 50.30D, 52.60D, 5.85D, 9.55D, "030000000000", "060000000000", "070000000000"));
        regions.put("HESSE", region("060000000000", 49.35D, 51.70D, 7.75D, 10.25D, "030000000000", "050000000000", "070000000000", "080000000000", "090000000000", "150000000000", "160000000000"));
        regions.put("RHINELAND_PALATINATE", region("070000000000", 48.95D, 50.95D, 6.00D, 8.60D, "050000000000", "060000000000", "080000000000", "100000000000"));
        regions.put("BADEN_WUERTTEMBERG", region("080000000000", 47.45D, 49.90D, 7.45D, 10.55D, "070000000000", "060000000000", "090000000000", "100000000000"));
        regions.put("BAVARIA", region("090000000000", 47.20D, 50.60D, 8.95D, 13.90D, "060000000000", "080000000000", "140000000000", "160000000000"));
        regions.put("SAXONY_ANHALT", region("150000000000", 51.00D, 53.10D, 10.50D, 13.30D, "030000000000", "060000000000", "120000000000", "140000000000", "160000000000"));
        regions.put("THURINGIA", region("160000000000", 50.10D, 51.70D, 9.85D, 12.85D, "060000000000", "090000000000", "140000000000", "150000000000"));
        regions.put("SAXONY", region("140000000000", 50.15D, 51.75D, 11.85D, 15.10D, "090000000000", "120000000000", "150000000000", "160000000000"));
        return Collections.unmodifiableMap(regions);
    }

    private static RegionDefinition region(
            String ars,
            double minLat,
            double maxLat,
            double minLon,
            double maxLon,
            String... neighborArs) {
        return new RegionDefinition(ars, minLat, maxLat, minLon, maxLon, Arrays.asList(neighborArs));
    }

    interface CoordinateProvider {
        Coordinate get();
    }

    interface ArsSetStore {
        Set<String> read();

        void write(Set<String> arsSet);
    }

    static final class Coordinate {

        private final double latitude;
        private final double longitude;

        private Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        static Coordinate of(double latitude, double longitude) {
            return new Coordinate(latitude, longitude);
        }
    }

    private static final class RegionDefinition {

        private final String ars;
        private final double minLat;
        private final double maxLat;
        private final double minLon;
        private final double maxLon;
        private final Iterable<String> neighborArs;

        private RegionDefinition(
                String ars,
                double minLat,
                double maxLat,
                double minLon,
                double maxLon,
                Iterable<String> neighborArs) {
            this.ars = ars;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.neighborArs = neighborArs;
        }

        private boolean contains(double latitude, double longitude) {
            return latitude >= minLat
                    && latitude <= maxLat
                    && longitude >= minLon
                    && longitude <= maxLon;
        }
    }

    private static final class EmptyCoordinateProvider implements CoordinateProvider {

        @Override
        public Coordinate get() {
            return null;
        }
    }

    private static final class NoOpArsSetStore implements ArsSetStore {

        @Override
        public Set<String> read() {
            return Collections.emptySet();
        }

        @Override
        public void write(Set<String> arsSet) {
        }
    }

    private static final class SharedPreferencesArsSetStore implements ArsSetStore {

        private final Context context;

        private SharedPreferencesArsSetStore(Context context) {
            this.context = context;
        }

        @Override
        public Set<String> read() {
            if (context == null) {
                return Collections.emptySet();
            }

            SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String joined = preferences.getString(PREF_KEY_ARS_SET, null);
            if (joined == null || joined.trim().isEmpty()) {
                return Collections.emptySet();
            }

            LinkedHashSet<String> arsSet = new LinkedHashSet<>();
            for (String ars : joined.split(",")) {
                if (ars != null && !ars.trim().isEmpty()) {
                    arsSet.add(ars.trim());
                }
            }
            return arsSet;
        }

        @Override
        public void write(Set<String> arsSet) {
            if (context == null || arsSet == null || arsSet.isEmpty()) {
                return;
            }

            SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            preferences.edit().putString(PREF_KEY_ARS_SET, join(arsSet)).apply();
        }

        private String join(Set<String> arsSet) {
            StringBuilder builder = new StringBuilder();
            for (String ars : arsSet) {
                if (ars == null || ars.trim().isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(ars.trim());
            }
            return builder.toString();
        }
    }

    private static final class MapViewCenterProvider implements CoordinateProvider {

        private static final String[] CENTER_METHODS = {
                "getPoint",
                "getCenterPoint",
                "getCenter",
                "getMapCenter",
                "getMapCenterPoint"
        };

        private final MapViewProvider mapViewProvider;

        private MapViewCenterProvider(MapViewProvider mapViewProvider) {
            this.mapViewProvider = mapViewProvider;
        }

        @Override
        public Coordinate get() {
            Object mapView = mapViewProvider == null ? null : mapViewProvider.get();
            if (mapView == null) {
                return null;
            }

            for (String methodName : CENTER_METHODS) {
                Coordinate coordinate = invokeCoordinate(mapView, methodName);
                if (coordinate != null) {
                    return coordinate;
                }
            }
            return null;
        }

        private Coordinate invokeCoordinate(Object target, String methodName) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                return extractCoordinate(value);
            } catch (Exception ignored) {
                return null;
            }
        }

        private Coordinate extractCoordinate(Object value) {
            if (value == null) {
                return null;
            }

            Double latitude = invokeDouble(value, "getLatitude");
            Double longitude = invokeDouble(value, "getLongitude");
            if (latitude != null && longitude != null) {
                return new Coordinate(latitude, longitude);
            }

            try {
                Method method = value.getClass().getMethod("get");
                return extractCoordinate(method.invoke(value));
            } catch (Exception ignored) {
                return null;
            }
        }

        private Double invokeDouble(Object target, String methodName) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                if (value != null) {
                    return Double.parseDouble(String.valueOf(value));
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
    }
}
