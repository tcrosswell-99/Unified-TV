package io.unifiedtv.preview;

import android.graphics.*;
import java.util.*;

/**
 * Spec 32: each Home app's own light, as soft radial fields over a dark base, like coloured light behind frosted glass.
 * Palettes are declarative and follow the app's identity, never its dock slot. Drift is slow (20 to 40 s paths, scale 1.00
 * to 1.06) and never touches focus or input; each light travels {@link #TRAVEL} times its authored amplitude (see there).
 * Rendering is a handful of pre-rendered bitmaps composited by the GPU (see draw()). A still render of the same palette
 * backs the glass and the lightweight mode.
 */
final class AmbientMesh {
    /** One light field: centre and radius as fractions of the screen width/height, colour, and its slow drift path. */
    static final class Light {
        final float x, y, r; final int colour; final float period, phase, ax, ay;
        Light(float x, float y, float r, int colour, float period, float phase, float ax, float ay) {
            this.x = x; this.y = y; this.r = r; this.colour = colour; this.period = period; this.phase = phase; this.ax = ax; this.ay = ay;
        }
    }
    static final class Palette {
        final int base, accent; final Light[] lights;
        Palette(int base, int accent, Light... lights) { this.base = base; this.accent = accent; this.lights = lights; }
    }

    private static final Map<String, Palette> PALETTES = new HashMap<>();
    // Drift amplitudes sit at the upper end of the agreed 2 to 5 % (owner, TCL, 25 October 2026: "nothing moves"): these
    // lights are large and very soft, so a shift of 2 to 3 % of the screen over half a minute was below what the eye
    // notices from the sofa. samsung/src/palettes.json carries the same numbers (samsung/test/palettes.test.js).
    static {
        // Crimson from the lower left, a softer band rising to the upper right; charcoal elsewhere.
        PALETTES.put("smartutube", new Palette(0xFF15171C, 0xFFE0303C,
                new Light(.16f, .80f, .42f, 0xD8D42230, 31, 0f, .05f, .03f),
                new Light(.38f, .56f, .30f, 0x8CA01C2A, 37, 1.7f, .04f, .05f),
                new Light(.70f, .22f, .34f, 0x96B0202E, 27, 3.1f, .05f, .03f)));
        // Deliberately the darkest: a low red glow in the centre and a heavy vignette.
        PALETTES.put("netflix", new Palette(0xFF050203, 0xFFE50914,
                new Light(.42f, .44f, .40f, 0xC0800A10, 34, 0f, .03f, .03f),
                new Light(.66f, .30f, .28f, 0x7A60080E, 26, 2.2f, .05f, .04f)));
        // Navy and indigo, with a cyan bloom rising from behind the dock.
        PALETTES.put("disney", new Palette(0xFF080E2C, 0xFF2D6BE0,
                new Light(.32f, .34f, .40f, 0xC02A3FB8, 33, 0f, .05f, .03f),
                new Light(.66f, .28f, .38f, 0xB03A6AD8, 29, 2.4f, .04f, .05f),
                new Light(.50f, .48f, .30f, 0x7A5A40C8, 38, 4.0f, .03f, .03f),
                new Light(.36f, .70f, .22f, 0x9C50D0F0, 23, 1.1f, .03f, .02f)));
        // Magenta and deep pink, kept dark enough for white type.
        PALETTES.put("bbc", new Palette(0xFF1A0A14, 0xFFD6336F,
                new Light(.46f, .18f, .42f, 0xC0B83672, 32, 0f, .05f, .03f),
                new Light(.30f, .60f, .32f, 0xB4A8103F, 27, 2.0f, .04f, .05f),
                new Light(.76f, .40f, .32f, 0xA0C04478, 36, 3.6f, .05f, .03f)));
        // Restrained violet pools with calm space for the protection sheet.
        PALETTES.put("live", new Palette(0xFF100E26, 0xFF8A5CF0,
                new Light(.45f, .46f, .40f, 0xA05A1AA0, 35, 0f, .04f, .03f),
                new Light(.80f, .66f, .32f, 0x905828C0, 28, 2.6f, .05f, .04f),
                new Light(.20f, .30f, .28f, 0x66302080, 39, 4.4f, .03f, .03f)));
        // The warmest: peach, coral and pink flowing together, with dark edges kept.
        PALETTES.put("music", new Palette(0xFF181216, 0xFFF0507A,
                new Light(.24f, .44f, .32f, 0xB0E07E62, 30, 0f, .05f, .05f),
                new Light(.76f, .34f, .32f, 0xA8E58C68, 26, 1.9f, .05f, .05f),
                new Light(.50f, .62f, .36f, 0xB4E04A72, 34, 3.3f, .05f, .03f),
                new Light(.56f, .86f, .26f, 0x96F05062, 22, 5.0f, .04f, .025f)));
        PALETTES.put("xbox", new Palette(0xFF07120A, 0xFF2CA243,
                new Light(.36f, .40f, .40f, 0xA0167C2C, 33, 0f, .05f, .03f),
                new Light(.72f, .62f, .30f, 0x80309A48, 27, 2.5f, .04f, .05f)));
        PALETTES.put("tvplus", new Palette(0xFF07101E, 0xFF3D7FE0,
                new Light(.40f, .40f, .40f, 0xA0244E9C, 32, 0f, .05f, .03f),
                new Light(.72f, .60f, .30f, 0x80306CC0, 28, 2.2f, .04f, .05f)));
    }
    /** Any other app: a quiet slate light that keeps every label readable (spec 32.2 neutral fallback). */
    private static final Palette NEUTRAL = new Palette(0xFF0C121B, 0xFF7C93AE,
            new Light(.40f, .38f, .40f, 0x8C3E536C, 34, 0f, .04f, .03f),
            new Light(.74f, .64f, .30f, 0x704A5E78, 28, 2.4f, .05f, .04f));

    /**
     * SET-022 Ambient background: the light a Home app shows is named by a "light id": the app's own id (its palette), or
     * {@link #NEUTRAL_LIGHT} when the household chose a neutral colour, with {@link #SUBTLE} added for the subtle intensity.
     * Home saves the light id as the "scene" every glass screen reads, so Settings, Ask your TV and the library carry the
     * same light. Anything that isn't a known app (including "neutral") gets the quiet neutral palette.
     */
    static final String NEUTRAL_LIGHT = "~neutral", SUBTLE = "~subtle";
    /** Settings keys and values (SettingsSchema): intensity standard/subtle, colour follow the focused app/neutral. */
    static final String INTENSITY = "ambientIntensity", STANDARD = "standard", SUBTLE_VALUE = "subtle";
    static final String COLOUR = "ambientColour", FOLLOW = "follow", NEUTRAL_VALUE = "neutral";
    static final String[] INTENSITIES = {STANDARD, SUBTLE_VALUE}, COLOURS = {FOLLOW, NEUTRAL_VALUE};
    /** The subtle intensity: every light at this share of its opacity (the dark base and vignette stay). */
    static final float SUBTLE_STRENGTH = .6f;
    static String intensityLabel(String v) { return SUBTLE_VALUE.equals(v) ? "Subtle" : "Standard"; }
    static String colourLabel(String v) { return NEUTRAL_VALUE.equals(v) ? "Neutral" : "Follow the app in focus"; }
    static String lightId(String app, boolean neutral, boolean subtle) { String base = neutral ? NEUTRAL_LIGHT : app; return subtle ? base + SUBTLE : base; }
    static String lightId(String app, android.content.SharedPreferences p) {
        return lightId(app, NEUTRAL_VALUE.equals(p.getString(COLOUR, FOLLOW)), SUBTLE_VALUE.equals(p.getString(INTENSITY, STANDARD)));
    }
    /** How strongly the lights of this light id shine (1, or {@link #SUBTLE_STRENGTH}). */
    static float strength(String id) { return id != null && id.endsWith(SUBTLE) ? SUBTLE_STRENGTH : 1f; }
    private static String base(String id) { return id != null && id.endsWith(SUBTLE) ? id.substring(0, id.length() - SUBTLE.length()) : id; }
    static Palette palette(String id) { Palette p = PALETTES.get(base(id)); return p == null ? NEUTRAL : p; }
    static boolean known(String id) { return PALETTES.containsKey(base(id)); }
    static int accent(String id) { return palette(id).accent; }
    /** A light's colour at this strength (its alpha scaled). */
    static int colour(Light l, float strength) {
        if (strength >= 1f) return l.colour;
        int a = Math.round(((l.colour >>> 24) & 255) * strength);
        return (a << 24) | (l.colour & 0xFFFFFF);
    }

    /**
     * Owner, TCL, 25 October 2026 (after 469eca5 and c62956e): "the light blobs still don't move at all". Each light is
     * very large and soft (up to 1,600 px across on a 1080p screen), so the 2 to 5 % shift of spec 7.4 changed the
     * brightness at any point by only a few per cent over a quarter of a minute, which the eye does not notice. The
     * authored paths (amplitudes, 20 to 40 s periods, phases) stay exactly as palettes.json shares them with the Frame;
     * Android moves each light this many times further, so a light travels up to a fifth of the screen width and back
     * over 20 to 40 seconds: visible from the sofa, still slow and calm (at most about 90 px a second at 1080p).
     */
    static final float TRAVEL = 4f;

    /** Where a light sits at time t (seconds): a slow, smooth figure that never resets or jumps. */
    static float[] position(Light l, double seconds) { return position(l, seconds, new float[3]); }
    /** The same, written into {@code out} (x, y as fractions of the screen; scale), so a step allocates nothing. */
    static float[] position(Light l, double seconds, float[] out) {
        double a = 2 * Math.PI * seconds / l.period + l.phase;
        out[0] = (float) (l.x + TRAVEL * l.ax * Math.sin(a));
        out[1] = (float) (l.y + TRAVEL * l.ay * Math.sin(a * 0.77 + 1.3));
        out[2] = (float) (1.0 + 0.03 * (1 + Math.sin(a * 0.61 + 0.4)));   // scale 1.00 to 1.06
        return out;
    }
    /** A light at rest (drift off): its home position, at the middle of its scale range. */
    static float[] rest(Light l, float[] out) { out[0] = l.x; out[1] = l.y; out[2] = 1.03f; return out; }

    // Pre-rendered layers, composited by the GPU each frame (spec 32.4, owner instruction 25 October 2026): one soft
    // white light sprite, tinted per light by a cached colour filter and moved/scaled as a bitmap, and one small
    // vignette image stretched over the screen. Nothing full-screen is repainted as a gradient, nothing is allocated.
    private static Bitmap sprite, shade;
    private final Map<Integer, ColorFilter> tints = new HashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF dst = new RectF();

    /**
     * A white light with a long, feathered falloff, so it reads as blurred without a blur pass. Built once, at the size the
     * TV's MemoryBudget allows (512 px, or 256 px on a 2 GB TV: magnified six times or more either way, so it looks the same).
     */
    static synchronized Bitmap sprite() {
        if (sprite == null) {
            int size = MemoryBudget.current().spriteSize();
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG); float h = size / 2f;
            p.setShader(new RadialGradient(h, h, h, new int[]{0xFFFFFFFF, 0x94FFFFFF, 0x2EFFFFFF, 0x00FFFFFF}, new float[]{0, .38f, .72f, 1}, Shader.TileMode.CLAMP));
            new Canvas(b).drawCircle(h, h, h, p);
            sprite = b;
        }
        return sprite;
    }
    /** Darkened corners and a band behind the clock and status, as one small image. Built once. */
    static synchronized Bitmap shade() {
        if (shade == null) {
            int w = 384, h = 216; Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setShader(new RadialGradient(w * .5f, h * .5f, w * .72f, new int[]{0, 0, 0x99000000}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new LinearGradient(0, 0, 0, h * .24f, 0x66000000, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * .24f, p);
            shade = b;
        }
        return shade;
    }
    /**
     * Memory is short and Home's light isn't on screen: forget the shared sprite and vignette (views still showing them keep
     * their own reference; the next light built makes them again).
     */
    static synchronized void trim() { sprite = null; shade = null; }
    private final float[] at = new float[3];
    private ColorFilter tint(int colour) {
        ColorFilter f = tints.get(colour);
        if (f == null) { f = new PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN); tints.put(colour, f); }
        return f;
    }

    /** Draws one app's mesh at the given opacity. With drift off, the lights rest at their home positions. */
    void draw(Canvas c, String id, float w, float h, int alpha, double seconds, boolean drift) {
        Palette p = palette(id);
        paint.setColorFilter(null); paint.setColor(p.base); paint.setAlpha(alpha);
        c.drawRect(0, 0, w, h, paint);
        Bitmap light = sprite(); float strength = strength(id);
        for (Light l : p.lights) {
            if (drift) position(l, seconds, at); else rest(l, at);
            float cx = at[0] * w, cy = at[1] * h, r = l.r * w * at[2];
            dst.set(cx - r, cy - r, cx + r, cy + r);
            paint.setColorFilter(tint(colour(l, strength))); paint.setAlpha(alpha);
            c.drawBitmap(light, null, dst, paint);
        }
        paint.setColorFilter(null); paint.setAlpha(255);
    }

    /** Darkens the corners and the band behind the clock and status, once per frame over whatever mesh is showing. */
    void protect(Canvas c, float w, float h) {
        dst.set(0, 0, w, h); paint.setColorFilter(null); paint.setAlpha(255);
        c.drawBitmap(shade(), null, dst, paint);
    }

    /** A still frame of the palette, for the cached glass and the lightweight mode. */
    static void still(Canvas c, float w, float h, String id) {
        AmbientMesh mesh = new AmbientMesh();
        mesh.draw(c, id, w, h, 255, 0, false);
        mesh.protect(c, w, h);
    }
}
