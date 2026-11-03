package io.unifiedtv.preview;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * Spec 6.4, RC-05, HRC-09, HRC-01 and OD-06: "Check your remote's buttons". Every key that reaches Unified TV is
 * listed live with its name, keycode, scancode and how long it was held. Volume, mute, power, input and the voice key
 * are recorded and still passed on to Android. Nothing is sent anywhere; the summary stays on the TV.
 * Hold OK for two seconds, or press Back twice, to finish.
 */
/* OD-19: a setup screen, so the glass sidebar over a still Home (GlassScreen). */
public final class RemoteCheckActivity extends GlassScreen {
    static final String REPORT_FILE = "remote-check.txt";
    private final RemoteLog log = new RemoteLog();
    private boolean logging = true, swallowOkUp;
    private TextView last, list, hint;

    /** Spec 6.1: hardware key logging only in owner-authorised debug builds, such as the review build (.devicecheck). */
    static boolean allowed(Context c) { return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        autoFocusButtons = false;
        page("Check your remote's buttons");
        if (!allowed(this)) {
            logging = false;
            text("This check is in the Unified TV review build only, so that remote buttons are never recorded in an everyday build.", 20);
            button("Back", this::finish).requestFocus();
            return;
        }
        text("Press each button on your remote. Hold OK to finish.", 24);
        text("Or press Back twice. Volume, mute, power, input and voice buttons still work as normal. Home is taken by Android and never reaches apps, so the screen will close if you press it: open this check again and carry on.", 17);
        last = text(log.lastLine(), 22);
        hint = text("", 17);
        list = text(log.liveText(), 17);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int action = e.getAction();
        if (!logging) {
            // The rest of the two-second OK hold (repeats, then the release) must not press the summary's first button.
            if (swallowOkUp && RemoteLog.isOk(e.getKeyCode())) { if (action == KeyEvent.ACTION_UP) swallowOkUp = false; return true; }
            return super.dispatchKeyEvent(e);
        }
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(e);
        InputDevice device = e.getDevice();
        RemoteLog.Outcome outcome = log.record(e.getKeyCode(), KeyEvent.keyCodeToString(e.getKeyCode()), e.getScanCode(),
                action == KeyEvent.ACTION_DOWN, e.getRepeatCount(), e.getEventTime(), e.getDownTime(), device == null ? "" : device.getName());
        last.setText(log.lastLine());
        list.setText(log.liveText());
        if (outcome == RemoteLog.Outcome.FINISH) { swallowOkUp = RemoteLog.isOk(e.getKeyCode()); finishLogging(); return true; }
        hint.setText(outcome == RemoteLog.Outcome.BACK_ONCE ? "Press Back again to finish." : "");
        if (RemoteLog.passToAndroid(e.getKeyCode())) return super.dispatchKeyEvent(e);
        return true;   // everything else is only recorded, so no button moves, opens or plays anything here
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && logging) { log.interrupted(); if (list != null) list.setText(log.liveText()); }
    }

    private void finishLogging() {
        logging = false;
        String summary = log.summary(PerformanceActivity.header(this));
        String saved = PerformanceActivity.saveReport(this, REPORT_FILE, summary);
        page("Remote check finished");
        text("Copy this into the report, or photograph it. It holds no addresses, serial numbers or account details.", 17);
        if (saved != null) text("Also saved on the TV as " + saved, 16);
        button("Back", this::finish).requestFocus();
        button("Start again", this::recreate);
        text(summary, 16);
    }
}
