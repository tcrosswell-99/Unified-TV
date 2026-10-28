package io.unifiedtv.preview;

/**
 * Spec 4.8: Home's mood follows the time of day without ever switching palettes. The app's own light stays the colour
 * source; only a faint wash over it changes: none by morning and day, a slight warm dimming at dusk, deeper and softer at
 * night. Phases blend over 20 minutes, so nothing ever flashes at a boundary. Fixed times on the household's clock (its
 * time zone setting, HouseholdSettings) are used because no authorised sunrise/sunset provider is configured (spec 4.8
 * fallback).
 *
 * Also spec 2/14 OLED care for the Sony: after two minutes without a key, Home dims gently; any key restores it at once.
 */
final class DayMood {
    private DayMood() {}
    /** Phase starts in minutes after midnight: morning 06:00, day 10:00, dusk 17:00, night 21:00. */
    static final int MORNING = 6 * 60, DAY = 10 * 60, DUSK = 17 * 60, NIGHT = 21 * 60, BLEND = 20;
    /** {colour, alpha 0-255} of the wash for each phase. */
    private static final int[][] WASH = {
            {0x000000, 0},      // morning: crisp, nothing added
            {0x000000, 0},      // day: clearest
            {0x2A1406, 34},     // dusk: slight warmth, slightly dimmer
            {0x05070C, 66},     // night: deeper charcoal, softer
    };

    private static int phase(int minute) {
        if (minute >= NIGHT || minute < MORNING) return 3;
        if (minute >= DUSK) return 2;
        if (minute >= DAY) return 1;
        return 0;
    }
    private static int start(int phase) { return new int[]{MORNING, DAY, DUSK, NIGHT}[phase]; }

    /** The wash as ARGB for a minute of the day (0-1439), blended across the 20 minutes after each boundary. */
    static int wash(int minute) {
        minute = ((minute % 1440) + 1440) % 1440;
        int now = phase(minute), before = (now + 3) % 4;
        int since = ((minute - start(now)) % 1440 + 1440) % 1440;
        float t = Math.min(1f, since / (float) BLEND);
        return mix(WASH[before], WASH[now], t);
    }
    private static int mix(int[] a, int[] b, float t) {
        int alpha = Math.round(a[1] + (b[1] - a[1]) * t);
        int r = Math.round(((a[0] >> 16) & 255) + ((((b[0] >> 16) & 255) - ((a[0] >> 16) & 255)) * t));
        int g = Math.round(((a[0] >> 8) & 255) + ((((b[0] >> 8) & 255) - ((a[0] >> 8) & 255)) * t));
        int bl = Math.round((a[0] & 255) + (((b[0] & 255) - (a[0] & 255)) * t));
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    /** OLED care: no dimming for the first two minutes idle, then a 10-second fade to at most 45 % darker. */
    static final long OLED_AFTER_MS = 120_000, OLED_FADE_MS = 10_000;
    static float oledDim(long idleMs) {
        if (idleMs <= OLED_AFTER_MS) return 0;
        return .45f * Math.min(1f, (idleMs - OLED_AFTER_MS) / (float) OLED_FADE_MS);
    }
    /** A 2-pixel orbit every three minutes, so no edge sits on the same pixels for hours (OLED). */
    static int[] shift(long wallClockMs) {
        int step = (int) ((wallClockMs / 180_000L) % 4);
        return new int[][]{{0, 0}, {2, 0}, {2, 2}, {0, 2}}[step];
    }
}
