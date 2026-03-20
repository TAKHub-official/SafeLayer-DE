package com.takhub.safelayerde.debug;

import android.util.Log;

public final class SafeLayerDebugLog {

    private static final String DEFAULT_TAG = "SafeLayer";
    private static final String VERBOSE_INFO_PROPERTY = "safelayer.debug.verboseInfo";

    private SafeLayerDebugLog() {
    }

    public static void i(String tag, String message) {
        String safeTag = safeTag(tag);
        String safeMessage = safeMessage(message);
        if (!shouldLogInfo(safeTag, safeMessage)) {
            return;
        }
        try {
            Log.i(safeTag, safeMessage);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    public static void w(String tag, String message) {
        try {
            Log.w(safeTag(tag), safeMessage(message));
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    public static void e(String tag, String message, Throwable throwable) {
        try {
            if (throwable == null) {
                Log.e(safeTag(tag), safeMessage(message));
                return;
            }
            Log.e(safeTag(tag), safeMessage(message), throwable);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    static boolean shouldLogInfo(String tag, String message) {
        if (isVerboseInfoEnabled()) {
            return true;
        }

        if ("SafeLayerPlugin".equals(tag)) {
            return hasPrefix(message, "plugin-bootstrap", "plugin-onStart", "plugin-onStop");
        }
        if ("SafeLayerDeps".equals(tag)) {
            return hasPrefix(message, "services-resolved");
        }
        if ("SafeLayerRuntime".equals(tag)) {
            return hasPrefix(message, "runtime-start", "runtime-stop");
        }
        return false;
    }

    private static boolean isVerboseInfoEnabled() {
        try {
            return Boolean.parseBoolean(System.getProperty(VERBOSE_INFO_PROPERTY, "false"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasPrefix(String message, String... prefixes) {
        if (message == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && message.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String safeTag(String tag) {
        return tag == null ? DEFAULT_TAG : tag;
    }

    private static String safeMessage(String message) {
        return message == null ? "" : message;
    }
}
