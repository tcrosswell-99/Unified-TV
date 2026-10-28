# Unified TV DLNA discovery probe (proof of concept)

> **Prototype. Not part of Unified TV.** This is a separate test package that answers one question on the owner's Samsung The Frame (2021): can a Unified TV package find the NAS's media server automatically, with no address typed? It has **not** been installed or tested on the Frame. Nothing here changes the Unified TV app in `samsung/src/`, which keeps its current NAS flow until this probe passes on the real TV.

## Why a probe, and why a service

A Tizen web page can only make HTTP requests. Media servers are found by SSDP, which is UDP, so the page can't find them by itself. A Tizen **web service**, packaged in the same `.wgt` as the page, runs on the TV's Node runtime and can use UDP. The page starts the service and asks it over `http://127.0.0.1:18731`.

The architecture follows [Tessel](https://github.com/PatrickSt1991/tessel-tizen-tv) (MIT, Copyright (c) 2026 Patrick), a Samsung TV media player with the same kind of hybrid service. Its [issue #131](https://github.com/PatrickSt1991/tessel-tizen-tv/issues/131) shows why the probe tries four methods:

- multicast M-SEARCH from one TV never reached the wire;
- MiniDLNA answered only multicast, so a passive listener that heard its NOTIFY announcements found it;
- a unicast sweep was cut short because its socket closed before the send queue drained.

Tessel's results are from other Samsung TVs (its README lists a 2019 RU7020, a 2023 S90C and a 2024 QN90D; issue #131 used an S90D), not from a 2021 Frame. **The probe's code is our own, written from that description. No Tessel code is copied**, so there is no third-party code notice here.

## What it does, step by step

| Step | What happens | PASS means |
| --- | --- | --- |
| 1 Service startup | The page makes a fresh random token, starts `UTVDLNAPOC.svc` with `tizen.application.launchAppControl` (the token travels in the launch's ApplicationControl data, key `utv-token`), then polls `GET /ping` every 0.3 s for up to 20 s | The service answered with this launch's token; the time is shown |
| 2 Localhost and token | The page asks `/ping` with no token and with a wrong token, and reads which address the service is listening on | Both refused (403), and the service listens on `127.0.0.1` only |
| 3 SSDP discovery | The service searches four ways at once, each on its own sockets and counted separately: **mc** multicast M-SEARCH (ST `urn:schemas-upnp-org:device:MediaServer:1`, sent twice); **bc** M-SEARCH to the subnet's directed broadcast address (twice); **uni** unicast M-SEARCH to every address of the TV's own /24 (never wider; at most 64 per socket, and each socket closes only after its send queue has drained, with a 12 s limit); **notify** a passive listener on the standard SSDP multicast group, port 1900, running since the service started, recording media-server NOTIFY announcements. Answers are collected for 4 s after the last search leaves | At least one media server answered by some method |
| 4 Validation | Each answer's LOCATION is read only if it is plain http to a **private** IPv4 address (10/8, 172.16/12, 192.168/16) and on **the same host that answered**. The description must name a MediaServer device and a ContentDirectory service whose control address is on that same host. The screen shows friendlyName, modelName, UDN and which methods found it | At least one media server confirmed |
| 5 Browsing | ContentDirectory Browse of object `0` (BrowseDirectChildren, first 50): the top folder names and their count; then one level into the first folder | The root lists at least one folder |
| 6 Summary | One line to photograph | — |

Example summary line: `POC v0.1.0 svc=PASS(1.2s) local=PASS ssdp: mc=0 bc=1 uni=1 notify=1 valid=1 browse=PASS(5 folders)`. Each `ssdp` figure is the number of distinct media-server answers that method received; a code in brackets means the method itself had a problem.

The screen never shows an IP address. Press **Down** to see the service's own log (it may show addresses on your network: cover them before sharing a photo). Press **OK** to run again, and **Back** to stop the service and close the probe.

## Security limits

- The service listens on `127.0.0.1:18731` only, never on a network interface. Every request needs this launch's token (32 hex characters from `crypto.getRandomValues`). Without the token the service answers nothing but a refusal.
- It is not a proxy. It never fetches an address the page sends. Browse takes only the index of a server validated in the current run; the control addresses stay inside the service. It reads only private IPv4 addresses, follows no redirects, caps every reply (256 KB for a description, 2 MB for a Browse) and gives up after 4 s.
- There is no streaming proxy. If this succeeds, AVPlay would play from the NAS directly.
- The page's Content-Security-Policy and `<access>` rule allow `http://127.0.0.1:18731` and nothing else, with no `unsafe-eval` or `unsafe-inline`. `tizen:allow-mixed-content` is on only so that the packaged page may call that plain-http loopback address.
- Privileges: `internet` (the loopback request, the service's UDP and its HTTP to the NAS) and `application.launch` (starting the service). Nothing else: no filesystem, download, mediastorage or externalstorage. A web service has no privileges of its own; the package's privileges cover it ([Tizen Web Service guide](https://github.com/Samsung/tizen-docs/blob/master/docs/application/web/guides/applications/service-app.md)).

## What Samsung's documentation says about services on a 2021 TV

- The [Tizen Web Service guide](https://github.com/Samsung/tizen-docs/blob/master/docs/application/web/guides/applications/service-app.md) lists services as supported on "Tizen 3.0 and Higher for TV". It also says: "The TV Web service requires partner-level certification in Tizen 3.0." Whether the Frame (Tizen 6.0) runs a service signed with an ordinary Samsung developer certificate is **unknown**. Tessel reports that it works with the owner's own certificate on its TVs. Step 1 answers this for the Frame: `SVC_LAUNCH_ERR` or `SVC_NO_ANSWER` there means it doesn't.
- Lifecycle: a service exports `onStart`, `onRequest` and `onStop` (CommonJS). The page starts it with `tizen.application.launchAppControl` and the service reads the data with `getRequestedAppControl()` in `onRequest` (Samsung's [TizenTVApps SimpleService sample](https://github.com/Samsung/TizenTVApps/tree/master/TVWebApps/Service/SimpleService); Samsung notes that the data's order isn't guaranteed, so the token is looked up by key).
- Newer Tizen versions document a `wrt:service` module (`startService`). The probe doesn't use it, because the 2021 engine may not have it.
- The TV's Node build is old (Tessel found one without `Buffer.alloc`), so the service is ES5 with no npm packages.
- `developer.samsung.com` was not reachable from the build environment. The sources above are Samsung's own GitHub repositories.

## Result codes

| Code | Meaning |
| --- | --- |
| `SVC_PASS` | The service started and answered with this launch's token |
| `SVC_NO_TIZEN` | Not on a Samsung TV (a desktop browser) |
| `SVC_LAUNCH_ERR` | The TV refused to start the service (the error name is shown). Likely certificate or firmware policy |
| `SVC_NO_ANSWER` | Nothing answered on 127.0.0.1:18731 within 20 s: the service didn't start, or the page isn't allowed to reach it |
| `SVC_TOKEN_NOT_RECEIVED` | The service answered but never got the token (the launch data didn't arrive) |
| `SVC_TOKEN_MISMATCH` | The service holds a different token |
| `LOCAL_PASS` | No token or a wrong token is refused, and the service listens on 127.0.0.1 only |
| `LOCAL_TOKEN_NOT_ENFORCED` | A request without the right token was answered: a security failure, stop testing |
| `LOCAL_NOT_LOOPBACK` | The service isn't bound to 127.0.0.1 only |
| `LOCAL_UNEXPECTED` | The token check gave an unexpected status |
| `SSDP_FOUND` / `SSDP_NOTHING` | Some method found a media server / none did |
| `SSDP_FAILED`, `NO_IFACE`, `BUSY` | The discovery request failed / no private network address on the TV / a search was already running |
| `VALID_PASS` / `VALID_NONE` | At least one / no answer confirmed as a media server with a ContentDirectory |
| `BROWSE_PASS`, `BROWSE_EMPTY`, `BROWSE_FAILED`, `BROWSE_SKIPPED` | Folders listed / none at the top / the Browse failed / nothing to browse |

Per-method codes, shown in brackets after a count:

| Code | Meaning |
| --- | --- |
| `OK`, `LISTENING` | Normal |
| `NO_UDP`, `NO_SOCKET`, `BIND_ERR`, `SOCKET_ERR`, `SEND_ERR` | The runtime has no UDP / couldn't open or bind a socket / the socket failed / every send failed |
| `NO_BROADCAST`, `BROADCAST_REFUSED` | No usable subnet mask / the TV refused broadcast |
| `NOT_DRAINED` | Some unicast searches never left within 12 s |
| `BIND_1900_ERR`, `JOIN_ERR`, `LISTEN_ERR` | The passive listener couldn't use port 1900 (the TV's own DLNA may hold it) / couldn't join the group / stopped |

Validation set-aside codes (step 4 lists them): `LOC_HOST_MISMATCH`, `LOC_NOT_PRIVATE`, `LOC_NOT_IPV4`, `LOC_BAD_URL`, `LOC_CREDENTIALS`, `DESC_TIMEOUT`, `DESC_HTTP_<status>`, `DESC_NET_<error>`, `DESC_NOT_XML`, `NOT_MEDIA_SERVER`, `NO_CONTENT_DIRECTORY`, `CONTROL_HOST_MISMATCH`.

## Building and testing (no TV needed)

```text
node --test samsung/poc-dlna/test/*.test.js   # Node 18 or later, no npm packages
bash samsung/poc-dlna/build.sh                # runs the tests, then builds the unsigned package
```

This writes `samsung/poc-dlna/out/UnifiedTV-DLNA-POC-unsigned.wgt` (unsigned, test-only), `SHA256SUMS.txt`, `manifest.txt`, and the staged folder `samsung/poc-dlna/out/UnifiedTV-DLNA-POC/` for signing. `out/` is git-ignored. The tests use real loopback UDP and HTTP with fake media servers. They cover:

- SSDP parsing;
- description validation;
- Browse parsing;
- the token check;
- the private-address guard;
- the /24 limit;
- the send-queue drain.

CI (`.github/workflows/samsung.yml`) runs the same commands and uploads the unsigned probe as its own 14-day test artifact.

## Owner steps on Windows (PowerShell)

These need the owner's own Samsung certificate profile, the same one used for Unified TV ([samsung/README.md](../README.md)). Replace `<profile>`, `<TV-address>` and `<device>` with your own values and keep them in your private notes, not in this repository. Each step is reversible.

1. **Build** (from the repository root; needs Node 22 or later, which expands the quoted pattern itself, and Python 3):

   ```powershell
   node --test "samsung/poc-dlna/test/*.test.js"
   python samsung\poc-dlna\tools\package.py
   ```

2. **Sign** the staged folder with your Samsung certificate profile:

   ```powershell
   tizen security-profiles list
   tizen package -t wgt -s <profile> -- samsung\poc-dlna\out\UnifiedTV-DLNA-POC
   ```

   Note the signed `.wgt` file name it prints (it is written into that folder) and keep its SHA-256 with your notes (`Get-FileHash <file>.wgt`).

3. **Connect** (Developer Mode on, with your computer as the Host PC, exactly as for Unified TV):

   ```powershell
   sdb connect <TV-address>
   sdb devices
   ```

4. **Install and run.** The probe has its own package id, so it installs beside Unified TV and doesn't touch it:

   ```powershell
   tizen install -n samsung\poc-dlna\out\UnifiedTV-DLNA-POC\<signed-file>.wgt -s <device>
   tizen run -p UTVDLNAPOC.Probe -s <device>
   ```

   Make sure the NAS's media server (for example Synology Media Server) is switched on first, and that the TV is on the same home network, not guest Wi-Fi.

5. **Photograph**: the whole screen once the summary line appears, including every step's code. Then press **OK** to run it again after two or three minutes and photograph that too. The passive listener only hears servers when they announce themselves, and some announce rarely, so a second run is the fair test of `notify`. Write down the model, firmware, whether the TV is on Wi-Fi or Ethernet, the NAS and its media server, and the package hash.

6. **Logs** (optional): press **Down** on the probe to show the service's log on screen. If your TV allows `sdb dlog` (many retail TVs don't), this filters the service's lines:

   ```powershell
   sdb -s <device> dlog -v time | Select-String "UTVPOC"
   ```

7. **Remove it** (Unified TV and the TV's settings are untouched):

   ```powershell
   tizen uninstall -p UTVDLNAPOC.Probe -s <device>
   sdb disconnect <TV-address>
   ```

   Then turn Developer Mode off if you don't need it.

## What a result would and wouldn't prove

A full PASS proves that on this Frame, with this firmware and this network, a signed package can start a service, keep it on loopback, find the NAS by SSDP, confirm it and list its folders. It doesn't prove playback, which is a separate test. If step 1 fails, discovery from the TV itself isn't available to Unified TV on this Frame, and the decision goes back to the owner. Record results with [OWNER_TESTING.md](../../docs/OWNER_TESTING.md).

## Licence

Part of Unified TV, under the repository's MIT licence. Original code; the architecture credits Tessel (MIT) as above.
