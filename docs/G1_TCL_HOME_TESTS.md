# G1 on the TCL: making Unified TV the Home screen, and getting back

Gate G1 in [SPEC.md](SPEC.md) Section 4 asks for proof, on the TCL and with its own remote, that Unified TV can be the Home screen and that the TV's own Home always comes back. This page gives the exact steps, in order. You run them from the Windows PC and with the remote. The agent cannot reach the TV. Nothing here counts as `physical_verified` until you report the results with the template at the end.

**Order matters.** Record the factory Home first (step 1), and test the way back (step 7 with `--check`) before you make Unified TV the Home screen.

**Keep it private.** Never post the TV's address, serial number, account names or family photos. The tool used here never prints or saves the address. Its records hold only app names, the TV model, the Android version and the build number.

## Before you start

Do the one-off PC setup in [OWNER_TESTING.md](OWNER_TESTING.md) (platform-tools in your home folder, network debugging on the TV), and install the review build `io.unifiedtv.launcher.devicecheck` as described there. Then, in PowerShell:

```powershell
cd $HOME
Invoke-WebRequest https://raw.githubusercontent.com/tcrosswell-99/Unified-TV/claude/modest-davinci-dq3wrc/preview/tools/home_recovery.py -OutFile home_recovery.py
$env:TV_HOST = "<the TV's address>:5555"
```

`home_recovery.py` needs only Python, nothing to install with pip. It finds `adb.exe` in `$HOME\platform-tools` by itself. If `python3` isn't found, use `py` instead in every command below. You can also give the address per command with `--tv <address>:5555` after `status` or `restore`.

## 1. Record the factory Home

Do this while the TV's own Home still answers the Home button.

```powershell
python3 home_recovery.py status --save
```

**Expected:**

- `The Home button opens: com.google.android.tvlauncher/.MainActivity (the TV's own Home)` (or a TCL name).
- A list of Home candidates. Android's `FallbackHome` is marked "never choose it".
- `Unified TV review build (io.unifiedtv.launcher.devicecheck): installed; Home entry switched off`.
- `Saved: home-evidence\factory-home-<date>.json`.

Keep the `home-evidence` folder on your PC. It is your note of the factory Home, and `restore` reads it.

## 2. Test the way back before you need it

```powershell
python3 home_recovery.py restore --check
```

**Expected:** `That is already the Home app. Nothing to do.` The tool only reads the TV here.

## 3. Check your remote's buttons (RC-05, HRC-09, HRC-01)

In the review build, open **Settings → TV & performance → Check your remote's buttons**. The same check is also on the **Performance check** screen. It appears only in the review build, so remote buttons are never recorded in an everyday build.

1. Photograph the remote, front and back, and the label inside the battery cover.
2. Press every button once, slowly. Then hold OK, Back, Up and one app button each for about a second. The screen lists each button as it arrives: its name, keycode, scancode and how long it was held.
3. Volume, mute, power, input and voice buttons still do their usual job. **Home never reaches apps**, because Android takes it itself. That is expected, not a fault. If a button closes the screen (Home, an app shortcut such as Netflix, or the TCL button that once escaped to the TCL interface), write down which one and what appeared, open the check again and carry on. The summary records each time the screen was lost, and after which button.
4. Hold OK for two seconds, or press Back twice, to finish. Photograph the summary.
5. Copy the summary to the PC:

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $env:TV_HOST shell cat /sdcard/Android/data/io.unifiedtv.launcher.devicecheck/files/remote-check.txt
   ```

   If that says "Permission denied", use the photograph instead.

For each button, the report uses one of the words from SPEC.md Section 6.4: `received`, `OS-reserved`, `third-party-owned` (an app button the TV handles), `unsupported` (pressed, nothing arrived) or `not-present` (no such button). The summary sorts out what it can. You mark the rest.

## 4. Measure Home (AM-07, spec Section 14)

In the review build, open **Settings → TV & performance → Performance check → Measure Home (10 seconds)**. Home opens. Move left and right along the dock, steadily, for the whole 10 seconds. The results then open by themselves: frames, median and 95th-percentile frame time, janky frames (over 32 ms), the worst frame, focus response (button press to the next Home frame) and memory. Run it three times. Copy the last run to the PC:

```powershell
& "$HOME\platform-tools\adb.exe" -s $env:TV_HOST shell cat /sdcard/Android/data/io.unifiedtv.launcher.devicecheck/files/home-measurement.txt
```

These numbers cover Unified TV's own frames and key handling only, not other apps, HDMI inputs or the network. The focus target is a 95th percentile under 100 ms over 100 rapid presses, so add runs together until you have about 100 presses.

## 5. Make Unified TV the Home screen

In the review build, open **Settings → TV & performance → Make Unified TV the Home screen**, then **Continue**. Write down exactly what Android shows: a request to change the Home app, a Home settings page or a chooser. Choose Unified TV. Then:

```powershell
python3 home_recovery.py status
```

**Expected:** `The Home button opens: io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry (Unified TV)`, and the review build's Home entry "switched on".

## 6. The Home and remote tests

Do each one with the TCL remote and write down what happened. Film anything surprising.

| Test | What to do | Expected |
| --- | --- | --- |
| HRC-02, RC-02 | Press Home from Unified TV, then from Netflix, Disney+ and BBC iPlayer (start something playing in each first). | Unified TV Home on the first press each time. |
| RC-06 | Open Netflix, Disney+ and BBC iPlayer from the dock and come back with Home or Back. | The same tile still has focus, with its own artwork, no stale background. |
| HRC-03, RC-04 | On Unified Home, press Back ten times quickly, then hold Back for three seconds. Repeat inside Settings and an open sheet. | Each press closes one layer. At Home, nothing happens: you never land in the TCL interface. |
| RC-11 | Hold Right along the dock; hold OK on a tile; tap Down fast five times. | One predictable action each; no double launch. |
| HRC-04, RC-07 | Standby with the remote's power button, wait a minute, wake. Then restart (Settings → System → Restart, or unplug at the wall for 30 seconds). Then turn the router off for a minute while on Home. | Unified TV Home, or a usable TCL Home, after each; the remote and sound work. |
| HRC-01 | Press the button that used to escape to the TCL interface, from Home and from inside an app. | Record exactly where it goes. It must not strand you: Home brings you back. |
| OI-01, OI-02 | Settings → TV & performance → **Open original TV interface** (small, last row), then **Cancel**. | Clear choices; Cancel changes nothing. |
| OI-03 | The same, then **Open original interface for now**; then press Home. | The TCL interface opens; Home returns to Unified TV. |
| OI-05 | The same, then **Make original interface the default**, confirm, choose the TCL Home. Then put Unified TV back from its Settings. | The TCL Home answers Home until you change it back with the remote alone. |
| HRC-10, RC-10 | With Unified TV as Home, put the factory Home back **without the PC**, using only the TV's own Settings. | Write down the menu path you found (for example Apps → Default apps → Home app). The TCL Home answers Home. |

## 7. Restore the factory Home with the PC

Use this whenever you want the TV's own Home back, or if anything above goes wrong. First see what it will do:

```powershell
python3 home_recovery.py restore --check
```

**Expected:** `Factory Home: com.google.android.tvlauncher/.MainActivity (saved by status --save on …)` and `Would run: adb shell cmd package set-home-activity …`. Nothing changes. Then:

```powershell
python3 home_recovery.py restore
```

Type `yes` when asked. **Expected:** `The Home button now opens: com.google.android.tvlauncher/.MainActivity`, `Restored.`, and a `home-evidence\restore-<date>.json` record. Press Home on the remote to confirm.

- If there is no saved record, it offers the TV's own Home candidates (never Unified TV or Android's placeholder) and asks you to pick one by number. `--component <name>` picks one from the `status` list directly.
- It changes only which app answers the Home button. It never uninstalls, disables, hides or clears anything, and it refuses any command that would.
- Removing the review build also hands Home back ([OWNER_TESTING.md](OWNER_TESTING.md) shows how). The general recovery rules are in [INSTALLATION_AND_RECOVERY.md](INSTALLATION_AND_RECOVERY.md).
- Afterwards, turn network debugging off on the TV.

## Reporting back

One block per test, in the chat or a GitHub issue, without addresses, account names or family photos:

```
Build: <file name and SHA-256 from SHA256SUMS.txt>
TV: TCL <model from Settings > About>, Android TV <version>, firmware <build number>
Remote: <model from the battery-cover label, or a photo>
Test: <ID, e.g. HRC-02, RC-05, AM-07>
Home before: <what status showed>
Steps: <what you pressed, from where>
Expected: <from the table above>
Observed: <what happened; for RC-05 paste the remote-check summary, for AM-07 the measurement>
Escape button (HRC-01): <which button, and where it went>
Settings route back (HRC-10): <menu path on this TV>
Evidence: <photo/video name, or tool output pasted>
Result: pass / fail / unsure
```

The agent then updates the rows in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv). Only this evidence can make a G1 test `physical_verified`.
