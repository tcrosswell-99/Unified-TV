package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * OD-29: the VPN page, from holding OK (or Menu) on Home's shield and from Settings > Live TV & Network Protection. The
 * glass sidebar over a still Home (OD-19): On/Off, the country (with flags, Fastest first), "Connect when the TV turns
 * on" (off by default, launcher app only), "Use NordVPN's own app instead", and what is happening now (server, connected
 * since). Without a NordVPN key it offers the phone step instead. In Kids it only shows the state: an adult changes it.
 * It redraws itself whenever the VPN changes (OwnVpn's listener), never by polling.
 */
public final class VpnActivity extends GlassScreen {
    private boolean countriesPage;
    private final Runnable redraw = () -> { if (!isFinishing() && !countriesPage) show(); };
    @Override protected void onResume() { super.onResume(); OwnVpn.listen(redraw); if (countriesPage) countries(); else show(); }
    @Override protected void onPause() { OwnVpn.unlisten(redraw); super.onPause(); }

    void show() {
        countriesPage = false;
        page(OwnVpn.TITLE, OwnVpn.SUBTITLE);
        boolean kids = Profiles.kids(this), key = OwnVpn.saved(this);
        card();
        TextView state = text(OwnVpn.status(this), 20); state.setFocusable(false);
        if (!key) {
            text("Unified TV can run NordVPN itself, so one press on Home's shield connects or disconnects. It needs an access token from your Nord Account, pasted on your phone.", 16);
            if (!kids) row("Add your NordVPN token on your phone", null, true, () -> OwnVpn.setup(this));
            else text("An adult sets this up in Adults.", 16);
            nordApp(kids);
            notes();
            return;
        }
        OwnVpn.Phase p = OwnVpn.phase();
        if (kids) text("An adult turns the VPN on or off, and chooses the country, in Adults.", 16);
        else {
            Button toggle = row(p == OwnVpn.Phase.CONNECTING ? OwnVpn.CONNECTING_WORDS : "VPN", p == OwnVpn.Phase.ON ? "On" : p == OwnVpn.Phase.CONNECTING ? null : "Off", false, () -> OwnVpn.press(this));
            toggle.setEnabled(p != OwnVpn.Phase.CONNECTING);
            row("Country", OwnVpn.countryWords(OwnVpn.chosen(this)), true, this::countries);
            if (OwnVpn.atStartOffered(this)) switchRow("Connect when the TV turns on", OwnVpn.atStart(this), yes -> OwnVpn.atStart(this, yes));
            else text(DefaultHome.available(this) ? "\"Connect when the TV turns on\" needs Unified TV to come to the front by itself after the TV starts: make it the Home screen, or switch on \"Open Unified TV when the TV turns on\"."
                : "\"Connect when the TV turns on\" is in the Unified TV app, not in this preview.", 15);
        }
        nordApp(kids);
        if (!kids) {
            group("Your NordVPN key"); card();
            text("Kept encrypted on this TV and never shown. The access token you pasted was not kept.", 15);
            row("Add a new token on your phone", null, true, () -> OwnVpn.setup(this));
            row("Forget the NordVPN key", null, false, () -> new AlertDialog.Builder(this).setTitle("Forget the NordVPN key?")
                .setMessage("Unified TV's VPN turns off and the key is removed from this TV. NordVPN's own app is not affected.")
                .setPositiveButton("Forget", (d, w) -> { OwnVpn.forget(this); note("NordVPN key forgotten."); show(); }).setNegativeButton("Cancel", null).show());
        }
        notes();
    }
    /** NordVPN's own app, for a household that prefers it: Unified TV's VPN goes off first (Android allows one VPN). */
    private void nordApp(boolean kids) {
        if (!LocalDiscovery.nordVpn(this)) return;
        group("NordVPN's own app"); card();
        // Kids: only NordVPN's app opens (if an adult approved it); Unified TV's VPN is never turned off from here.
        if (kids) row("Open NordVPN's app", null, false, () -> VpnGate.connect(this));
        else row("Use NordVPN's own app instead", null, false, () -> {
            if (OwnVpn.phase() == OwnVpn.Phase.ON || OwnVpn.phase() == OwnVpn.Phase.CONNECTING) OwnVpn.disconnect(this, () -> VpnGate.connect(this));
            else VpnGate.connect(this);
        });
        text("Android allows one VPN at a time: connecting one turns the other off." + (kids ? " In Kids, NordVPN opens only if an adult approved it." : ""), 15);
    }
    private void notes() {
        group("Good to know"); card();
        text(LiveTvProtection.RULE + " Unified TV's own VPN counts.", 15);
        text(OwnVpn.LAN, 15);
        text(OwnVpn.NOT_KILL_SWITCH, 15);
        text(OwnVpn.UNOFFICIAL, 15);
    }

    /** The country list: Fastest first, then NordVPN's countries with their flags. A week's copy, else asked for once. */
    void countries() {
        countriesPage = true;
        page("Country", "Where the VPN connects");
        List<NordLynx.Country> kept = OwnVpn.cached(this);
        if (kept.isEmpty()) {
            card(); TextView wait = busy("Getting NordVPN's countries…");
            android.content.Context app = getApplicationContext();
            new Thread(() -> { List<NordLynx.Country> got = OwnVpn.countries(app, false);
                runOnUiThread(() -> { if (!countriesPage || isFinishing()) return; if (got.isEmpty()) { wait.setText("Unified TV couldn't get NordVPN's countries. Fastest still works."); row(OwnVpn.FASTEST, null, false, () -> pick("")); } else list(got); }); }, "vpn-countries").start();
            return;
        }
        list(kept);
    }
    private void list(List<NordLynx.Country> all) {
        clear(); card();
        String now = OwnVpn.chosen(this);
        List<Button> rows = new ArrayList<>();
        rows.add(row(OwnVpn.FASTEST, now.isEmpty() ? "Chosen" : null, false, () -> pick("")));
        text("Fastest: the server NordVPN recommends for this TV.", 15);
        for (NordLynx.Country c : all) rows.add(row(OwnVpn.flagged(c.code), c.code.equals(now) ? "Chosen" : null, false, () -> pick(c.code)));
        for (Button b : rows) if (b.getText().toString().equals(OwnVpn.countryWords(now))) { focusOn(b); break; }
    }
    private void pick(String code) {
        OwnVpn.choose(this, code);
        note(OwnVpn.phase() == OwnVpn.Phase.CONNECTING ? "Moving the VPN to " + OwnVpn.countryWords(code) + "…" : "Country: " + OwnVpn.countryWords(code));
        show();
    }
    /** Back from the country list returns to the VPN page; from the VPN page it closes. */
    @Override public void onBackPressed() { if (countriesPage) show(); else close(); }
    /** For tests: the country list is showing. */
    boolean onCountries() { return countriesPage; }

    static void open(android.app.Activity from) { try { from.startActivity(new Intent(from, VpnActivity.class)); } catch (RuntimeException ignored) {} }
}
