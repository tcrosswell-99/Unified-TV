package io.unifiedtv.preview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import io.nayuki.qrcodegen.QrCode;

/**
 * A QR code drawn crisply: whole-pixel modules (no scaling or anti-aliasing blur), black on white with the standard
 * four-module quiet zone, centred in the view. Encoded by Project Nayuki's QR Code generator (MIT, vendored unchanged
 * under io.nayuki.qrcodegen; see preview/THIRD_PARTY.md).
 */
final class QrView extends View {
    static final int QUIET = 4;
    private QrCode qr;
    private final Paint dark = new Paint(), light = new Paint();

    QrView(Context c) {
        super(c);
        dark.setAntiAlias(false); dark.setColor(0xff000000); dark.setStyle(Paint.Style.FILL);
        light.setAntiAlias(false); light.setColor(0xffffffff); light.setStyle(Paint.Style.FILL);
        setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Encodes the text (medium error correction, raised when it fits); null clears the view. */
    void setText(String text, String description) {
        qr = text == null ? null : QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        setContentDescription(description);
        invalidate();
    }
    /** The encoded symbol's width in modules, or 0 (tests). */
    int modules() { return qr == null ? 0 : qr.size; }
    boolean dark(int x, int y) { return qr != null && qr.getModule(x, y); }

    /** The largest whole-pixel module size that fits the symbol and quiet zone in {@code side} pixels (at least 1). */
    static int moduleSize(int side, int modules) { return Math.max(1, side / (modules + 2 * QUIET)); }

    @Override protected void onDraw(Canvas canvas) {
        if (qr == null) return;
        int cells = qr.size + 2 * QUIET, m = moduleSize(Math.min(getWidth(), getHeight()), qr.size), total = m * cells;
        int left = (getWidth() - total) / 2, top = (getHeight() - total) / 2;
        canvas.drawRect(left, top, left + total, top + total, light);
        for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++)
            if (qr.getModule(x, y)) { int px = left + (x + QUIET) * m, py = top + (y + QUIET) * m; canvas.drawRect(px, py, px + m, py + m, dark); }
    }
}
