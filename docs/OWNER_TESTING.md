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

> **Before uninstalling or replacing a review build that has taken over the Home button** (Settings → Appearance & Home → Take over the Home button, OD-28): first choose **Return to the original TV interface** in that build's Settings. Otherwise the TV's own Home stays off and the TV has no Home screen after the uninstall. If that has already happened, put it back from the PC:
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
7. **Work through the checks** in [PREVIEW_0_5_TESTS.md](PREVIEW_0_5_TESTS.md): Home, Films & TV, Apps, NAS playback, and the alarm test under Settings → Notifications → Doorbell, reminders & room setup.

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

Removing the review build also hands Home back, **unless** it has taken over the Home button (OD-28, below): then choose Return to the original TV interface first, or run `adb shell pm enable com.google.android.tvlauncher` afterwards. Then, in the review build, open **Settings → TV & performance → Make Unified TV the Home screen**, and record what Android shows (a role request, a Home settings page or a chooser). Test Home from Netflix, after standby and after a restart, and **Open original TV interface**. Record each one.

## Take over the Home button (launcher app, OD-28; only when you choose to try it)

**Not tested on a TV yet.** It turns the TV's own Home (`com.google.android.tvlauncher` on the TCL) **off**, never uninstalls it, and only after you confirm. Read the box at the top of the review-build steps first, and keep the PC fix to hand: `adb shell pm enable com.google.android.tvlauncher`. It needs network debugging on, as for the Optional clean-up.

1. **Before:** from the PC, write down the Home and the disabled list:

   ```powershell
   & "$HOME\platform-tools\adb.exe" -s $tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   & "$HOME\platform-tools\adb.exe" -s $tv shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   & "$HOME\platform-tools\adb.exe" -s $tv shell pm list packages -d
   ```

2. **Start:** Settings → Appearance & Home → **Take over the Home button**, PIN, **Start**. If the TV asks **Allow USB debugging?** for `unifiedtv@tv`, tick **Always allow** and choose **OK**. Expected on the TCL: the page says the TV puts **Android TV Home (com.google.android.tvlauncher)** ahead of other Home screens, and nothing has changed yet (step 1's commands still say the same). Record exactly what the page says.
3. **Confirm:** choose **Turn off Android TV Home and use Unified TV**. Expected: *Unified TV is the Home screen*. From the PC, the first command of step 1 now names `io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.HomeEntry`, and `pm list packages -d` now includes `com.google.android.tvlauncher`. If the page says another app answers Home instead (for example `com.google.android.tungsten.setupwraith`), write its name down and choose **Undo**.
4. **Debugging:** choose **Next**, follow the steps to turn off USB debugging if you want, and **Check that it's off**. Then **Next: try the Home button**.
5. **Press Home** on the remote within 20 seconds. Expected: Unified TV comes to the front and says *Done. The Home button now opens Unified TV.* Then test Home from Netflix, from an HDMI input, after standby and after a restart at the wall, and Back from each app. Note any remote button (app buttons, Settings, Input) that stops working.
6. **Uninstall guard:** Settings → Updates & About shows *Before uninstalling or replacing this build, choose Return to the original TV interface.*
7. **Return:** Settings → Appearance & Home → **Return to the original TV interface**, PIN, and the same button again (debugging needs to be on for this). Expected: *The Home button opens Android TV Home again*; pressing Home shows the TV's own Home; step 1's commands match what you wrote down before.
8. **Return without debugging:** repeat steps 2 to 5, turn off USB debugging, then try step 7. Expected: the page shows the TV-only route (Settings → Apps → See all apps → Show system apps → Android TV Home → Enable) and the PC command. Follow the TV-only route, then **Check again**.

Report the model, firmware, build, what each step said, the PC output, and anything on the remote that stopped working. Unified TV never reads or reassigns any key, and never turns off anything except the TV's own Home.

## Open Unified TV when the TV turns on (launcher app, OD-23)

For a TV that keeps its own Home button. In the launcher app: **Settings → Appearance & Home → Home button → Open Unified TV when the TV turns on** (adult PIN). It is off by default and not shown once the Home button opens Unified TV. The Home button still opens the TV's own Home; nothing reads or reassigns a key.

It needs Android's **Display over other apps** permission, used only to bring Unified TV to the front. If the TV has a screen for it, the row opens it. If not, the row says **Needs permission** and shows this one-time computer step with the right package name (the review build is `io.unifiedtv.launcher.devicecheck`):

```powershell
& "$HOME\platform-tools\adb.exe" -s $tv shell appops set io.unifiedtv.launcher SYSTEM_ALERT_WINDOW allow
```

To take the permission away again: the same command with `default` instead of `allow`. Switching the row off stops the small watcher.

On the TCL, with it switched on, record for each case which screen appears and after how long: (1) standby with the remote, wait 10 seconds, wake; (2) the same from inside Netflix, paused; (3) start a video, put the TV in standby and wake it: if the video is still playing, Unified TV should stay out of the way; (4) a full power cycle at the wall; (5) waking the TV by turning on a device on HDMI (HDMI-CEC), and with the TV set to start on its last input or on its own Live TV. Android does not tell apps the TV woke into another input, so in (5) Unified TV may come to the front over that input: say if it does. Also say if the TV's own Home appears on top of Unified TV a moment later, and send `adb shell dumpsys meminfo io.unifiedtv.launcher` after a day with it on.

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

## Live TV and NordVPN (OD-26)

**Not tested on a TV yet.** Live TV now opens when NordVPN is connected ([VPN_PROTECTION.md](VPN_PROTECTION.md)). On the TCL, in Adults:

1. **NordVPN off:** press Live TV on the dock. Expected: no Live TV; the sheet says "Live TV is locked. Connect NordVPN first." with **Connect**, **Retry** and **Cancel**.
2. **Connect, then Retry:** **Connect** opens NordVPN; connect it, press Back, then **Retry**. Expected: Live TV opens. Settings > Live TV & Network Protection says "Live TV: open", "NordVPN is connected. If NordVPN drops, Unified TV will cover Live TV." and "This isn't a kill switch: …".
3. **The other routes:** with NordVPN on, open Live TV from Apps and by typing "Open Live TV" in Ask your TV; with NordVPN off, both must refuse with the same words.
4. **Drop during Live TV:** with "Display over other apps" allowed for Unified TV (as for "Open Unified TV when the TV turns on"), play a channel and disconnect NordVPN with its own button (or from the phone app). Expected within about two seconds: the full-screen notice "NordVPN disconnected — Live TV is covered. Reconnect NordVPN." with **Reconnect** and **Back to Home**. Say whether Live TV's sound stopped behind it. Then **Reconnect**, connect, Back: the notice says "NordVPN is connected again." and **Watch Live TV** opens it.
5. **Without "Display over other apps":** repeat step 4. Expected: Live TV stays on screen and a "NordVPN disconnected" notification is posted; say whether the TCL shows it at all.
6. **Notification access:** if another VPN app is installed, step 2 says "Can't tell which VPN is on. Allow notification access so Unified TV can see NordVPN." until notification access is allowed. Settings > Updates & About > Advanced > Owner diagnostics, under **VPN and Live TV**, lists what the rule read (NordVPN installed, notification access, NordVPN's notice, apps that can run a VPN, the safety net). Send that line.
7. **It stops:** after step 4's **Back to Home**, or pressing the TV's Home and opening another app, then disconnecting NordVPN, nothing should cover the other app (the second case needs the Kids app guard's accessibility service on; say what happens without it).

Report the model, firmware, build and each step's result.

## Amazon Music from inside Unified TV (OD-27)

**Not tested on a TV yet.** Unified TV controls the real Amazon Music app through Android's own media controls; Amazon Music keeps playing in its own app. Before starting: Amazon Music installed and signed in on the TCL (or Sony), and notification access allowed for Unified TV (Music page > **Allow in Settings**, or the one ADB line on Now Playing's access page). In Adults:

1. **Now Playing:** start a playlist in Amazon Music, press Home, open Unified TV and press OK on the Music tile. Expected: Now Playing (not the Amazon Music app), with the title, artist, album, the album art as the large square and the blurred background, and a moving progress line. Say if the art is missing or another album's.
2. **The remote:** OK pauses and plays; Right and Left skip; hold Right or Left for about two seconds: the track moves forward or back about 10 seconds a step (or a line says Amazon Music doesn't offer it); the remote's Play/Pause, Next and Previous keys work too. Down reaches the buttons below: say which appear (Shuffle, Repeat, Thumbs up or Like, Play on, Open Amazon Music app) and whether Shuffle and Repeat actually change Amazon Music. Menu opens Amazon Music. Back returns to Home: the music must keep playing.
3. **Play by name:** Music tile with nothing playing (or hold OK on the Music tile for **Open Amazon Music app**, then stop the music), type an artist in the Music page's search with the on-screen keyboard, choose **Looking for: Artist**, press **Play**. Expected within about 8 seconds: Now Playing with that artist. If Amazon Music's own screen opens instead, say whether Unified TV came back by itself. Repeat once with **Album** and once with **Playlist**. The search then appears under **Recent searches**.
4. **Voice:** in Ask your TV, type or say "play Adele" and "play Adele on Amazon Music". Expected: the same as step 3.
5. **Your Amazon Music:** on the Music page, say whether **Your Amazon Music** appears. If it does, open it, open a playlist and press OK on it: it should play and Now Playing open. Settings > Updates & About > Advanced > Owner diagnostics, under **Music**, says "Amazon Music browsing: refused" or "available" and the last play-by-name route. Send those lines. (Refused is expected and fine: search still works.)
6. **Without notification access** (only if you are happy to withdraw it and allow it again): the Music page explains the one step and step 3 still sends the request to Amazon Music.
7. **Kids:** switch to Kids. Without an adult approving Amazon Music for Kids, Music's Play says Amazon Music isn't on the Kids list; with approval it plays.

## Play on an Echo (OD-27; only with Home Assistant's Alexa Media Player)

**Not tested yet.** Needs Home Assistant already connected to Unified TV (Settings > Smart Home) and the community **Alexa Media Player** integration installed in Home Assistant through HACS, signed in to Amazon there (Unified TV never asks for your Amazon password). Alexa Media Player is unofficial and can stop working when Amazon changes its sign-in.

1. **Found:** open Music. Expected: **Play on: This TV**. Without Alexa Media Player (or with Home Assistant off) it says instead "To play on your Echo speakers, add Alexa Media Player to Home Assistant". Open **Play on**: your Echos by name, **Everywhere**, and any speaker groups. Say whether an entity called "Everywhere" exists in Home Assistant (we couldn't confirm the integration lists it).
2. **One Echo:** choose an Echo, search an artist, **Play**. Expected: that Echo plays it and Now Playing shows its title, artist and artwork; OK pauses it, Right skips, the TV remote's volume keys (and Quieter/Louder) change the Echo's volume.
3. **Everywhere:** choose **Everywhere** and play something. Expected: every Echo plays it together, in sync. Say whether they started together, or only one played.
4. **Voice:** "play jazz everywhere" and "play jazz on" and an Echo's name.
5. **Problems in plain words:** with Home Assistant switched off, Play says "Home Assistant isn't connected"; Owner diagnostics, under **Music**, shows the last Echo problem (never an address or a token).
6. **Kids:** in Kids, Play on offers only This TV until an adult turns on **Echo speakers in Kids** (Adults' Play on page, adult PIN).

Report the model, firmware, build and each step's result.

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
