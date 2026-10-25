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

`UnifiedTV-Launcher-review.apk` is the package `io.unifiedtv.launcher.devicecheck`. It installs beside the existing preview and does not touch it. It has its own empty settings. It never changes the Home button unless you choose that in its Settings.

1. **Download.** Open the pull request's latest green **CI** run on GitHub (Actions tab), scroll to **Artifacts** and download **UnifiedTV-Launcher-review**. Unzip it into `$HOME\review`.
2. **Check the file.**

   ```powershell
   Get-FileHash -Algorithm SHA256 $HOME\review\UnifiedTV-Launcher-review.apk
   Get-Content $HOME\review\SHA256SUMS.txt
   ```

   **Expected:** the hash matches the `UnifiedTV-Launcher-review.apk` line.
3. **Confirm the preview is untouched before and after.**

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

Removing the review build also hands Home back. Then, in the review build, open **Settings → TV & performance → Make Unified TV the Home screen**, and record what Android shows (a role request, a Home settings page or a chooser). Test Home from Netflix, after standby and after a restart, and **Open original TV interface**. Record each one.

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

## Live TV protection check

Follow [VPN_PROTECTION.md](VPN_PROTECTION.md). It uses `preview/tools/vpn_check.py`, which needs Python and the repository's `tools/requirements.txt`.

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
