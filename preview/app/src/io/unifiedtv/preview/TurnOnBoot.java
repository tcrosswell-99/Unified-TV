package io.unifiedtv.preview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * OD-23, after the TV starts (launcher app only, declared in launcher/AndroidManifest.xml): when "Open Unified TV when the
 * TV turns on" is switched on and allowed, starts the small watcher, which counts the start as one wake. Otherwise it does
 * nothing at all. Only Android can send BOOT_COMPLETED (a protected broadcast), so no other app can trigger it.
 * Module 03: no keys are read or reassigned here.
 */
public final class TurnOnBoot extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;
        if(TurnOn.active(c))TurnOnService.start(c,TurnOnService.BOOT);
    }
}
