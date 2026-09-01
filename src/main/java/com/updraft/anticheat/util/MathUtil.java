package com.updraft.anticheat.util;

/**
 * Small math helpers used by movement and combat checks.
 * Kept dependency-free so they can be unit-tested in isolation.
 */
public final class MathUtil {

    private MathUtil() {}

    public static double square(double v) { return v * v; }

    public static double sqrt(double v) { return Math.sqrt(v); }

    public static double distance2d(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distance3d(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double distanceSquared2d(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    public static double distanceSquared3d(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    /** Linear interpolation. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Wraps an angle in degrees into {@code [-180, 180)}. */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    /** Smallest absolute difference between two angles (degrees), accounting for wrap. */
    public static double angleDelta(double a, double b) {
        return Math.abs(wrapDegrees(a - b));
    }

    /** Mean of a primitive array. */
    public static double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /** Sample standard deviation of a primitive array. */
    public static double stddev(double[] values) {
        if (values.length < 2) return 0.0;
        double mean = mean(values);
        double acc = 0;
        for (double v : values) acc += (v - mean) * (v - mean);
        return Math.sqrt(acc / (values.length - 1));
    }

    /** Greatest common divisor of a long array via Euclid. */
    public static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a < 0 ? -a : a;
    }

    /** Convert milliseconds-since-epoch to seconds (integer-safe). */
    public static long toSeconds(long ms) {
        return ms / 1000L;
    }
}
