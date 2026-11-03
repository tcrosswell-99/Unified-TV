package io.unifiedtv.preview;

import java.util.Arrays;
import java.util.Locale;

/**
 * Spec 14 and AM-07: frame pacing from Choreographer frame times, plus the shared percentile rule used for focus
 * latency. Pure arithmetic, so it is unit-tested on the JVM; PerformanceActivity feeds it on the TV. Nothing it
 * produces leaves the TV.
 */
final class FrameStats {
    /** A frame interval above this is janky: two or more 60 Hz frames were missed. */
    static final double JANK_MS = 32.0;
    private long[] intervals = new long[1024];
    private int count;
    private long first = -1, last = -1;

    /** One Choreographer frame time (System.nanoTime base). Out-of-order or repeated times are ignored. */
    void frame(long frameTimeNanos) {
        if (last >= 0 && frameTimeNanos > last) {
            if (count == intervals.length) intervals = Arrays.copyOf(intervals, count * 2);
            intervals[count++] = frameTimeNanos - last;
        }
        if (first < 0) first = frameTimeNanos;
        if (frameTimeNanos > last) last = frameTimeNanos;
    }

    /** Time from the first to the last frame, in seconds. */
    double spanSeconds() { return first < 0 ? 0 : (last - first) / 1e9; }

    /** Number of measured frame intervals. */
    int frames() { return count; }

    double[] intervalsMs() {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = intervals[i] / 1_000_000.0;
        return out;
    }

    double percentileMs(double p) { return percentile(intervalsMs(), p); }
    double medianMs() { return percentileMs(50); }

    double worstMs() {
        long worst = 0;
        for (int i = 0; i < count; i++) worst = Math.max(worst, intervals[i]);
        return worst / 1_000_000.0;
    }

    int janky() {
        int n = 0;
        for (int i = 0; i < count; i++) if (intervals[i] / 1_000_000.0 > JANK_MS) n++;
        return n;
    }

    /** Nearest-rank percentile (p in 0..100) of the values; NaN when there are none. The input is not changed. */
    static double percentile(double[] values, double p) {
        if (values == null || values.length == 0) return Double.NaN;
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(Math.max(0, Math.min(100, p)) / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    static double percentile(long[] values, double p) {
        if (values == null) return Double.NaN;
        double[] d = new double[values.length];
        for (int i = 0; i < values.length; i++) d[i] = values[i];
        return percentile(d, p);
    }

    private static String ms(double v) { return String.format(Locale.ROOT, "%.1f ms", v); }

    /**
     * The report block for the owner. header: app and TV lines (model, Android version, build; no identifiers).
     * inputMs: key-press-to-next-Home-draw samples taken during the run. Memory in kilobytes; negative when unknown.
     */
    static String report(String header, double seconds, FrameStats f, long[] inputMs,
                         long pssBeforeKb, long pssAfterKb, long heapUsedKb, long heapMaxKb) {
        StringBuilder s = new StringBuilder("Home measurement (AM-07, spec section 14)\n");
        if (header != null && !header.isEmpty()) s.append(header.trim()).append('\n');
        s.append(String.format(Locale.ROOT, "Duration: %.1f s\n", seconds));
        if (f == null || f.frames() == 0) s.append("Frames: none were measured. Open Home and try again.\n");
        else {
            s.append("Frames: ").append(f.frames()).append(String.format(Locale.ROOT, " (%.1f per second)\n", seconds > 0 ? f.frames() / seconds : 0));
            s.append("Frame time: median ").append(ms(f.medianMs())).append(", 95th percentile ").append(ms(f.percentileMs(95)))
                    .append(", worst ").append(ms(f.worstMs())).append('\n');
            int jank = f.janky();
            s.append("Janky frames (over ").append((int) JANK_MS).append(" ms): ").append(jank)
                    .append(String.format(Locale.ROOT, " (%.1f%%)\n", 100.0 * jank / f.frames()));
        }
        if (inputMs == null || inputMs.length == 0)
            s.append("Focus: no button presses reached Home during the run. Move along the dock while it measures.\n");
        else {
            s.append(String.format(Locale.ROOT, "Focus (button press to next Home frame): median %.0f ms, 95th percentile %.0f ms, worst %.0f ms (%d %s)\n",
                    percentile(inputMs, 50), percentile(inputMs, 95), percentile(inputMs, 100), inputMs.length, inputMs.length == 1 ? "press" : "presses"));
            if (inputMs.length < 100) s.append("Spec target: 95th percentile under 100 ms over 100 rapid presses. This run had fewer; repeat it or add runs together.\n");
        }
        if (pssAfterKb >= 0) {
            s.append("Memory: app ").append(pssAfterKb / 1024).append(" MB (PSS)");
            if (pssBeforeKb >= 0) s.append(", ").append(pssAfterKb >= pssBeforeKb ? "up " : "down ").append(Math.abs(pssAfterKb - pssBeforeKb) / 1024).append(" MB during the run");
            if (heapMaxKb > 0) s.append("; Java heap ").append(heapUsedKb / 1024).append(" of ").append(heapMaxKb / 1024).append(" MB");
            s.append('\n');
        }
        s.append("Measured inside Unified TV only: its own frames and key handling. Not other apps, HDMI inputs or the network.\n");
        return s.toString();
    }
}
