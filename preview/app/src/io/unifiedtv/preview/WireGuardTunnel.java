package io.unifiedtv.preview;

import android.content.Context;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * OD-29: the real tunnel, through the official WireGuard tunnel library for Android (com.wireguard.android:tunnel,
 * Apache-2.0, preview/THIRD_PARTY.md). GoBackend runs WireGuard in user space inside the library's own VpnService
 * (GoBackend$VpnService, declared by the library's manifest with BIND_VPN_SERVICE, so only Android can bind it). Android's
 * VPN consent (VpnService.prepare) must already be given: OwnVpn asks for it first. Created only when the household
 * connects, so tests (which use a fake VpnTunnel) never load the library's native code.
 */
final class WireGuardTunnel implements VpnTunnel {
    /** The name Android shows for the VPN session (the library's pattern: letters, digits, _=+.- and at most 15). */
    static final String NAME = "UnifiedTV";
    private final GoBackend backend;
    private final Named tunnel = new Named();
    private volatile Runnable dropped;
    /** True while Unified TV itself is taking the tunnel down: that is not a drop. */
    private volatile boolean closing;

    WireGuardTunnel(Context app) { backend = new GoBackend(app.getApplicationContext()); }

    @Override public void up(String config) throws Exception {
        Config parsed = Config.parse(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
        backend.setState(tunnel, Tunnel.State.UP, parsed);
    }
    @Override public void down() throws Exception {
        closing = true;
        try { backend.setState(tunnel, Tunnel.State.DOWN, null); } finally { closing = false; }
    }
    @Override public boolean isUp() { try { return backend.getState(tunnel) == Tunnel.State.UP; } catch (RuntimeException e) { return false; } }
    @Override public void onDropped(Runnable r) { dropped = r; }

    /**
     * Android's own always-on VPN (when the owner chooses Unified TV there) starts the library's VPN service by itself:
     * this asks Unified TV to connect then. Static, and loads no native code.
     */
    static void alwaysOn(Runnable connect) { GoBackend.setAlwaysOnCallback(connect::run); }

    private final class Named implements Tunnel {
        @Override public String getName() { return NAME; }
        @Override public void onStateChange(Tunnel.State state) {
            Runnable r = dropped;
            if (state == Tunnel.State.DOWN && !closing && r != null) r.run();
        }
    }
}
