package io.unifiedtv.preview;

/**
 * OD-29: the one tunnel Unified TV runs, as a small interface so everything around it (OwnVpn, the shield, Live TV's
 * rule) is tested with a fake. The real one is {@link WireGuardTunnel} (the official WireGuard tunnel library's
 * GoBackend). Calls block, so they are made off the main thread. The configuration holds the private key: an
 * implementation never logs, stores or shows it.
 */
interface VpnTunnel {
    /** Brings the tunnel up with this WireGuard (wg-quick) configuration; throws when it can't. */
    void up(String config) throws Exception;
    /** Takes it down; nothing happens when it is already down. */
    void down() throws Exception;
    /** Whether it is up now. */
    boolean isUp();
    /** Told when Android takes the tunnel down by itself (another app's VPN took over, the VPN was revoked). */
    void onDropped(Runnable dropped);
}
