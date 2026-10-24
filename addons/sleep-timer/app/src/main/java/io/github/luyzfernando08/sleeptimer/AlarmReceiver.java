package io.github.luyzfernando08.sleeptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The timer's two alarms: a minute early, the warning; just after the end, sleep if nobody answered. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Timer.ACTION_TEST.equals(intent.getAction())) {
            // From adb only (the receiver needs WRITE_SECURE_SETTINGS): a timer of a few seconds, to try it out.
            Timer.startSeconds(context, intent.getIntExtra("seconds", 70));
            return;
        }
        // The stored end even once passed: an inexact alarm can come well after it (Timer.due).
        long end = Timer.rawEnd(context);
        switch (Timer.due(intent.getAction(), end, System.currentTimeMillis())) {
            case WARN:
                context.startActivity(new Intent(context, WarningActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case SLEEP:
                Timer.sleepNow(context);                        // also clears the end and both alarms
                break;
            default:
                break;                                          // cancelled, pushed back since, or long stale
        }
    }
}
