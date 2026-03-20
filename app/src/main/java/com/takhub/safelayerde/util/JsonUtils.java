package com.takhub.safelayerde.util;

import org.json.JSONObject;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String optString(JSONObject object, String key, String defaultValue) {
        if (object == null || key == null || object.isNull(key)) {
            return defaultValue;
        }

        Object value = object.opt(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public static long optLong(JSONObject object, String key, long defaultValue) {
        if (object == null || key == null || object.isNull(key)) {
            return defaultValue;
        }

        Object value = object.opt(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static boolean optBoolean(JSONObject object, String key, boolean defaultValue) {
        if (object == null || key == null || object.isNull(key)) {
            return defaultValue;
        }

        Object value = object.opt(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    public static double optDouble(JSONObject object, String key, double defaultValue) {
        if (object == null || key == null || object.isNull(key)) {
            return defaultValue;
        }

        Object value = object.opt(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
