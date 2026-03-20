package com.takhub.safelayerde.render.map;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class MapItemCompat {

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_METHODS =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private MapItemCompat() {
    }

    static boolean setTitle(Object target, String title) {
        return invoke(target, "setTitle", new Class<?>[] {String.class}, title);
    }

    static boolean setMetaString(Object target, String key, String value) {
        return invoke(target, "setMetaString", new Class<?>[] {String.class, String.class}, key, value);
    }

    static boolean setMetaBoolean(Object target, String key, boolean value) {
        return invoke(target, "setMetaBoolean", new Class<?>[] {String.class, boolean.class}, key, value);
    }

    static boolean setClickable(Object target, boolean clickable) {
        return invoke(target, "setClickable", new Class<?>[] {boolean.class}, clickable);
    }

    static boolean setColor(Object target, int color) {
        return invoke(target, "setColor", new Class<?>[] {int.class}, color);
    }

    static boolean setIconColor(Object target, int color) {
        return invoke(target, "setIconColor", new Class<?>[] {int.class}, color);
    }

    static boolean setStrokeColor(Object target, int color) {
        return invoke(target, "setStrokeColor", new Class<?>[] {int.class}, color);
    }

    static boolean setFillColor(Object target, int color) {
        return invoke(target, "setFillColor", new Class<?>[] {int.class}, color);
    }

    static boolean setClosed(Object target, boolean closed) {
        return invoke(target, "setClosed", new Class<?>[] {boolean.class}, closed);
    }

    static boolean setPoints(Object target, GeoPoint[] points) {
        if (invoke(target, "setPoints", new Class<?>[] {GeoPoint[].class}, (Object) points)) {
            return true;
        }
        return invoke(target, "setPoints", new Class<?>[] {GeoPoint[].class, boolean.class}, points, true);
    }

    static boolean invoke(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null || parameterTypes == null) {
            return false;
        }

        Method method = resolveMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, args);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String getMetaString(Object target, String key, String defaultValue) {
        Object value = invokeForResult(
                target,
                "getMetaString",
                new Class<?>[] {String.class, String.class},
                key,
                defaultValue);
        return value instanceof String ? (String) value : defaultValue;
    }

    static boolean getMetaBoolean(Object target, String key, boolean defaultValue) {
        Object value = invokeForResult(
                target,
                "getMetaBoolean",
                new Class<?>[] {String.class, boolean.class},
                key,
                defaultValue);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static Object invokeForResult(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null || parameterTypes == null) {
            return null;
        }

        Method method = resolveMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method resolveMethod(
            Class<?> targetClass,
            String methodName,
            Class<?>[] parameterTypes) {
        if (targetClass == null) {
            return null;
        }

        String cacheKey = buildCacheKey(targetClass, methodName, parameterTypes);
        if (MISSING_METHODS.contains(cacheKey)) {
            return null;
        }

        Method cachedMethod = METHOD_CACHE.get(cacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }

        try {
            Method method = targetClass.getMethod(methodName, parameterTypes);
            METHOD_CACHE.put(cacheKey, method);
            return method;
        } catch (Exception ignored) {
            MISSING_METHODS.add(cacheKey);
            return null;
        }
    }

    private static String buildCacheKey(
            Class<?> targetClass,
            String methodName,
            Class<?>[] parameterTypes) {
        return targetClass.getName()
                + '#'
                + methodName
                + '#'
                + Arrays.toString(parameterTypes);
    }
}
