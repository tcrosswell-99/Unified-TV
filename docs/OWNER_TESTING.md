# Owner testing: what runs on your PC and TVs

Agents work only in the repository and cannot reach the televisions. Everything on this page is run by the owner from a Windows PC on the same network as the TV. Nothing becomes `physical_verified` in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv) without the evidence asked for at the end.

## What the agent does, and what you do

| Agent (repository) | Owner (PC and TV) |
| --- | --- |
| Code, unit and simulated tests, CI builds | Downloading a build from GitHub |
| Review builds and release files | Installing on the TV and testing with the real remote |
| Install, check and evidence tools | Running those tools against the TV |
| Updating the register from your report | Reporting what happened, with photos or tool output |

## One-off PC setup

In PowerShell:

```powershell
cd $HOME
Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile platform-tools.zip
Expand-Archive platform-tools.zip -DestinationPath $HOME -Force
& "$HOME\platform-tools\adb.exe" version
```

**Expected:** "Android Debug Bridge version …".

On the TCL, turn on **Developer options → USB debugging** (or network debugging, if your firmware calls it that). Find the TV's address under **Settings → Network → About**. Keep it private: never post it in an issue or screenshot.

```powershell
$tv = "<the TV's address>:5555"
& "$HOME\platform-tools\adb.exe" connect $tv
& "$HOME\platform-tools\adb.exe" -s $tv get-state
```

**Expected:** "connected to …", then `device`. Accept the "Allow USB debugging?" prompt on the TV the first time.

## Review build of the launcher (safe beside everything else)

> **Before uninstalling or replacing a review build that has taken over the Home button** (Settings → Appearance & Home → When the TV turns on → Make Unified TV the TV's Home (instant), OD-28): first choose **Return to the original TV interface** in that build's Settings. Otherwise the TV's own Home stays off and the TV has no Home screen after the uninstall. If that has already happened, put it back from the PC:
>
> ```powershell
> & "$HOME\platform-tools\adb.exe" -s $tv shell pm enable com.google.android.tvlauncher
> ```
>
> or on the TV: Settings → Apps → See all apps → Show system apps → Android TV Home → Enable.

`UnifiedTV-Launcher-review.apk` is the package `io.unifiedtv.launcher.devicecheck`. It installs beside the existing preview and does not touch it. It has its own empty settings. It never changes the Home button unless you choose that in its Settings.

1. **Download.** Open the pull request's latest green **CI** run on GitHub (Actions tab), scroll to **Artifacts** and download **UnifiedTV-Launcher-review**. Unzip it into `$HOME\review`.
2. **Check the file.**

   ```powershell
   Get-FileHash -Algorithm SHA256 $HOME\review\UnifiedTV-Launcher-review.apk
   Get-Content $HOME\review\SHA256SUMS.txt
   ```

   **Expected:** the hash matches the `UnifiedTV-Launcher-review.apk` line.
3. **Confirm the preview is untouched before and after** (skip this on the TCL: the owner removed the 0.3 preview there on 25 October 2026).

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $tv shell dumpsys package io.unifiedtv.preview | Select-String versionName,firstInstallTime
   ```

   Write both values down.
4. **Install and open.**

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $tv install -r $HOME\review\UnifiedTV-Launcher-review.apk
   & "$HOME\platform-tools\adb.exe" -s $tv shell am start -n io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.UnifiedPreviewActivity
   ```

   **Expected:** `Success`, then Unified TV opens on the TV.

   If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE ... signatures do not match`, that is expected: each GitHub build signs the review build with a new temporary test key, so one review build cannot update another (seen on the TCL, 25 October 2026). Remove only the review build, using the command under "To remove the review build" below, then run this step again without `-r`. This clears only the review build's own settings; the preview and the TV's own Home are untouched. Settings surviving an update can only be tested with the launcher signed with your own key ([SIGNING.md](SIGNING.md)).
5. **Repeat step 3.** **Expected:** the same versionName and firstInstallTime: the preview was not touched.
6. **Press Home on the remote.** **Expected:** the TV's own Home appears; installing changed nothing.
7. **Work through the checks** in [PREVIEW_0_5_TESTS.md](PREVIEW_0_5_TESTS.md): Home, Films & TV, Apps, NAS playback, and the alarm test under Settings → Notifications & Doorbell → Doorbell, alarms & this TV (see [Ring doorbell, alarms and this TV](#ring-doorbell-alarms-and-this-tv-settings--notifications--doorbell)).

**To remove the review build** (only the review build; never the preview):

```powershell
& "$HOME\platform-tools\adb.exe" -s $tv uninstall io.unifiedtv.launcher.devicecheck
```

## Trying Unified TV as the Home screen (G1; only after recovery is proven)

The full G1 procedure, with the recovery tool `preview/tools/home_recovery.py`, the remote check screen and Home measurement, is in [G1_TCL_HOME_TESTS.md](G1_TCL_HOME_TESTS.md). The short version follows.

Do the recovery first, so you know the way back works:

```powershell
& "$HOME\platform-tools\adb.exe" -s $tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Write down the last line: the TV's own Home (on the TCL it was `com.google.android.tvlauncher/.MainActivity`). The way back, whatever happens:

```powershell
& "$HOME\platform-tools\adb.exe" -s $tv shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

Removing the review build also hands Home back, **unless** it has taken over the Home button (OD-28, below): then choose Return to the original TV interface first, or run `adb shell pm enable com.google.android.tvlauncher` afterwards. Then, in the review build, open **Settings → Appearance & Home → When the TV turns on → Make Unified TV the Home screen** (Android's own Home choice, under Home button), and record what Android shows (a role request, a Home settings page or a chooser). Test Home from Netflix, after standby and after a restart, and **Open original TV interface**. Record each one.

## When the TV turns on (launcher app; owner's question of 3 November 2026)

**Not tested on a TV yet.** "Will Unified TV load instantly when the TV wakes from standby?" is now one choice: **Settings → Appearance & Home → When the TV turns on** (the first row there; its chip says the current choice). The page is the glass sidebar with three options, one line each, and a tick on what is true now:

1. **Show the TV's own Home**: nothing changes (the default).
2. **Open Unified TV (quick)**: the TV's own Home shows for a moment, then Unified TV comes to the front (OD-23, below).
3. **Make Unified TV the TV's Home (instant)**: the Home button and waking both show Unified TV straight away (OD-28, below: it uses network debugging once and may turn the TV's own Home off).

The Home button's other rows (Android's own **Make Unified TV the Home screen** and **Return to the original TV interface**) are on this page too, under *Home button*, and no longer in Appearance & Home itself.

1. **Tick:** open the page and note which option is ticked. Expected on a fresh install: **Show the TV's own Home**.
2. **Quick:** choose **Open Unified TV (quick)** and type the adult PIN. Expected: the tick moves to it. If *Display over other apps* is missing, the page says *Not working yet* and either opens the TV's own setting for it or shows the one-time computer command (below). Allow it, come back, and check the warning has gone. Then do the OD-23 checks below (standby and wake, and so on) and note how long the TV's own Home shows before Unified TV.
3. **Back to 1:** choose **Show the TV's own Home**, PIN. Expected: the tick moves back and the notification *Open Unified TV when the TV turns on* goes.
4. **Instant:** choose **Make Unified TV the TV's Home (instant)**. Expected: the Home takeover page opens and asks the adult PIN, then explains what changes (with the review build reminder). Follow the OD-28 steps below. When it finishes, open the page again: the tick is on option 3, and if option 2 was on before, it is now off (not needed).
5. **Choosing 1 or 2 while Unified TV is Home:** with option 3 done, choose **Open Unified TV (quick)**. Expected: nothing changes yet; **Return to the original TV interface** opens first and says *To choose Open Unified TV (quick), Unified TV first hands the Home button back to the TV's own Home.* Choose **Not now**: the tick stays on option 3. Do it again and complete Return: back on the page, the tick is on option 2.
6. **Guided setup:** Settings → Updates & About → Guided setup → **Run the guided setup again**. After *Your films and TV* comes **When the TV turns on** (step 5 of 6) with the same three options and **Skip — choose later in Settings**. Choose one (or skip), then **Next**: the last step lists *When the TV turns on:* and what is chosen (or *skipped*).

Report the model, firmware, build, what each step said and how long each wake took.

## Take over the Home button (launcher app, OD-28; only when you choose to try it)

**Not tested on a TV yet.** It turns the TV's own Home (`com.google.android.tvlauncher` on the TCL) **off**, never uninstalls it, and only after you confirm. Read the box at the top of the review-build steps first, and keep the PC fix to hand: `adb shell pm enable com.google.android.tvlauncher`. It needs network debugging on, as for the Optional clean-up.

1. **Before:** from the PC, write down the Home and the disabled list:

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   & "$HOME\platform-tools\adb.exe" -s $tv shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   & "$HOME\platform-tools\adb.exe" -s $tv shell pm list packages -d
   ```

2. **Start:** Settings → Appearance & Home → **When the TV turns on** → **Make Unified TV the TV's Home (instant)**, PIN, **Start**. The first page explains what changes and says *Before uninstalling a review build, choose Return to the original TV interface.* If the TV asks **Allow USB debugging?** for `unifiedtv@tv`, tick **Always allow** and choose **OK**. Expected on the TCL: the page says the TV puts **Android TV Home (com.google.android.tvlauncher)** ahead of other Home screens, and nothing has changed yet (step 1's commands still say the same). Record exactly what the page says.
3. **Confirm:** choose **Turn off Android TV Home and use Unified TV** (the page first warns that a few of the remote's app buttons may stop working). Expected: *Unified TV is the Home screen*. From the PC, the first command of step 1 now names `io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry`, and `pm list packages -d` now includes `com.google.android.tvlauncher`. If the page says another app answers Home instead (for example `com.google.android.tungsten.setupwraith`), write its name down and choose **Undo**.
4. **Debugging:** choose **Next**, follow the steps to turn off USB debugging if you want, and **Check that it's off**. Then **Next: try the Home button**.
5. **Press Home** on the remote within 20 seconds. Expected: Unified TV comes to the front and says *Done. The Home button now opens Unified TV.* Then test Home from Netflix, from an HDMI input, after standby and after a restart at the wall, and Back from each app. Note any remote button (app buttons, Settings, Input) that stops working.
6. **Uninstall guard:** Settings → Updates & About shows *Before uninstalling or replacing this build, choose Return to the original TV interface.*
7. **Return:** Settings → Appearance & Home → **When the TV turns on** → **Return to the original TV interface**, PIN, and the same button again (debugging needs to be on for this). Expected: *The Home button opens Android TV Home again*; pressing Home shows the TV's own Home; step 1's commands match what you wrote down before.
8. **Return without debugging:** repeat steps 2 to 5, turn off USB debugging, then try step 7. Expected: the page shows the TV-only route (Settings → Apps → See all apps → Show system apps → Android TV Home → Enable) and the PC command. Follow the TV-only route, then **Check again**.

Report the model, firmware, build, what each step said, the PC output, and anything on the remote that stopped working. Unified TV never reads or reassigns any key, and never turns off anything except the TV's own Home.

### On the Sony BRAVIA 4K UR2 (no tested list yet)

Your report of 3 November 2026 (review build run 106, Android TV 10, `QTG3.200305.006.S97`): after **Allow USB debugging?** the page waited without end. No tested list covers this Sony, so Unified TV now **never turns anything off on it**: it can only ask Android to use Unified TV as Home, which changes no other app and is fully undone by **Return to the original TV interface**. **Not tested on the Sony yet.**

1. **Debugging:** on the Sony, Settings → Device Preferences → About → press OK on **Build** seven times; then Settings → Device Preferences → Developer options → turn on **USB debugging** (and **Network debugging**, if your Sony lists it).
2. **Before:** from the PC, write down the firmware, the Home and the disabled list:

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $tv shell getprop ro.build.fingerprint
   & "$HOME\platform-tools\adb.exe" -s $tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   & "$HOME\platform-tools\adb.exe" -s $tv shell pm list packages -d
   ```

3. **Start:** Settings → Appearance & Home → **When the TV turns on** → **Make Unified TV the TV's Home (instant)**, PIN. Expected, before any debugging prompt, one of:
   - *This TV keeps its own Home. Use 'Open Unified TV (quick)' instead.* (Android lists the Sony's own Home ahead of other Home screens, so Android's own choice can't change the Home button). Nothing was opened or changed. Note what the line above it names, then try **Try anyway** once, so we learn what the Sony really does.
   - *This TV hasn't been tested yet. Unified TV can ask Android to use it as Home without turning anything off. If the TV's own Home still wins, it will say so and change nothing.* Choose **Start**.
4. **Allow:** if the Sony asks **Allow USB debugging?** for `unifiedtv@tv`, the page says *Waiting for you to choose Allow on the TV…*; tick **Always allow** and choose **OK** within a minute. **Back** on the remote cancels at any time while it waits.
5. **Result:** within about a minute, never more than two, the page says either *Unified TV is the Home screen* (then **Next**, the debugging-off check, **Next: try the Home button** and press Home within 20 seconds), or *This TV keeps its own Home. Use 'Open Unified TV (quick)' instead.* Write down exactly what it says. If it stops with a problem instead, it names the step (for example *The TV didn't answer the debugging link*); write that down too.
6. **Steps:** Settings → Updates & About → Advanced → **Owner diagnostics** → **Debugging link**: copy *Last debugging-link step* and the list under it (times and steps only; no key, token or output).
7. **Nothing turned off:** run the last command of step 2 again; the disabled list must be the same as before.
8. **Option 2:** if the Sony keeps its own Home, choose **Use Open Unified TV (quick)**. Expected: the When the TV turns on page with **Open Unified TV (quick)** ticked (and Android's "Display over other apps" asked for, if it isn't allowed yet). Then put the Sony in standby and wake it: note how long the Sony's own Home shows before Unified TV.

## Open Unified TV when the TV turns on (launcher app, OD-23)

For a TV that keeps its own Home button. In the launcher app: **Settings → Appearance & Home → When the TV turns on → Open Unified TV (quick)** (adult PIN; see the section above). It is off by default, and not needed once the Home button opens Unified TV (option 3 then has the tick). The Home button still opens the TV's own Home; nothing reads or reassigns a key.

It needs Android's **Display over other apps** permission, used only to bring Unified TV to the front. If the TV has a screen for it, choosing the option opens it. If not, the page says **Not working yet** and shows this one-time computer step with the right package name (the review build is `io.unifiedtv.launcher.devicecheck`):

```powershell
& "$HOME\platform-tools\adb.exe" -s $tv shell appops set io.unifiedtv.launcher SYSTEM_ALERT_WINDOW allow
```

To take the permission away again: the same command with `default` instead of `allow`. Choosing **Show the TV's own Home** stops the small watcher.

On the TCL, with it switched on, record for each case which screen appears and after how long: (1) standby with the remote, wait 10 seconds, wake; (2) the same from inside Netflix, paused; (3) start a video, put the TV in standby and wake it: if the video is still playing, Unified TV should stay out of the way; (4) a full power cycle at the wall; (5) waking the TV by turning on a device on HDMI (HDMI-CEC), and with the TV set to start on its last input or on its own Live TV. Android does not tell apps the TV woke into another input, so in (5) Unified TV may come to the front over that input: say if it does. Also say if the TV's own Home appears on top of Unified TV a moment later, and send `adb shell dumpsys meminfo io.unifiedtv.launcher` after a day with it on.

## Remote button that opens Unified TV (launcher app, OD-31)

**Not tested on a TV yet.** The owner asked (3 November 2026): "On the Sony, pressing the remote's TV button should turn the TV on if it's asleep and then open Unified TV." It is offered on any TV, the TCL included. In the launcher app: **Settings → Appearance & Home → Remote button that opens Unified TV** opens **When the TV turns on** at the button's rows. Off by default; every change asks the adult PIN.

It works through a separate accessibility service, **Unified TV remote button**, that you switch on in the TV's own settings. Android shows a warning when you do: Unified TV only reacts to the one button you chose; it doesn't read the screen or other keys. Every other button keeps working as before.

On the Sony (BRAVIA 4K UR2, Android TV 10):

1. **Choose the button:** press **Use the TV button (suggested)** and type the PIN. Or press **Choose a button by pressing it**, then the button: *Press the button you want to use…*; Back, the arrows, OK, volume, power, numbers and play/pause are refused with a reason. Expected: the row shows **TV** (or the button's name). If pressing the TV button switches the TV to its own Live TV instead, the TV keeps that button from apps until step 3 is done: do step 3, then choose again.
2. **Switch it on:** **Open Unified TV with this button**, PIN. Expected: *Unified TV remote button* opens with the steps and what Android's warning means.
3. **Android's switch:** press **Open the TV's accessibility settings**, then **Device Preferences → Accessibility → Unified TV remote button**, switch it on and confirm Android's warning, then Back. Expected: *Switched on ✓ Unified TV remote button is on.* and the row **Android's accessibility switch** says *Switched on ✓*. If the TV has no such screen, the page shows the computer line below.
4. **Awake:** from the TV's own Home, Live TV, Netflix (paused) and YouTube, press the TV button. Expected: Unified TV's Home straight away, and the TV's own Live TV doesn't open. Then check that nothing else changed: volume (pressed and held), mute, Back, the arrows, OK, Home, numbers, channels, play/pause inside an app, and the voice button.
5. **Inside Unified TV:** in Settings, press the TV button: Home. With a film playing in Unified TV's player: nothing happens and the film keeps playing (Back or Stop return as usual).
6. **From standby:** put the TV in standby, wait 10 seconds, press the TV button. Record whether the TV wakes, what shows first and for how long (the TV's own Live TV may show for a moment), and whether Unified TV then comes to the front. Then wake it with the Power button: Unified TV comes to the front then too, because Android doesn't tell apps which button woke the TV (the page says so).
7. **Off again:** switch **Open Unified TV with this button** off. Expected: the TV button does what it did before. To remove the service altogether, switch **Unified TV remote button** off in the TV's accessibility settings.
8. **Kids:** in Kids, the TV button still opens Unified TV; changing anything asks the adult PIN.

The computer alternative to step 3 (the page shows the same as one line). It **adds** the service to Android's list instead of replacing it, so the Kids guard and any other accessibility service stay on:

```powershell
$adb = "$HOME\platform-tools\adb.exe"
$me = "io.unifiedtv.launcher/io.unifiedtv.preview.ButtonService"
$cur = (& $adb -s $tv shell settings get secure enabled_accessibility_services | Out-String).Trim()
if ($cur -eq "" -or $cur -eq "null") { $new = $me } elseif ($cur -like "*$me*") { $new = $cur } else { $new = "${cur}:${me}" }
& $adb -s $tv shell settings put secure enabled_accessibility_services "$new"
& $adb -s $tv shell settings put secure accessibility_enabled 1
```

For the review build the package is `io.unifiedtv.launcher.devicecheck` (the class name stays `io.unifiedtv.preview.ButtonService`). `adb shell input keyevent` does not go through accessibility services, so only the real remote tests this.

On the TCL, do steps 1 to 8 as well, and also hold Volume up and Volume down with the option on: on some TCL Google TV builds any service that filters keys stops volume repeating while held (docs/devices/tcl-55p71b-g09-key-repeat.md). Unified TV asks Android for keys only while the option is on, so switching it off should bring repeat back. After restarting the TV at the wall, check the row still says *Switched on ✓* and the button still works.

Report the model, firmware, build, the button's name as the page showed it, what each step did and how long the TV's own Live TV showed, and anything on the remote that stopped working.

## Fetching the Wallpaper artwork

This cloud session cannot reach museum websites, so the curated artwork is fetched on your PC. The tool keeps only works the museum itself marks public domain.

```powershell
cd $HOME
Invoke-WebRequest https://raw.githubusercontent.com/tcrosswell-99/Unified-TV/claude/modest-davinci-dq3wrc/preview/tools/fetch_artwork.py -OutFile fetch_artwork.py
python3 -m pip install --user pillow
python3 fetch_artwork.py
```

**Expected:** a "kept …" line for each work and "N of 6 works kept in C:\Users\<you>\UnifiedTV-artwork". Any "skip" line gives its reason. Send back the `.jpg` files, `catalogue.json` and `ARTWORK-rows.md` from that folder (drag them into the chat). The agent checks them and adds them to the app with their licence rows.

## Samsung Frame web app (only when you choose to try it)

The agent built and unit-tested the Frame app; it has never run on the TV. The full procedure, with its rollback, is in [samsung/README.md](../samsung/README.md#installing-on-the-frame-owner-only-on-the-owners-computer). On Windows the same steps are, in PowerShell from the repository folder, after installing Tizen Studio with the **Samsung TV Extension** and **Samsung Certificate Extension** and creating your Samsung certificate profile in its Certificate Manager:

```powershell
$env:Path += ";C:\tizen-studio\tools;C:\tizen-studio\tools\ide\bin"   # adjust if Tizen Studio is elsewhere
python samsung\tools\package.py                 # builds samsung\out\UnifiedTV-Frame (unsigned)
tizen security-profiles list                     # note your profile name
tizen package -t wgt -s <profile> -- samsung\out\UnifiedTV-Frame
sdb connect <TV-address>                         # read-only checks first
sdb devices
tizen install -n samsung\out\UnifiedTV-Frame\UnifiedTV.wgt -s <device>
tizen run -p UTVFRAME00.UnifiedTV -s <device>
```

**Expected:** Unified TV opens on the Frame with the dock and its light; the remote moves focus; Back at Home does nothing; the Samsung Home button returns to Samsung's Home. **To undo:** `tizen uninstall -p UTVFRAME00.UnifiedTV -s <device>`, then `sdb disconnect <TV-address>`, then turn Developer Mode off. This removes only Unified TV's own app; Smart Hub, TV Plus and Art Mode are never touched. Do **not** run anything from [SAMSUNG_HOME_PROPOSAL.md](SAMSUNG_HOME_PROPOSAL.md); it is a proposal awaiting your approval. Keep the certificate files and passwords off the repository and out of the chat.

## Live TV and NordVPN (OD-26, amended 3 November 2026)

**Not tested on a TV yet.** Live TV now opens when a VPN is connected, with NordVPN installed; NordVPN's notice only adds the country ([VPN_PROTECTION.md](VPN_PROTECTION.md)). On the TCL, in Adults:

1. **NordVPN off:** press Live TV on the dock. Expected: no Live TV; the sheet says "Live TV is locked. Connect NordVPN first." with only **Connect**, **Try again** and **Cancel**. Press **Try again**: it must say "Checking…" for a moment, then the answer again. On Home, the shield is as dim as the AirPlay icon when AirPlay is off, with a slash.
2. **Connect, then carry on:** **Connect** opens NordVPN; connect it there and press nothing else. Expected: within a few seconds Unified TV comes back by itself with "NordVPN connected — opening Live TV" and Live TV opens (needs "Display over other apps"; without it, press Back once and the same happens). The shield is now bright with a tick, and the flag shows once notification access is on. Also try pressing Back in NordVPN without connecting: the sheet says why again. Settings > Live TV & Network Protection says "Live TV: open", "A VPN is connected. If NordVPN drops, Unified TV will cover Live TV." (or "NordVPN is connected to *<country>*. …" with notification access), "This isn't a kill switch: …" and the Auto-connect tip.
3. **The other routes:** with NordVPN on, open Live TV from Apps and by typing "Open Live TV" in Ask your TV; with NordVPN off, both must refuse with the same words.
4. **Drop during Live TV:** with "Display over other apps" allowed for Unified TV (as for "Open Unified TV when the TV turns on"), play a channel and disconnect NordVPN with its own button (or from the phone app). Expected within about two seconds: the full-screen notice "NordVPN disconnected — Live TV is covered. Reconnect NordVPN." with **Reconnect** and **Back to Home**. Say whether Live TV's sound stopped behind it. Then **Reconnect**, connect, Back: the notice says "NordVPN is connected again." and **Watch Live TV** opens it.
5. **Without "Display over other apps":** repeat step 4. Expected: Live TV stays on screen and a "NordVPN disconnected" notification is posted; say whether the TCL shows it at all.
6. **NordVPN connected, another VPN app on the TV (the owner's report, 3 November 2026):** with NordVPN connected (NordVPN itself says connected) and, if it is installed, the other VPN app or TCL data saver still on the TV, and without notification access, press Live TV on the dock. Expected: Live TV opens at once, with no sheet. Send a photo if the sheet still says "locked". Settings > Updates & About > Advanced > Owner diagnostics, under **VPN and Live TV**, lists what was read (NordVPN installed, notification access, NordVPN's notice, apps that can run a VPN, the safety net) and says these are information only. Send that line.
7. **Notification access (only for the flag and Now Playing; the TCL has no screen for it):** the Live TV sheet no longer offers it. In Settings > Live TV & Network Protection choose **Turn on access using the TV's debugging prompt**: after the PIN and **Start**, the TV asks "Allow USB debugging?" (tick Always allow, OK); expected "Notification access is on.", then the steps to turn network debugging off and **Check that it's off**. Check the flag beside the shield, the words "NordVPN is connected to *<country>*" in Settings, and Now Playing for Amazon Music. From a computer instead: `adb shell cmd notification allow_listener io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.MediaWatch`.
8. **It stops:** after step 4's **Back to Home**, or pressing the TV's Home and opening another app, then disconnecting NordVPN, nothing should cover the other app (the second case needs the Kids app guard's accessibility service on; without it, a drop more than 15 minutes after opening Live TV should only post the notification). Also: while the notice shows, press the TV's own Home: a "NordVPN disconnected" notification should follow.

Report the model, firmware, build and each step's result.

## Unified TV's own VPN (OD-29)

**Not tested on a TV yet.** Unified TV now runs NordVPN itself, so the shield connects and disconnects with one press ([VPN_PROTECTION.md](VPN_PROTECTION.md#unified-tvs-own-vpn-od-29)). You need your NordVPN account and your phone, on the same home Wi-Fi as the TV. On the TCL (and later the Sony), in Adults, with the latest build:

1. **Generate a token:** on your phone, sign in at [my.nordaccount.com](https://my.nordaccount.com/), choose **NordVPN**, then **Set up NordVPN manually** (Nord Account may email you a code), then under **Access token** choose **Generate new token**. Any expiry is fine: it is used once. Don't send the token to anyone, including in a report.
2. **Scan the QR code:** on Home, press Up to the shield and press **OK** (or go to Settings > Live TV & Network Protection > **Add your NordVPN token on your phone**). Expected: one line, "To use the VPN here, add your NordVPN token on your phone…", then the adult PIN and the **NordVPN for Unified TV** page with its QR code. Scan it with the phone's camera.
3. **Paste:** paste the token on the phone page and tap **Save on the TV**. Expected on the phone and the TV: "Saved. Press the shield on Home to connect." Also try a wrong token once: both should say "NordVPN didn't accept that token".
4. **OK on the shield:** back on Home, with NordVPN's own app **disconnected**, press OK on the shield. Expected the first time: a sheet explaining that Android will ask once, **Continue**, then Android's own "Connection request" dialog: choose **OK**. Then the shield is dim with a dot ("Connecting…"), and within a few seconds bright with a tick and a small flag. Press OK again: it goes dim with a slash (off). Press OK again: it connects with no dialog this time. Send a photo if the consent dialog never appears or the shield stays on "Couldn't connect" (and what the line under the header said).
5. **Country and flag:** hold OK on the shield (or press Menu on it). Expected: the **VPN** page as a glass sidebar, showing "On · <server name> · connected since <time>". Choose **Country**, pick **United Kingdom** (or another): the VPN moves there and the flag beside the shield changes. Close and reopen the page: the country is still chosen. Check **Fastest** too.
6. **Live TV:** with Unified TV's VPN on (and NordVPN's own app not connected), press Live TV on the dock. Expected: Live TV opens at once; Settings > Live TV & Network Protection says "Unified TV's NordVPN connection is on (<country>). …". Then, while a channel plays, make the VPN drop: open NordVPN's own app, connect it (Android turns Unified TV's off), then disconnect it. Expected within about two seconds of the disconnect: the full-screen cover (with "Display over other apps" allowed). **Reconnect** on the cover turns Unified TV's VPN back on (Android may ask for its permission again, because NordVPN's app was used), and **Watch Live TV** follows.
7. **NAS still reachable:** with Unified TV's VPN on, open Your Films & TV and play something from the NAS; check Home Assistant's doorbell or status card and AirPlay from an iPhone. Expected: all work as with the VPN off, when they are set up by IP address or a `.local` name (a name only your router knows won't resolve while the VPN is on). Say which didn't.
8. **NordVPN's own app:** with Unified TV's VPN on, connect NordVPN's own app. Expected: Unified TV's goes off (Android allows one VPN), the VPN page says "Android turned Unified TV's VPN off…", and the shield's label under the pill says "NordVPN's app" or "another app". Then press OK on the shield: a one-time line says Unified TV's connection replaces NordVPN's app's, and Android may ask for its permission again.
9. **Connect when the TV turns on (Unified TV app only):** it is offered only while Unified TV is the Home screen or "Open Unified TV when the TV turns on" is on. Switch it on on the VPN page and restart the TV (Restart in the TV's settings, or power off at the wall). Expected: the shield turns on shortly after Unified TV's Home appears (not before: Android doesn't let it start from the background). Switch it off again afterwards if you prefer. Also: with NordVPN's subscription lapsed or a server not answering, OK on the shield should end with "NordVPN didn't answer — check your NordVPN subscription, or paste a new token." after about 10 seconds.
10. **Forget:** Settings > Privacy & Data > **Forget the NordVPN key**. Expected: the VPN turns off and the shield asks for the token again.

Report the model, firmware, build and each step's result. Never include the token or anything from the phone page's paste box.

## Amazon Music from inside Unified TV (OD-27)

**Not tested on a TV yet.** Unified TV controls the real Amazon Music app through Android's own media controls; Amazon Music keeps playing in its own app. Before starting: Amazon Music installed and signed in on the TCL (or Sony), and notification access allowed for Unified TV (Music page > **Allow in Settings**, or the one ADB line on Now Playing's access page). In Adults:

1. **Now Playing:** start a playlist in Amazon Music, press Home, open Unified TV and press OK on the Music tile. Expected: Now Playing (not the Amazon Music app), with the title, artist, album, the album art as the large square and the blurred background, and a moving progress line. Say if the art is missing or another album's.
2. **The remote:** OK pauses and plays; Right and Left skip; hold Right or Left for about two seconds: the track moves forward or back about 10 seconds a step (or a line says Amazon Music doesn't offer it); the remote's Play/Pause, Next and Previous keys work too. Down reaches the buttons below: say which appear (Shuffle, Repeat, Thumbs up or Like, Play on, Open Amazon Music app) and whether Shuffle and Repeat actually change Amazon Music. Menu opens Amazon Music. Back returns to Home: the music must keep playing.
3. **Play by name:** Music tile with nothing playing (or hold OK on the Music tile for **Open Amazon Music app**, then stop the music), type an artist in the Music page's search with the on-screen keyboard, choose **Looking for: Artist**, press **Play**. Expected within about 8 seconds: Now Playing with that artist. If Amazon Music's own screen opens instead, say whether Unified TV came back by itself. Repeat once with **Album** and once with **Playlist**. The search then appears under **Recent searches**.
4. **Voice:** in Ask your TV, type or say "play Adele" and "play Adele on Amazon Music". Expected: the same as step 3.
4a. **Speak on the Music page:** choose **Looking for: Album**, press **Speak** beside the search field (or the remote's own voice or microphone key while the Music page is open) and say an album. Expected: the TV asks "Say an artist, album, song or playlist", the words appear in the field, and the album plays at once (Now Playing). Say whether the TCL listened inside the page (it asks once to allow the microphone) or opened its own voice screen. If the TV has no voice input for apps, there is no **Speak** button and one line says so. With Echos set up, "… everywhere" or "… on" an Echo's name plays there this once only.
4b. **Your playlists / Recently played:** under the field, **Your playlists** shows your Amazon Music playlists with their pictures when Amazon Music shares its library (step 5); OK on one plays it. If Amazon Music refuses, **Recently played** shows the albums and artists Unified TV has seen play in this profile (up to 12, newest first, with their art); OK plays it by name. Nothing appears until something has played. **Clear recent searches and plays** empties both.
5. **Your Amazon Music:** on the Music page, say whether **Your Amazon Music** appears. If it does, open it, open a playlist and press OK on it: it should play and Now Playing open. Settings > Updates & About > Advanced > Owner diagnostics, under **Music**, says "Amazon Music browsing: refused" or "available" and the last play-by-name route. Send those lines. (Refused is expected and fine: search still works.)
6. **Without notification access** (only if you are happy to withdraw it and allow it again): the Music page explains the one step and step 3 still sends the request to Amazon Music.
7. **Kids:** switch to Kids. Without an adult approving Amazon Music for Kids, Music's Play says Amazon Music isn't on the Kids list; with approval it plays.

## Play on an Echo (OD-27; only with Home Assistant's Alexa Media Player)

**Not tested yet.** Needs Home Assistant already connected to Unified TV (Settings > Smart Home) and the community **Alexa Media Player** integration installed in Home Assistant through HACS, signed in to Amazon there (Unified TV never asks for your Amazon password). Alexa Media Player is unofficial and can stop working when Amazon changes its sign-in.

1. **Found:** open Music. Expected: **Play on: This TV**. Without Alexa Media Player (or with Home Assistant off) it says instead "To play on your Echo speakers, add Alexa Media Player to Home Assistant". Open **Play on**: your Echos by name, **Everywhere**, and any speaker groups. Say whether an entity called "Everywhere" exists in Home Assistant (we couldn't confirm the integration lists it).
2. **One Echo:** choose an Echo, search an artist, **Play**. Expected: that Echo plays it and Now Playing shows its title, artist and artwork; OK pauses it, Right skips, the TV remote's volume keys (and Quieter/Louder) change the Echo's volume.
3. **Everywhere:** choose **Everywhere** and play something. Expected: every Echo plays it together, in sync. Say whether they started together, or only one played.
4. **Voice:** "play jazz everywhere" and "play jazz on" and an Echo's name. Expected: it plays there this once; afterwards Music's **Play on** still shows what you chose before (a place said by voice is for that request only). Then say "play Carry On Wayward Son" and "play Shine On You Crazy Diamond": each plays as one title on your Play on choice, not on an Echo called "Wayward Son".
5. **Problems in plain words:** with Home Assistant switched off, Play says "Home Assistant isn't connected"; Owner diagnostics, under **Music**, shows the last Echo problem (never an address or a token).
6. **Kids:** in Kids, Play on offers only This TV until an adult turns on **Echo speakers in Kids** (Adults' Play on page, adult PIN).

8. **AirPlay (AirPin LITE), OD-30:** first, in AirPin LITE's own settings, turn on **start at boot** (or **run in background**) once. With AirPlay ready (bright icon), OK on the AirPlay icon shows a small panel, "AirPlay is ready. On your iPhone or Mac, open Control Centre > Screen Mirroring … and choose '<name>'", with **Done**; AirPin does not open. Force-stop AirPin so the icon is dim with a slash, then press OK: a panel says "AirPlay isn't running. Turn on 'start at boot' …", with **Check again** focused and **Start AirPin now**; AirPin must **not** open. Press **Check again**: "Checking…" for a few seconds, then "AirPlay still isn't running …" (or the ready panel, if AirPin came back by itself). Press **Start AirPin now**: "Starting AirPlay…", AirPin opens briefly, and Unified TV comes back by itself with the ready panel. Say how long AirPin stayed on screen, and whether your iPhone then sees the TV. If AirPlay never appears, Unified TV leaves you in AirPin (it never pulls you out of AirPin's own prompts or another app); when you come back to Home, the panel says "AirPlay didn't start…". Finally restart the TV, wait a minute and say whether the icon is bright without opening AirPin (AirPin's start-at-boot working). Settings > Connections & Devices > AirPlay should say the same, with **Check again** and a short how-to.

Report the model, firmware, build and each step's result.

## Ring doorbell, alarms and this TV (Settings > Notifications & Doorbell)

Nothing needs setting up in Home Assistant beyond the Ring integration itself: no automation, no script. Unified TV listens to Home Assistant directly. **Not tested with a real Ring yet**, so please report each step.

1. **Ring in Home Assistant:** in Home Assistant, Settings > Devices & services should list **Ring** with your doorbell. If not, add it there (Settings > Devices & services > Add integration > Ring) and sign in with your Ring account.
2. **The page:** on the TV, Settings > Notifications & Doorbell > **Doorbell, alarms & this TV** (adult PIN). The top card should say "Home Assistant is connected. When someone rings Front Door, this TV shows who's there. Nothing else to set up in Home Assistant." (with your doorbell's name). If it says "No Ring doorbell found in Home Assistant…" or "Home Assistant knows … but isn't hearing from Ring right now", note the exact words and check step 1.
3. **Doorbell rows:** *Doorbell press* and *Picture* should show your doorbell with a tick (for example "Picture: Front Door live view ✓"); *Motion at the door (optional)* says Off. Open *Doorbell press*: the list replaces the page inside the same sidebar (nothing behind it), your doorbell is under **Ring doorbells** with a tick, and no Echo or "Amazon Kids" items appear. **Show everything** lists the rest, with their technical names in smaller type. Back returns to the page with *Doorbell press* in focus.
4. **Test the doorbell:** the card that a real ring brings up appears in the lower right, marked TEST, with your doorbell's name. Say whether the live picture appeared, or "Live view unavailable", and how long it took. Back closes it.
5. **A real ring:** with Unified TV on screen (and once while playing something), press the doorbell. Expected: the same card without TEST, within a few seconds. Say how long it took, and whether it appeared once or twice (some Ring models send two alerts per ring). Leaving and reopening Unified TV, or restarting Home Assistant, should never bring up an old ring.
6. **Motion (optional):** choose *Motion at the door*, pick your doorbell's motion, and walk past the doorbell. Expected: a small "Movement at Front Door" notice for about 5 seconds, no camera. Set it back to Off if you'd rather not have it.
7. **Smoke and carbon monoxide:** the row says "On — found N alarms in Home Assistant" with their names below. **Test the alarm screen** shows the full alarm screen marked TEST; **End the alarm test** clears it.
8. **Xbox (optional, Sony):** *Turn on the Xbox from this TV* lists Home Assistant scripts, scenes and buttons with Xbox ones first. Pick the one that turns your Xbox on, then choose Xbox on Home and say whether it switched on.

## Live TV protection check (Android's own block, optional)

Follow [VPN_PROTECTION.md](VPN_PROTECTION.md#adding-androids-own-block-optional-the-only-real-protection). It uses `preview/tools/vpn_check.py`, which needs Python and the repository's `tools/requirements.txt`.

## Memory and CPU check

`preview/tools/mem_check.py` measures how much memory and CPU Unified TV uses on the TV, and how many of its frames were janky, over a few minutes. It needs only Python (no `tools/requirements.txt`) and the `adb` from the PC setup above, already connected. It changes nothing on the TV except the app's own frame counters, and prints the model and Android version but never a serial number or an address.

```powershell
$env:ADB = "$HOME\platform-tools\adb.exe"
$env:ANDROID_SERIAL = $tv
python preview\tools\mem_check.py --minutes 3 --every 15
```

Leave Home on screen for the first run, then repeat while moving along the dock, and once with the library open. It measures the Unified TV build it finds (the review build first); add `--package io.unifiedtv.preview` to measure the preview instead.

**Expected:** one line every 15 seconds (`PSS … MB  native …  java …  graphics …  CPU …%  frames … janky …%  TV free … MB  pressure …`), then a summary. The target for Home left alone on the 2 GB TCL is a few per cent of CPU (not yet measured on the TV): the background's drift is the only thing still drawing, and it rests after ten minutes without a key. Paste the summary into your report. Settings > Updates & About > Advanced > Owner diagnostics also says, under **Home background**, whether the light was moving and at how many frames a second, and shows the TV's own animation scales.

## Optional clean-up on the TV (only when you choose to try it)

**Not tested on a real TV yet.** Read [INSTALLATION_AND_RECOVERY.md](INSTALLATION_AND_RECOVERY.md#optional-clean-up-on-the-tv-itself-android-preview-and-launcher) first. It needs network debugging (ADB on port 5555) switched on, as you already have it for the PC. Before you start, take photos of the TV's Home, Settings > Apps, and a working HDMI input with sound, so you have a baseline.

1. **Skipping:** open Settings > Updates & About > Advanced > **Optional clean-up**, read the first page, choose **Not now**. Expected: nothing on the TV changed, and nothing asks you again (AT-18).
2. **Read-only check:** open it again and choose **Start the read-only check**. The first time, the TV shows **Allow USB debugging?** for `unifiedtv@tv`: tick **Always allow from this computer** and choose **OK**. Expected: the page names your TV and firmware and says *Tested list: TCL 50C725K, on this exact firmware*; each listed app shows on/off, version, risk and *Recommended* or *Optional, your choice*; every switch is off; TalkBack, Live TV, Settings and the launcher are listed as kept. From the PC, `adb shell pm list packages -d` is unchanged (AT-20).
3. **One small batch:** turn on the switches for two low-risk apps (for example *TCL analytics* and *Store demo sticker*), choose **Review my choices**, check the summary, then **Turn off these 2 apps**. Expected: progress one by one, then both *off*. From the PC: `adb shell pm list packages -d` now lists them. The undo record was saved on the TV before the first one.
4. **Check the TV still works** (AT-21): follow the page's list (streaming apps, remote and microphone, NordVPN, each HDMI input with sound, doorbell alerts, standby and wake). Note anything that broke.
5. **Undo:** choose **Undo all**. Expected: both apps *on* again; `pm list packages -d` back to step 2.
6. **Undo files:** do step 3 again, then **Save the undo files on your phone**; download both files. On the PC, put them in one folder and run `$env:TV_HOST = $tv; python preview\tools\cleanup_restore.py <folder>\unifiedtv-cleanup-record.json`, then the `tools/restore.sh` line it prints (Git Bash). Expected: both apps on again, Home unchanged.
7. **Without any computer:** do step 3 again, then on the TV open Settings > Apps > See all apps > Show system apps > *TCL analytics* > **Enable**. Expected: it is on again.
8. **End:** choose **Finish**, follow the steps to turn off USB debugging, then **Check that it's off**. Expected: "nothing answers on port 5555". (Turn it back on afterwards if you still need ADB from the PC.)

Report the model, firmware, build, each step's result and any app that misbehaved. **Never** test with the stock launcher, Live TV, the inputs or the remote's services: the page refuses them, and that refusal is part of the test.

## Reporting back

Reply in the chat, or open a GitHub issue **without** addresses, account names or family photos, using:

```
Build: <file name and SHA-256 from SHA256SUMS.txt>
TV: TCL <model from Settings > About>, Android TV <version>, firmware <build number>
Remote: <model or a photo>
Test: <ID from the register, e.g. P05-02 or SAFE-01>
Steps: <what you pressed>
Expected: <from the checklist>
Observed: <what happened>
Evidence: <photo/video name, or tool output pasted>
Result: pass / fail / unsure
```

The agent then updates the register row: `test_level` becomes `physical_TCL`, and `status` becomes `physical_verified` only with that evidence (a failure stays `implemented_unverified`, with what happened written down).
