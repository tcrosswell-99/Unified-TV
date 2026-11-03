# Samsung The Frame (2021): Unified TV capability table

The owner's TV: **Samsung The Frame, QE50LS03AAUXXU (LS03A, 2021)**, firmware **T-NKM2DEUC-2310.0** as recorded by the owner, with the Samsung Smart Remote (and its on-screen 123 keypad). The runtime is a native Tizen web application ([samsung/](../../samsung/README.md)).

Values follow [SPEC.md](../SPEC.md) section 6.3: `supported_verified`, `unsupported_verified` or `unknown`, never inferred from the platform's name. **The Unified TV web app has never been installed on this TV**, so every value is `unknown`. The "Expected" column is a plan taken from Samsung's documentation, not a result. The acceptance register holds the status. The same list is shown on the TV in Settings, TV & performance (`samsung/src/js/capabilities.js`).

| Capability | Value | Expected (not verified) | Test |
| --- | --- | --- | --- |
| Signed Unified TV app installs, opens and updates in place | `unknown` | Needs the owner's Samsung author and distributor certificates | NT-11, SIGN-05, CF-02 |
| Developer access (SDB) | `unknown` | The owner reported Developer Mode on and the developer port reachable, but an SDB connection failed ([AI_HANDOVER.md](../AI_HANDOVER.md)) | CF-01 |
| Which remote keys reach the app | `unknown` | Arrows, OK and Return without registration; media keys after `registerKey` | RC-09, HRC-08 |
| Physical Home key | `unknown` | Opens Samsung Smart Hub; a web app can't change that | NT-05, HRC-07 |
| Long Return | `unknown` | The TV's own exit; never intercepted | NT-05 |
| Opens by itself after power-on, standby or reboot | `unknown` | Model-dependent | NT-01 |
| Hidden and resumed (suspend) | `unknown` | JavaScript may be suspended; the app closes its socket and revalidates on return | NT-06 |
| Opening Netflix, Disney+, BBC iPlayer, Amazon Music | `unknown` | `tizen.application.launch` with ids found on the TV by name | NT-09, RC-06 |
| Opening Samsung TV Plus | `unknown` | Found by name; its native id is not confirmed | NT-04, AT-33 |
| SmartTube and ReamSmartz | `unknown` | Android-only; shown as unavailable, never as a fake tile | NT-09 |
| NAS playback in AVPlay | `unknown` | Direct play of supported formats; per-format results to be recorded | NT-02, LIB-03 |
| Automatic NAS discovery (SSDP) | `unknown` | Not available to a web app; the address is entered once | LIB-01 |
| Tizen web service in a signed developer package (DLNA probe) | `unknown` | The [DLNA probe](../../samsung/poc-dlna/README.md) starts a service on 127.0.0.1. Samsung's Tizen guide says TV web services needed partner-level certification in Tizen 3.0; Tessel reports services working with an owner's certificate on other Samsung TVs | Probe step 1 (svc), step 2 (local) |
| SSDP from that service: multicast, broadcast, /24 unicast sweep, passive NOTIFY | `unknown` | Each method counted separately; one Tessel report saw multicast M-SEARCH from a TV never reach the network | Probe step 3 (mc, bc, uni, notify) |
| Media server validated and browsed through the service | `unknown` | MediaServer with ContentDirectory on a private address, root Browse lists folders | Probe steps 4 and 5 |
| Private (authenticated) NAS playback | `unknown` | Not built: needs a validated media broker or scoped cookie | NT-07 |
| Tizen Key Manager for secrets | `unknown` | Available to web apps; hardware backing on this model unknown | GATE-CONN-02 |
| Alerts while Unified TV is hidden, in Art Mode or off | `unknown` | Not expected: a hidden app may be suspended and can't draw over other apps | SAFE-06, SAFE-07, NT-08 |
| Ring interrupting native Art Mode | `unknown` | Needs an external household coordinator; not built and never claimed | NT-03, NT-08, AT-07 |
| Native Art Mode left intact | `unknown` | The app never controls Art Mode | WP-12, FRAME-02 |
| Live TV with proven NordVPN protection | `unknown` | No per-app VPN on Tizen and no Live TV client; the tile stays locked | NT-10, GATE-VPN-03 |
| Echo Studio audio after leaving Art Mode | `unknown` | Depends on the TV's own audio connection | AT-12 |
| Ambient mesh frame rate and focus latency | `unknown` | CSS opacity crossfade and transform drift; still fallback if slow | AM-01–AM-12, NT-12 |
| Ring person toast and doorbell card (RING-01..04) | `unknown` | Shown only while Unified TV is open and in front; the doorbell card has no live camera and says so | RING-05 |
| Tizen Key Manager keeps the adult PIN record and Kids folders | `unknown` | Without it there is no PIN, so Kids can't be switched on (fail closed) | PROFILE-FRAME-01, GATE-CONN-02 |
| SubtleCrypto PBKDF2 for the adult PIN | `unknown` | A Tizen app page may not be a secure context; the identical JavaScript PBKDF2 is used then, at an untimed speed | PROFILE-FRAME-01 |
| Kids: apps opened outside Unified TV (Smart Hub, app buttons, voice) | `unknown` | Not expected: a web app can't see or close other apps, so there is no Kids app guard; only launches from Unified TV are checked | KIDS-FRAME-02 |
| Kids NAS folders and rating check (BrowseMetadata parent walk at play time) | `unknown` | Depends on the media server returning `parentID` and `upnp:rating`; unrated titles are allowed only inside a Kids folder | KIDS-FRAME-01 |
| Local resume per profile in AVPlay | `unknown` | Position kept in web storage by hashed key; seek-on-open behaviour to be checked per format | KIDS-FRAME-03, CW-01 |
| Native Home reassignment (CleanFrame-style) | `unknown` | Proposal only, not authorised ([SAMSUNG_HOME_PROPOSAL.md](../SAMSUNG_HOME_PROPOSAL.md)) | CF-03–CF-05 |

How to change a value: run the matching test on this TV with the exact signed build, and record the model, firmware, remote, build hash and what was observed ([OWNER_TESTING.md](../OWNER_TESTING.md)). A value becomes `supported_verified` or `unsupported_verified` only with that evidence.
