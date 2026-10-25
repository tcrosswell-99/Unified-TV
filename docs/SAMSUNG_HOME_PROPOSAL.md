# Proposal: a controlled test of native Home reassignment on the owner's Samsung Frame (gate G5P)

> **This is a proposal only. Nothing in it has been executed.** No command has been run against the TV, no native helper exists in this repository, and no Samsung setting has been read or written. Writing or committing this document **does not authorise** any step in it. Owner decision OD-05 (25 October 2026) authorises only this written proposal: stages S1 to S5 as documentation. Stages S6 and S7 need a later, separate, explicit approval from the owner, given in their own words for a specific reviewed experiment ([SPEC.md](SPEC.md) sections 3 and 6.2, [PRODUCT_SPEC.md](PRODUCT_SPEC.md) section 31). An agent must never treat an edit to this file, or approval of it, as permission to touch the television.

## 1. What is being proposed, and why

Ordinary Tizen web apps can't own the remote's Home key: on the owner's Frame, Home opens Samsung Smart Hub. The supported Unified TV app ([samsung/](../samsung/README.md)) treats that as a documented platform gap.

[CleanFrame](https://github.com/nthnclrk/CleanFrame) (MIT licence, by Nathan Clark) reports that physical Home returned to its launcher on **one different Frame**. It did this through an **unsupported native mechanism**: a one-shot .NET helper, started over a developer SDB connection by an undocumented workaround, which queries and changes native Home routing and related settings and can restore them. It is not a Samsung SDK feature and can break with any firmware update.

This proposal describes how the owner *could* find out, safely and reversibly, whether anything similar is possible on **their** TV. It also sets out when to stop and how to undo every step. The default answer is **no-go** unless every gate below passes.

## 2. Reference device versus the owner's device

| Item | CleanFrame reference (reported by its author) | Owner's Frame |
| --- | --- | --- |
| Model | QA55LS03DAWXXY (LS03D, 2024) | QE50LS03AAUXXU (LS03A, 2021): different hardware |
| Firmware | T-PTMDUABC-0090-2130.0; Tizen 9.0, ARMv7, 32-bit | T-NKM2DEUC-2310.0; runtime and CPU architecture **not yet read from the TV** |
| Signed launcher installed | Reported | Not yet: no signed Unified TV package exists |
| Home from Netflix returns to the launcher | Reported on that TV | Unknown |
| Restore of original Home routing | Reported on that firmware | Unknown; no independent recovery shown |
| Art Mode, standby, power-on after change | Partly confirmed in the reference guide | Unknown |

Nothing in the reference column is evidence for the owner's TV. See also the [capability table](devices/samsung-frame-2021.md).

## 3. Compatibility questions to answer first (read-only)

1. What Tizen version, web runtime and CPU architecture does this exact firmware report? (A different major version or architecture is an immediate no-go for any reference technique.)
2. Does an official developer connection work at all? It failed earlier ([AI_HANDOVER.md](AI_HANDOVER.md)), and nothing further is possible without it.
3. Does the TV allow a signed developer app from the owner's own certificates to install and run (stage S2)?
4. Does this firmware have a native Home or launcher routing setting equivalent to the one CleanFrame describes? Can its current value, **including whether it is absent**, be read without writing anything?
5. Does reading it need anything to be copied onto the TV (staged files)? If so, that is itself a change and needs the owner's permission before it happens.
6. Is there a way back that does **not** depend on the same undocumented mechanism?
7. How do Art Mode, Samsung TV Plus, Smart Hub, standby, cold power-on and firmware updates depend on the Home routing setting?
8. Does Samsung's automatic firmware update risk changing or locking the setting mid-experiment? Can automatic updates be paused through normal Settings during the test window?

## 4. Staged approach (SPEC.md 6.2, PRODUCT_SPEC.md 31.4)

| Stage | What would happen | Changes the TV? | Authorised now? |
| --- | --- | --- | --- |
| S1: read-only inventory | The owner reads the model, full firmware, runtime, architecture and installed apps through official developer tooling, and records the Smart Remote key inventory. Addresses stay in private notes | No | Documented here; the owner runs it as normal development |
| S2: signed Unified TV app | The owner signs and installs the ordinary web app ([samsung/README.md](../samsung/README.md)), without any Home change | Installs one app (removable) | Owner's normal development choice; no native writes |
| S3: read-only native feasibility | Review whether this firmware exposes equivalent native operations, and whether the original Home routing can be read without writes. Record the differences from LS03D | Only if files must be staged, and only with separate permission | Proposal only |
| S4: recovery baseline | Record the original values and their presence or absence privately, offline, with firmware, build hash and app id. Prove a recovery route that doesn't use the same mechanism | No | Proposal only |
| S5: owner go/no-go | A short device-specific report, then the decision box in section 9 | No | This document |
| S6: guarded experiment | One apply, readback, physical check, exact restore, physical check | **Yes** | **Not authorised** |
| S7: household verification | The full remote, Art Mode and power test set | Yes (state from S6) | **Not authorised** |

## 5. Hazards

| Hazard | Effect | Mitigation in this proposal |
| --- | --- | --- |
| Home routes to Unified TV, and then Unified TV crashes, is uninstalled or fails to start | Home leads nowhere; the viewer may be stranded | Restore the original routing **before** any uninstall, update or identity change of the app. The app id stays fixed. Test crash recovery in S7 (CF-10) |
| The restore fails or is ambiguous | The TV stays reassigned | Stop at the first ambiguous output. Use the independent route in section 7. Never loop or force repeated writes |
| A firmware update changes or locks the setting | The modification or the rollback breaks silently | Pause automatic updates for the window where Settings allows. Revalidate after any update; firmware changes trigger a full re-test |
| Art Mode, Smart Hub, TV Plus, standby or power-on change behaviour | Native features are damaged (forbidden by SPEC.md 2 and 13) | Each is checked separately in S6 and S7. Any regression means restore and no-go |
| The developer connection is left open | A security exposure on the home network | Disconnect after every session, turn Developer Mode off and confirm |
| Factory reset or Smart Hub reset used as recovery | Loses installed apps, sign-ins, Art Mode content or settings | Last resort only. The owner decides. Its consequences are listed in section 7 |
| Copying CleanFrame's binaries, ids or values | Wrong identity or wrong values for this firmware | Forbidden. Any implementation is this project's own, reviewed and attributed ([CREDITS.md](../CREDITS.md)) |

## 6. The experiment, if it were ever approved (S6), described rather than scripted

This section explains **what would be tested and how it would be undone**. It deliberately contains no commands. A runnable procedure would be written only after S3 and S4 pass, reviewed separately, and run only after the section 9 approval.

1. **Before:** the signed Unified TV app (S2) is installed and passes NT-05 and NT-11. The original Home routing values, including any that are absent, are recorded offline (S4). The independent recovery route has been rehearsed as far as it safely can be. The owner is present with the physical remote, in a planned maintenance window, with the TV on mains power and automatic updates paused where possible.
2. **Guards:** any helper would refuse to run unless the firmware string, the installed Unified TV app id, the recorded original values and the target id all match exactly. On any mismatch it does nothing and reports.
3. **One apply:** change Home routing once, to the Unified TV app.
4. **Readback:** read the value back and compare it with the intended value. On any difference, ambiguity or error, restore at once and stop.
5. **Physical check:** press Home from Unified TV, from Netflix and from Samsung TV Plus. Note exactly where each lands. Check that Art Mode still enters and leaves normally.
6. **Exact restore:** put back the recorded original values, including removing any value that was absent before. Read back and compare.
7. **Physical check again:** Home must open Samsung Smart Hub exactly as before the experiment.
8. **Only then**, and only with renewed owner approval, could the apply be repeated for S7.

## 7. Rollback and independent recovery

In order of preference:

1. **The guarded restore** from step 6, which puts back the exact original values.
2. **A route independent of the undocumented mechanism**, which must be shown to exist in S4 before any write. Candidates to evaluate (not yet verified on this model):
   - **Reset Smart Hub** from the TV's own Settings (usually under Support, Device Care or Self Diagnosis). It signs out of apps and removes downloaded apps. Whether it resets Home routing is **unknown** and must be established before relying on it.
   - **Software update** to the current firmware through normal Settings. Whether it restores the routing is unknown.
3. **Factory reset**, from the TV's own Settings, as the last resort. This is the owner's decision alone, not an agent action, and not authorised by the specification for any other purpose. It returns the TV to its out-of-box state. Installed apps, sign-ins, network settings and Developer Mode are lost, and personal Art Mode content may be removed. Whether it reverts a native Home routing change is itself **unknown** and must be checked before the experiment, not assumed.

If no independent route can be demonstrated in S4, **the experiment must not happen** (PRODUCT_SPEC.md 31.4, S4).

After any session: disconnect developer tools, turn Developer Mode off, and confirm that Samsung Smart Hub, Samsung TV Plus, Art Mode, volume, Source/HDMI-CEC, Wi-Fi and Bluetooth still work through the TV's own menus (AT-75).

## 8. Stop conditions

Stop at once, restore the original state if anything was changed, and record a no-go, if any of these happens:

- the firmware, runtime or architecture differs from what S1 recorded, or an update is pending or installs;
- the developer connection is unreliable or asks for anything beyond the official Developer Mode flow;
- any read needs a staged file, or any write, that was not explicitly approved;
- any output is ambiguous, partial or unexpected, or a readback differs from the intended value;
- Home leads anywhere other than the two expected destinations (Unified TV after apply, Smart Hub after restore);
- Art Mode, Samsung TV Plus, standby, power-on, sound or the remote behaves differently;
- the restore can't be confirmed physically;
- the owner asks to stop, for any reason.

Never retry in a loop, never force a write, and never bypass a guard.

## 9. Owner go/no-go (S5)

Leave this box unticked unless every line is true. **An unticked box means no-go.** An agent must never tick it, fill it in or treat it as filled in.

```text
Samsung Frame native Home reassignment: owner decision

[ ] S1 inventory recorded privately (firmware, runtime, architecture, keys)
[ ] S2 signed Unified TV app installed and NT-05 / NT-11 checked
[ ] S3 read-only feasibility report reviewed; differences from LS03D listed
[ ] S4 original values (including absent ones) saved offline
[ ] S4 an independent recovery route was demonstrated, not assumed
[ ] I have read the hazards (section 5) and stop conditions (section 8)
[ ] I understand success is not guaranteed and that firmware updates may break it

Decision:   [ ] GO for one S6 experiment as described     [ ] NO-GO
Scope of the approval (one experiment, this TV, this firmware, this build):
Build hash: ______________________  Firmware: ______________________
Owner: __________________________  Date (UK): ______________________
```

A GO covers one S6 experiment only. S7 and any reapply need a fresh decision.

## 10. What this proposal does not do

- It does not add a native helper, a script, a command list or any CleanFrame code to this repository.
- It does not change the supported Unified TV app, which never registers or intercepts Home, Smart Hub, Art Mode or other reserved keys.
- It does not offer a "make default" option anywhere in the app (OI-06).
- It does not make any capability `supported_verified`. Every CF test (CF-01 to CF-10) stays unverified until the owner records evidence on this TV.
