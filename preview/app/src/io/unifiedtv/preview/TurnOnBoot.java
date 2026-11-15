package io.unifiedtv.preview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * OD-23, after the TV starts (launcher app only, declared in launcher/AndroidManifest.xml): when "Open Unified TV when the
 * TV turns on" is switched on and allowed, starts the small watcher, which counts the start as one wake. Otherwise it does
 * nothing at all. OD-29: "Connect when the TV turns on" (off by default) connects Unified TV's own VPN here too. Only
 * Android can send BOOT_COMPLETED (a protected broadcast), so no other app can trigger it.
 * Module 03: no keys are read or reassigned here.
 */
public final class TurnOnBoot extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;
        if(TurnOn.active(c))TurnOnService.start(c,TurnOnService.BOOT);
        // OD-29: "Connect when the TV turns on" (off by default) is only noted here: Android won't let an app start the VPN
        // from the background, so Unified TV's own VPN connects when Home first comes to the front (OwnVpn.catchUp).
        if(OwnVpn.atStart(c))OwnVpn.boot(c,()->{});
    }
}
