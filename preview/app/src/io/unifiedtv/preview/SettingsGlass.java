package io.unifiedtv.preview;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

/**
 * Module 13 §A tokens as Android drawables: the Settings glass sheet, raised cards, focus rows, monochrome category
 * glyphs, On/Off pills and status chips. Original Unified TV shapes, inspired by (not copied from) Liquid Glass.
 *
 * <p>Blur, honestly: an app can't sample or blur another activity's pixels on Android 11 (TCL), and on API 31+
 * {@code RenderEffect} blurs only a view's own content, never what is behind its window. So the sheet is (1) a static,
 * screen-aligned frost of Unified TV's own ambient mesh, drawn once per scene from a small cached bitmap, blurred with
 * {@code RenderEffect} only on API 31+ when lightweight rendering is off, (2) a dark blue-charcoal tint of about 75–80%
 * combined opacity, through which Home stays faintly visible, (3) a soft top sheen, inner rim and a hairline ice-blue left
 * edge, and (4) a low shadow separating it from Home. Reduce transparency and High contrast drop the frost and make the
 * tint nearly solid. Nothing here animates by itself.
 */
final class SettingsGlass {
    private SettingsGlass() {}
    static final int TEXT = 0xFFF4F7FB, SECONDARY = 0xFFC3CEDD, GROUP = 0xFF93A5BC, FOCUS_FILL = 0xFFE6EFFC, FOCUS_TEXT = 0xFF0D1522, EDGE = 0x8CDCEBFF;
    static final int[] FOCUSED = {android.R.attr.state_focused}, PRESSED = {android.R.attr.state_pressed};

    static android.content.res.ColorStateList textColours() {
        return new android.content.res.ColorStateList(new int[][]{FOCUSED, PRESSED, {}}, new int[]{FOCUS_TEXT, FOCUS_TEXT, TEXT});
    }
    static boolean focused(int[] state) { for (int s : state) if (s == android.R.attr.state_focused || s == android.R.attr.state_pressed) return true; return false; }
    static boolean checked(int[] state) { for (int s : state) if (s == android.R.attr.state_checked) return true; return false; }

    /** A row or button: barely raised at rest; focused, a pale illuminated fill with a crisp white border (not colour alone), crossfading in fadeMs (0 with reduced motion). */
    static Drawable row(float radius, int stroke, boolean strong, int fadeMs) {
        StateListDrawable list = new StateListDrawable(); list.setEnterFadeDuration(fadeMs); list.setExitFadeDuration(fadeMs);
        GradientDrawable focused = new GradientDrawable(); focused.setColor(FOCUS_FILL); focused.setCornerRadius(radius); focused.setStroke(stroke, 0xFFFFFFFF);
        GradientDrawable normal = new GradientDrawable(); normal.setColor(strong ? 0x24FFFFFF : 0x12FFFFFF); normal.setCornerRadius(radius);
        if (strong) normal.setStroke(Math.max(1, stroke / 2), 0x80FFFFFF);
        list.addState(FOCUSED, focused); list.addState(PRESSED, focused); list.addState(new int[]{}, normal);
        return list;
    }
    /** A raised translucent card behind a group of controls: a local scrim that keeps dense text readable. */
    static Drawable card(float radius, boolean strong) {
        GradientDrawable d = new GradientDrawable(); d.setColor(strong ? 0x1FFFFFFF : 0x14FFFFFF); d.setCornerRadius(radius); d.setStroke(1, strong ? 0x59FFFFFF : 0x1AFFFFFF);
        return d;
    }

    /** The sheet: shadow strip on the left, then the tinted glass with rounded left corners, sheen, rim and edge light. */
    static final class Sheet extends Drawable {
        private final float shadow, radius; private final boolean solid;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path(), edge = new Path();
        private Shader shade, tint, sheen, rim;
        Sheet(float shadow, float radius, boolean solid) { this.shadow = shadow; this.radius = radius; this.solid = solid; line.setStyle(Paint.Style.STROKE); }
        static void outline(Path p, float l, float t, float r, float b, float radius) {
            p.reset(); p.addRoundRect(new RectF(l, t, r, b), new float[]{radius, radius, 0, 0, 0, 0, radius, radius}, Path.Direction.CW);
        }
        @Override protected void onBoundsChange(Rect b) {
            float l = b.left + shadow;
            outline(shape, l, b.top, b.right, b.bottom, radius);
            edge.reset(); edge.moveTo(l + radius, b.top); edge.arcTo(new RectF(l, b.top, l + 2 * radius, b.top + 2 * radius), 270, -90);
            edge.lineTo(l, b.bottom - radius); edge.arcTo(new RectF(l, b.bottom - 2 * radius, l + 2 * radius, b.bottom), 180, -90);
            shade = new LinearGradient(b.left, 0, l, 0, 0x00000000, 0x4D000000, Shader.TileMode.CLAMP);
            tint = new LinearGradient(0, b.top, 0, b.bottom, solid ? 0xF2111A28 : 0x9E121B2A, solid ? 0xF50B1220 : 0xB00B1220, Shader.TileMode.CLAMP);
            sheen = new LinearGradient(0, b.top, 0, b.top + b.height() * .28f, solid ? 0x08FFFFFF : 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            rim = new LinearGradient(l, 0, b.right, 0, new int[]{0x00FFFFFF, 0x52FFFFFF, 0x0DFFFFFF}, null, Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); if (b.isEmpty()) return; float l = b.left + shadow;
            fill.setShader(shade); c.drawRect(b.left, b.top, l, b.bottom, fill);
            fill.setShader(tint); c.drawPath(shape, fill);
            fill.setShader(sheen); c.save(); c.clipPath(shape); c.drawRect(l, b.top, b.right, b.top + b.height() * .28f, fill);
            fill.setShader(rim); c.drawRect(l, b.top + 1, b.right, b.top + 2.5f, fill); c.restore(); fill.setShader(null);
            line.setStrokeWidth(Math.max(3, shadow / 6)); line.setColor(solid ? 0x00000000 : 0x1A9CC8FF); c.drawPath(edge, line);
            line.setStrokeWidth(solid ? 2.5f : 1.5f); line.setColor(solid ? 0xE6FFFFFF : EDGE); c.drawPath(edge, line);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Layer (1): a still, softened frame of Home's own ambient mesh for the current scene, aligned to the screen so its light
     * matches what's behind the sheet. Not live Home pixels (no app can read those); never animated.
     */
    static final class Frost extends View {
        private static final java.util.Map<String, Bitmap> stills = new java.util.HashMap<>();
        private final String scene; private final boolean sharp; private final float shadow, radius; private final int screenWidth;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG); private final Path clip = new Path(); private final RectF place = new RectF();
        Frost(Context c, String scene, boolean sharp, float shadow, float radius, int screenWidth) {
            super(c); this.scene = scene; this.sharp = sharp; this.shadow = shadow; this.radius = radius; this.screenWidth = screenWidth;
            setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); paint.setAlpha(90);
        }
        private static synchronized Bitmap still(String scene, boolean sharp) {
            String key = scene + (sharp ? "@hi" : "@lo"); Bitmap b = stills.get(key); if (b != null) return b;
            int w = sharp ? 384 : 160, h = sharp ? 216 : 90;   // the small one is soft on its own once scaled up
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); AmbientMesh.still(new Canvas(b), w, h, scene);
            if (stills.size() > 6) stills.clear(); stills.put(key, b); return b;
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { Sheet.outline(clip, shadow, 0, w, h, radius); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight(); if (w == 0 || h == 0) return;
            c.save(); c.clipPath(clip); place.set(w - Math.max(w, screenWidth), 0, w, h); c.drawBitmap(still(scene, sharp), null, place, paint); c.restore();
        }
    }

    /** Monochrome category and header glyphs on a 24-unit grid; dark on a focused (pale) row, light otherwise. */
    static final class Glyph extends Drawable {
        static final int BACK = 100, CLOSE = 101;
        private final int kind, size; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private boolean dark;
        Glyph(int kind, int size) { this.kind = kind; this.size = size; p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); setBounds(0, 0, size, size); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { boolean d = focused(state); if (d == dark) return false; dark = d; invalidateSelf(); return true; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); c.save(); c.translate(b.left, b.top); c.scale(b.width() / 24f, b.height() / 24f);
            p.setColor(dark ? FOCUS_TEXT : TEXT); p.setStrokeWidth(1.8f); p.setStyle(Paint.Style.STROKE);
            switch (kind) {
                case SettingsActivity.APPEARANCE: c.drawCircle(12, 12, 8.5f, p); dot(c, 8.5f, 9.5f); dot(c, 12.5f, 7.5f); dot(c, 15.5f, 11); c.drawCircle(10.5f, 15.5f, 1.8f, p); break;
                case SettingsActivity.LIBRARY: c.drawRoundRect(4, 5, 20, 19, 2, 2, p); c.drawLine(8, 5, 8, 19, p); c.drawLine(16, 5, 16, 19, p); c.drawLine(4, 12, 8, 12, p); c.drawLine(16, 12, 20, 12, p); break;
                case SettingsActivity.PROFILES: c.drawCircle(9, 8.5f, 3.2f, p); c.drawArc(3, 14, 15, 24, 180, 180, false, p); c.drawCircle(16.5f, 9.5f, 2.6f, p); c.drawArc(13.5f, 15, 21.5f, 22.5f, 200, 160, false, p); break;
                case SettingsActivity.APPS: for (int i = 0; i < 4; i++) { float x = i % 2 == 0 ? 4.5f : 13.5f, y = i < 2 ? 4.5f : 13.5f; c.drawRoundRect(x, y, x + 6, y + 6, 1.6f, 1.6f, p); } break;
                case SettingsActivity.NOTIFICATIONS: { Path bell = new Path(); bell.moveTo(6, 17); bell.lineTo(6, 11); bell.cubicTo(6, 7, 8.5f, 5, 12, 5); bell.cubicTo(15.5f, 5, 18, 7, 18, 11); bell.lineTo(18, 17); bell.close(); c.drawPath(bell, p); c.drawLine(4.5f, 17, 19.5f, 17, p); c.drawArc(10, 17.5f, 14, 21, 0, 180, false, p); break; }
                case SettingsActivity.SMART_HOME: { Path h = new Path(); h.moveTo(4, 11.5f); h.lineTo(12, 4.5f); h.lineTo(20, 11.5f); c.drawPath(h, p); c.drawRect(6.5f, 10, 17.5f, 19.5f, p); c.drawRect(10.5f, 14, 13.5f, 19.5f, p); break; }
                case SettingsActivity.LIVE_TV: { c.drawRoundRect(3, 5, 21, 17, 2, 2, p); c.drawLine(9, 20, 15, 20, p); Path s = new Path(); s.moveTo(12, 7.5f); s.lineTo(15.5f, 9); s.cubicTo(15.5f, 12.5f, 14, 14, 12, 15); s.cubicTo(10, 14, 8.5f, 12.5f, 8.5f, 9); s.close(); c.drawPath(s, p); break; }
                case SettingsActivity.CONNECTIONS: { Path r = new Path(); r.moveTo(3.5f, 9); r.lineTo(3.5f, 6); r.lineTo(20.5f, 6); r.lineTo(20.5f, 18); r.lineTo(13, 18); c.drawPath(r, p); c.drawArc(-3, 12.5f, 10, 25.5f, 270, 90, false, p); c.drawArc(0.5f, 16, 6.5f, 22, 270, 90, false, p); dot(c, 4, 19.5f); break; }
                case SettingsActivity.PRIVACY: c.drawRoundRect(5, 11, 19, 20, 2, 2, p); c.drawArc(8, 4, 16, 14, 180, 180, false, p); c.drawLine(8, 9, 8, 11, p); c.drawLine(16, 9, 16, 11, p); c.drawLine(12, 14.5f, 12, 16.5f, p); break;
                case SettingsActivity.ABOUT: c.drawCircle(12, 12, 8.5f, p); c.drawLine(12, 11, 12, 16.5f, p); dot(c, 12, 7.8f); break;
                case BACK: c.drawLine(14.5f, 5.5f, 8, 12, p); c.drawLine(8, 12, 14.5f, 18.5f, p); break;
                case CLOSE: c.drawLine(7, 7, 17, 17, p); c.drawLine(17, 7, 7, 17, p); break;
                default: break;
            }
            c.restore();
        }
        private void dot(Canvas c, float x, float y) { p.setStyle(Paint.Style.FILL); c.drawCircle(x, y, 1.3f, p); p.setStyle(Paint.Style.STROKE); }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * The right-hand end of a row: an optional small status chip (only for a state Unified TV has actually read), then a
     * chevron for a row that opens a page; or, for a switch, an On/Off pill whose word doesn't depend on colour.
     */
    static final class Trailing extends Drawable {
        private final String chip; private final boolean chevron, toggle; private final float text, height;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private int[] state = new int[0]; private final int width;
        Trailing(String chip, boolean chevron, boolean toggle, float textPx) {
            this.chip = chip == null || chip.isEmpty() ? null : chip; this.chevron = chevron; this.toggle = toggle; this.text = textPx; this.height = textPx * 1.7f;
            p.setTextSize(textPx); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float w = 0;
            if (toggle) w = Math.max(p.measureText("Off"), p.measureText("On")) + textPx * 1.6f;
            else { if (this.chip != null) w += p.measureText(this.chip) + textPx * 1.2f; if (chevron) w += (w > 0 ? textPx * .7f : 0) + textPx * .7f; }
            width = Math.round(w); setBounds(0, 0, width, Math.round(height));
        }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] s) { state = s.clone(); invalidateSelf(); return true; }
        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return Math.round(height); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); boolean dark = focused(state); int ink = dark ? FOCUS_TEXT : TEXT; float mid = b.exactCenterY(), x = b.left;
            if (toggle) {
                boolean on = checked(state); String word = on ? "On" : "Off"; RectF pill = new RectF(b.left, mid - height / 2.4f, b.right, mid + height / 2.4f);
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, text / 12)); p.setColor(on ? (dark ? FOCUS_TEXT : 0xFFCFE3FF) : ink);
                c.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, p);
                p.setStyle(Paint.Style.FILL); p.setColor(on ? (dark ? TEXT : FOCUS_TEXT) : ink); c.drawText(word, pill.centerX() - p.measureText(word) / 2, mid + text * .36f, p);
                return;
            }
            if (chip != null) {
                float w = p.measureText(chip) + text * 1.2f; RectF pill = new RectF(x, mid - height / 2.6f, x + w, mid + height / 2.6f);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, text / 16)); p.setColor(dark ? 0x99101827 : 0x80DCE6F2); c.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, p);
                p.setStyle(Paint.Style.FILL); p.setColor(dark ? FOCUS_TEXT : SECONDARY); c.drawText(chip, x + text * .6f, mid + text * .36f, p); x += w + text * .7f;
            }
            if (chevron) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(2f, text / 9)); p.setColor(ink);
                float s = text * .32f, cx = b.right - s; c.drawLine(cx - s, mid - s * 1.4f, cx + s * .2f, mid, p); c.drawLine(cx + s * .2f, mid, cx - s, mid + s * 1.4f, p);
            }
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
