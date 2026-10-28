# Samsung Frame / Tizen architecture — v3.4

**Owner target:** 2021 Frame QE50LS03AAUXXU, reported firmware T-NKM2DEUC-2310.0; verify on device.

Use signed Tizen Web App/WGT and supported Samsung/Tizen APIs. Preserve native Smart Hub, Home, Art Mode, TV Plus, HDMI/CEC, Echo Studio audio, volume and standby. Native Art Mode is system-owned and **not** Unified Wallpaper. Do not claim third-party WGT can overlay native Art Mode or control OS-reserved Home.

**CleanFrame-style Home reassignment:** separate research proposal only (G5P). Include exact firmware applicability, read-only capability checks, hazards, supported alternatives, independent rollback, stop conditions, owner go/no-go. No undocumented helper, native write, privileged install or remote reassignment is authorised by v3.4.

Use supported AVPlay/media paths only when platform permissions and hardware proof allow. Do not assume Android Media3, HOME intents, background services, audio focus or emergency overlays apply to Tizen. Tizen signing uses author/distributor certificates; verify distributor/device eligibility, expiry and renewal requirements and N→N+1 installation continuity on owner device. Protect keys and certificate passwords.

Agent prepares install/test instructions; owner executes SDB/TV developer steps and supplies evidence. A WGT build is not a successful Frame install.

## Zero-configuration NAS discovery feasibility (v3.4)

Ordinary Tizen Web page JavaScript lacks raw UDP SSDP. Investigate an **embedded Tizen Web service** within the same signed WGT (not a separate externally installed helper) for UDP M-SEARCH, DLNA ContentDirectory browsing and localhost HTTP IPC. GitHub implementation reference: `PatrickSt1991/tessel-tizen-tv` (`tizen-app/service/service.js`, `tizen-app/config.xml`, issue #131); project is MIT-licensed—review licence and attribution before reuse. It reports testing on other Samsung model years, **not this owner's 2021 Frame**. First prove service lifecycle, permissions, UDP, localhost, NAS browse and playback on physical Frame. Multicast may need broadcast/bounded unicast SSDP fallback; do not default to unconstrained HTTP subnet sweep or broad CSP/network privileges. If unsupported, report a blocker and evaluate alternatives without asking normal users for IP addresses or making false capability claims.
