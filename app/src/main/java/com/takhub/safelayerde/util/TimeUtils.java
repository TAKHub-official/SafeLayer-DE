package com.takhub.safelayerde.util;

public final class TimeUtils {

    public static final long ONE_MINUTE_MS = 60000L;
    public static final long ONE_HOUR_MS = 60L * ONE_MINUTE_MS;
    public static final long TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L;
    private static final long TEN_MINUTES_MS = 10L * ONE_MINUTE_MS;
    private static final long THIRTY_MINUTES_MS = 30L * ONE_MINUTE_MS;

    private TimeUtils() {
    }

    public static long nowEpochMs() {
        return System.currentTimeMillis();
    }

    public static long ageMinutes(long epochMs) {
        return ageMinutes(epochMs, nowEpochMs());
    }

    public static long ageMinutes(long epochMs, long nowEpochMs) {
        if (epochMs <= 0L) {
            return 0L;
        }

        return Math.max(0L, ageMillis(epochMs, nowEpochMs) / ONE_MINUTE_MS);
    }

    public static long ageMillis(long epochMs, long nowEpochMs) {
        if (epochMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowEpochMs - epochMs);
    }

    public static boolean isLessThanOneMinuteOld(long epochMs) {
        return isLessThanOneMinuteOld(epochMs, nowEpochMs());
    }

    public static boolean isLessThanOneMinuteOld(long epochMs, long nowEpochMs) {
        return epochMs > 0L && ageMillis(epochMs, nowEpochMs) < ONE_MINUTE_MS;
    }

    public static boolean expiredMoreThan(long epochMs, long thresholdMs) {
        return expiredMoreThan(epochMs, nowEpochMs(), thresholdMs);
    }

    public static boolean expiredMoreThan(long epochMs, long nowEpochMs, long thresholdMs) {
        if (epochMs <= 0L || thresholdMs < 0L) {
            return false;
        }
        return nowEpochMs - epochMs > thresholdMs;
    }

    public static RelativeDataAgeBucket relativeDataAgeBucket(long epochMs) {
        return relativeDataAgeBucket(epochMs, nowEpochMs());
    }

    public static RelativeDataAgeBucket relativeDataAgeBucket(long epochMs, long nowEpochMs) {
        return relativeDataAgeBucketForAgeMillis(ageMillis(epochMs, nowEpochMs));
    }

    public static RelativeDataAgeBucket relativeDataAgeBucketForAgeMillis(long ageMillis) {
        long normalizedAgeMillis = Math.max(0L, ageMillis);
        if (normalizedAgeMillis < TEN_MINUTES_MS) {
            return RelativeDataAgeBucket.justNow();
        }
        if (normalizedAgeMillis < THIRTY_MINUTES_MS) {
            return RelativeDataAgeBucket.underThirtyMinutes();
        }
        if (normalizedAgeMillis < ONE_HOUR_MS) {
            return RelativeDataAgeBucket.underOneHour();
        }
        if (normalizedAgeMillis >= TWELVE_HOURS_MS) {
            return RelativeDataAgeBucket.overTwelveHours();
        }

        int upperBoundHours = (int) (normalizedAgeMillis / ONE_HOUR_MS) + 1;
        return RelativeDataAgeBucket.underHours(upperBoundHours);
    }

    public static final class RelativeDataAgeBucket {

        public enum Type {
            JUST_NOW,
            UNDER_THIRTY_MINUTES,
            UNDER_ONE_HOUR,
            UNDER_HOURS,
            OVER_TWELVE_HOURS
        }

        private final Type type;
        private final int upperBoundHours;

        private RelativeDataAgeBucket(Type type, int upperBoundHours) {
            this.type = type;
            this.upperBoundHours = upperBoundHours;
        }

        public static RelativeDataAgeBucket justNow() {
            return new RelativeDataAgeBucket(Type.JUST_NOW, 0);
        }

        public static RelativeDataAgeBucket underThirtyMinutes() {
            return new RelativeDataAgeBucket(Type.UNDER_THIRTY_MINUTES, 0);
        }

        public static RelativeDataAgeBucket underOneHour() {
            return new RelativeDataAgeBucket(Type.UNDER_ONE_HOUR, 1);
        }

        public static RelativeDataAgeBucket underHours(int upperBoundHours) {
            return new RelativeDataAgeBucket(Type.UNDER_HOURS, Math.max(2, upperBoundHours));
        }

        public static RelativeDataAgeBucket overTwelveHours() {
            return new RelativeDataAgeBucket(Type.OVER_TWELVE_HOURS, 12);
        }

        public Type getType() {
            return type;
        }

        public int getUpperBoundHours() {
            return upperBoundHours;
        }
    }
}
