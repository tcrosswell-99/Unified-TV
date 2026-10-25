# Preview 0.5: on-TV checks

Preview 0.5 is code-checked and unit-tested only. These are the checks that need the TCL (Android TV 11), the NAS and Home Assistant. Record each result in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv) with the build, TV model, firmware and outcome, and summarise it in [FEATURE_STATUS.md](FEATURE_STATUS.md). Say **Not tested** for anything skipped.

Use a fresh install of the 0.5 test build, without clearing the 0.4 data, so the existing saved connections are kept. Run each check twice: once with NordVPN off, once with NordVPN on.

## Your films and series (NAS)

1. With no library set up, start Unified TV, wait ten seconds on Home, then open **Your Films & TV**.
   - **Pass:** with one media server on the network, it goes straight to its folders, with no search screen and no list.
   - **Pass:** with several, a list of their names appears.
   - **Pass:** no address is shown or asked for.
   - Press Back from the server's top folder. **Pass:** the search screen appears and stays (it does not jump straight back into the folders).
2. Switch the NAS media service off and search again.
   - **Pass:** "No media server found" with **Search again**. Record whether this matches what the NAS was doing.
3. Open each of the three collections and add it. Save. Leave and come back.
   - **Pass:** the saved collections open straight away, and only those three are listed.
   - **Pass:** Back from inside a collection returns to the list of three, never to the server's other folders.
4. Open a folder with more than 24 entries that mixes music or photos with videos.
   - **Pass:** **Show more** appears until the last video, and every video is reachable.
5. Check the cards.
   - **Pass:** titles with server artwork show their own poster and year.
   - **Pass:** titles without artwork show a plain card, never another title's picture.
   - **Pass:** no card shows a file ending such as `.mkv` or `.mp4`, and file-style names (`The.Film.2019`) read as words.
6. Open a sub-folder, then press Back.
   - **Pass:** focus lands on the folder you just left.
7. Press Back while a folder still says **Loading…**.
   - **Pass:** the previous screen appears at once, and nothing from the abandoned folder appears later.
8. Unplug the NAS's network cable (or stop its media service) and open a collection.
   - **Pass:** within 25 seconds a plain message says the server isn't answering, with **Try again**. Reconnect, press **Try again**. **Pass:** the folder opens.
9. Restart the NAS. If its address changes (check the router), open a collection.
   - **Pass:** the collection opens without setting anything up again. If it says the folder is no longer on the server, record that, because the NAS gave its folders new IDs.
10. Switch to the Child profile and open Your Films & TV.
    - **Pass:** shared folders are refused, with a message about a restricted account.
11. With NordVPN on: repeat steps 1 and 8.
    - **Pass:** the server is still found and plays.
    - **If it isn't:** record whether NordVPN's "allow devices on your home network" setting was on.

Titles and posters here come from the media server itself, not a checked film database. This is a connectivity and playback preview, not the finished poster catalogue.

## Playing a film from the NAS

Use one short 1080p film (MP4), one MKV with several soundtracks and subtitles, and one 4K HEVC file if you have one. Record each file's container, picture and sound format.

1. Choose a film. **Pass:** the picture starts. Note roughly how long it took.
2. Press OK. **Pass:** it pauses and the controls appear. Press OK again. **Pass:** it continues. Repeat with the remote's Play/Pause key if it has one.
3. Press Right once, then Left once. **Pass:** about 10 seconds forward, then back.
4. Hold Right for five seconds. **Pass:** it moves steadily faster, without freezing, and stops when you let go.
5. Press Down (or Menu). **Pass:** a list of the film's soundtracks and subtitles appears; choosing one changes it. A film with only one soundtrack and no subtitles says so instead.
6. Press Back. **Pass:** the same library folder appears, with focus on the film you played.
7. Choose the same film again. **Pass:** it offers **Resume from … (on this TV)** and **Start from the beginning**. It never says Continue Watching. Resume. **Pass:** it starts near where you left.
8. While it plays, press the remote's Home key, then return to Unified TV. **Pass:** the film restarts from the same place, or a message offers **Try again**.
9. While it plays, put the TV in standby for a minute, then wake it. **Pass:** as step 8, with no endless spinner.
10. While it plays, unplug the NAS's network cable. **Pass:** within about 30 seconds a plain message appears with **Try again** and **Back to your films**. Reconnect and press **Try again**. **Pass:** it continues from about the same place.
11. Play a file the TV can't handle (for example a 4K HEVC 10-bit file on a TV without that decoder, or an unusual sound format). **Pass:** a plain message names picture or sound as the problem. It never shows a black screen with sound, or silence, as if it were working.
12. Watch a film to the end. **Pass:** it returns to the library, and choosing it again starts from the beginning.

The player keeps a short, private record of the last playback (time to first picture, formats offered, errors) for the evidence report. It holds no titles, file names or addresses, and nothing is written to the TV's log.

## Home Assistant

1. With no connection saved, start Unified TV, wait ten seconds, then open **Settings → Connections → Home Assistant**.
   - **Pass:** with one Home Assistant on the network, it goes straight to sign-in, with its address hidden.
2. Temporarily block mDNS (or test on a network where it is filtered) and repeat.
   - **Pass:** it is still found, through `homeassistant.local` or the home-network check, within about 20 seconds.
3. Save the connection, then open Settings → Home Assistant again.
   - **Pass:** the saved connection opens directly, without a search.
4. Doorbell, with the camera live:
   - Trigger a person detection within a few seconds of a doorbell press.
   - **Pass:** the live picture stays up and its time is extended, with no reconnect.
5. Doorbell and reminder together:
   - Fire a reminder while the doorbell card is up, then press Back within 30 seconds.
   - **Pass:** the reminder appears once, after the card closes.
   - Repeat, waiting more than 30 seconds.
   - **Pass:** the reminder is dropped.

Signing in still needs the long-lived access token typed once. A phone sign-in for Home Assistant is not built yet.

## Your media account (Jellyfin)

Needs **Quick Connect** switched on in the Jellyfin server (Dashboard → General).

1. Open **Settings → Connections → Media server accounts → Adult**.
   - **Pass:** the server is found and a code appears, with no address field.
2. Enter the code in the Jellyfin app on a phone (Settings → Quick Connect).
   - **Pass:** within a few seconds the TV says "You're signed in as …", and the library opens your account.
3. Repeat for **Child**, approving the code from an administrator account.
   - **Pass:** the TV refuses it and asks for a restricted account.
   - Approve from a child account. **Pass:** it is accepted.
4. With Quick Connect switched off on the server:
   - **Pass:** the TV explains this in plain words and offers **Sign in another way**.

## VPN wording (appearance only)

These checks cover only what the status *says*. They are not a protection test. Live TV must stay locked in every case.

1. With NordVPN connected to everything, open Live TV.
   - **Pass:** the sheet says NordVPN is on for this TV and that Live TV stays locked until its protection has been checked.
2. With NordVPN set to leave Unified TV out:
   - **Pass:** the sheet says NordVPN isn't covering this app.
3. Turn NordVPN on and off quickly a few times while Home is visible.
   - **Pass:** the header settles on the true final state within about two seconds.

## Background search

- Start Unified TV with everything already set up.
  - **Pass:** no background search runs, and focus on Home stays instant.
- Start it with nothing set up.
  - **Pass:** Home stays responsive while the search runs, and nothing appears on screen because of it.

## Home: animated light and focus (P05-02, AM-01 to AM-12)

1. Move slowly along the dock. (AM-01, AM-02)
   - **Pass:** each app's light (crimson, dark red, navy with cyan, magenta, violet, peach and pink) crossfades in about half a second from the moment focus moves, with no flash or hard cut; the name and the tile's outline and glow change with it.
2. Hold Right, then tap quickly across the dock. (AM-03)
   - **Pass:** focus keeps up with the remote; the background ends on the last app; nothing launches.
3. Leave one app focused for a minute. (AM-04)
   - **Pass:** the light drifts gently, with no visible loop, jump or pulsing; text stays still and readable.
4. Settings → Appearance → **Keep the Home background still**, then **Reduce motion**. (AM-05)
   - **Pass:** with "still", the drift stops but focus changes still crossfade; with Reduce motion, changes are instant.
5. Look at each of the six apps. (AM-06)
   - **Pass:** clock, status, name, labels and focus outline are readable; record any visible banding (stepped rings in the light).
6. Open an app, return Home; open Settings; open Live TV's locked sheet. (AM-08)
   - **Pass:** the right light for the focused app each time.
7. Turn on **Lightweight rendering**. (AM-10)
   - **Pass:** still light in each app's colours, everything usable.
8. Move an app on the dock. (AM-11)
   - **Pass:** its light follows the app, not the position.
9. Compare with the mockups you supplied. (AM-12)
   - **Record:** what differs.

## Wallpaper (WP-01 to WP-16)

1. Settings → Appearance: check the Wallpaper rows are reachable with the remote and remembered after leaving Settings. (WP-01)
2. Choose **Start: after 2 minutes**, go Home and leave the remote alone. (WP-02)
   - **Pass:** full-screen artwork with a clock; no dock, clock header or VPN icon.
3. Wait for the minute to change. (WP-06)
   - **Pass:** the time updates with no flash.
4. Press Right once. (WP-07)
   - **Pass:** Home appears at once with the same app in focus, and nothing opens. The next press moves focus normally.
5. Press Back, OK and Home in turn to leave the Wallpaper. (WP-07)
   - **Pass:** each one just brings Home back.
6. From the Wallpaper, open Netflix, then press Home. (WP-08)
   - **Pass:** the Wallpaper never covers Netflix.
7. Turn on **Reduce motion** and **High contrast**. (WP-14)
   - **Pass:** no slow zoom; the clock stays readable.
8. Leave the Wallpaper for 30 minutes. (WP-14, OLED)
   - **Pass:** the clock dims; it has moved between pictures.
9. During the Wallpaper, run **Test: someone rings the doorbell** and **Test: smoke alarm** from household setup (use a phone timer and a second remote press, or start them first and wait). (WP-15, SAFE-02)
   - **Pass:** the alarm screen shows over the Wallpaper; afterwards the remote still works.
10. Use **Open original TV interface** after leaving the Wallpaper. (WP-16)

Depth clock (WP-04, WP-05): not testable yet; no picture has a prepared mask. My Photos (WP-03, WP-10, WP-11): not built.

## Music: Now Playing (MUSIC-01)

1. With Amazon Music playing a song, open **Music** on the Unified TV dock.
   - **If it asks for access:** press **Allow in Settings**. If the TV has no such setting, run this on the PC (it is also shown on screen):
     ```powershell
     & $adb -s $tv shell cmd notification allow_listener io.unifiedtv.launcher.devicecheck/io.unifiedtv.preview.MediaWatch
     ```
   - **Pass:** within 5 seconds the screen shows the song's art, title, artist, album and progress.
2. Left/Right along the controls, OK on Play/Pause, Next and Previous.
   - **Pass:** Amazon Music responds; the screen follows.
   - **Record:** whether Shuffle is available.
3. Back.
   - **Pass:** you return to Home with Music in focus.
4. With AirPin LITE ready, cast music from the iPhone and return to Unified TV Home.
   - **Record:** whether the AirPlay glyph brightens with a halo while casting.

## AirPlay indicator (GATE-AIRPLAY-01, AT-64)

1. With AirPin LITE opened once and running, go to Unified TV Home and wait up to two minutes.
   - **Pass:** the AirPlay glyph appears to the left of VPN in the top-right glass group, white and slightly transparent.
   - **Record:** if it never appears, whether your iPhone's Control Centre → Screen Mirroring lists the TV. If the iPhone sees it but Unified TV doesn't, Android can't see the TV's own AirPlay service, and the check needs another method.
2. Force-stop AirPin LITE (Settings → Apps → AirPin LITE → Force stop), return Home and wait up to two minutes.
   - **Pass:** the glyph disappears completely; no grey icon.
3. Restart the TV and open Unified TV without opening AirPin.
   - **Record:** whether the glyph appears, and whether the iPhone can see the TV. That shows whether AirPin starts with the TV.
4. Cast a video from the iPhone, then stop.
   - **Record:** that AirPin takes over the screen and that Back or Home returns to Unified TV.
5. Settings → Connections: the AirPlay line matches what the header shows; **Open AirPin LITE** opens it.

## Films & TV from Home (P05-02, spec 4.6)

1. With the NAS set up, press Down on Home.
   - **Pass:** a row of posters from your chosen collections rises; each poster is its own title's artwork.
2. Move along the posters.
   - **Pass:** the background becomes that title's own poster, softened. A title with no artwork shows a plain dark scene, never another title's picture.
3. OK on a film; OK on a series or folder.
   - **Pass:** the film plays; the series opens on its episodes.
4. Up and Back.
   - **Pass:** both return to the dock with the same app in focus.
5. With no NAS set up, press Down.
   - **Pass:** a single "Set up" card offers to set up your films, and opens setup.

## Apps and your Home dock (P05-03)

1. Open **Apps** from the controls menu.
   - **Pass:** every installed app plus Live TV, Xbox and TV Plus shortcuts, five to a row, focus visible.
2. Hold OK on an app that isn't on Home and choose **Add to Home**.
   - **Pass:** it appears at the end of the dock and is still there after restarting the TV.
3. On Home, hold OK on an app: **Move**, then **Remove from Home**, then **Cancel**.
   - **Pass:** Move reorders; Remove takes it off and it is still in Apps; Cancel changes nothing.
4. Switch room profile.
   - **Pass:** each room keeps its own dock.
5. Open Live TV from Apps.
   - **Pass:** the same Live TV protection check applies as from Home.

## Unified TV as Home (spec 29, RC and HRC rows)

1. Install the 0.5 build, then press Home.
   - **Pass:** the TV's own Home appears. Installing changed nothing.
2. **Settings → TV & performance → Make Unified TV the Home screen.**
   - **Pass:** Android itself asks which Home to use. Note on this firmware whether it is a role request, a Home settings page or the Home chooser.
3. Choose the TV's own Home instead.
   - **Pass:** it says nothing changed, and the Home button still opens the TV's own interface.
4. Repeat 2 and choose Unified TV. Then press Home from Netflix, from playback, after standby and after a reboot.
   - **Pass:** each time, Unified TV's main screen appears with the same app in focus.
5. Press Back repeatedly on the main screen.
   - **Pass:** it stays on Unified TV.

## Open original TV interface (spec 30, OI rows)

1. **Settings → TV & performance → Open original TV interface → Open original interface for now.**
   - **Pass:** the TV's own interface opens, or Unified TV says plainly that this TV doesn't allow it. Home still returns to Unified TV.
2. **Make original interface the default** and confirm twice.
   - **Pass:** Home now goes to the TV's own interface, and Unified TV can be chosen again from step 2 above.

## Installing and updating (P05-01)

1. Run `python3 preview/tools/install_preview.py --check <APK>` against the TV with the 0.4 preview installed.
   - **Pass:** it reports the certificates and versions and installs nothing.
2. Install over 0.4 with the tool.
   - **Pass:** saved connections are still there afterwards; the record says data was kept and Home was unchanged.
3. Try an APK signed with a different key.
   - **Pass:** the tool refuses and changes nothing on the TV.

## Smoke and carbon monoxide alarm (SAFE-01 to SAFE-10)

No detectors are connected yet, so start with the built-in test. Household alerts need overlay permission (**Settings → Notifications → Doorbell, reminders & room setup → Grant optional alert overlay permission**). The test buttons are in the same screen, under **Test the alarm screen**. Every test entry is labelled TEST.

1. **Test: smoke alarm in the kitchen**, while Home is showing. (SAFE-01)
   - **Pass:** a full red screen shows "TEST · SMOKE ALARM — Kitchen", "Since HH:mm", "Get everyone out, stay out, and call the fire service." and an **Acknowledge** button with focus.
2. Press Back. (SAFE-09)
   - **Pass:** nothing is acknowledged; focus returns to Acknowledge.
3. **Test: carbon monoxide alarm in the hallway** without acknowledging the first. (SAFE-04)
   - **Pass:** two separate entries, oldest first, each with its own Acknowledge.
4. Acknowledge the smoke entry only.
   - **Pass:** the carbon monoxide entry stays, with its Acknowledge.
5. Acknowledge the carbon monoxide entry.
   - **Pass:** the full screen goes; a red banner at the top stays: "… still active". It never takes focus.
6. **Test: someone rings the doorbell** while the banner shows. (SAFE-03)
   - **Pass:** no camera card. The banner adds "Doorbell rang at HH:mm: shown when the alarm ends".
7. **Test: a smoke detector goes offline**. (SAFE-05)
   - **Pass:** a separate amber notice "TEST · Smoke detector offline: Landing", never styled as an alarm.
8. **End the alarm test**.
   - **Pass:** banner and screen go. If the doorbell test was under 45 seconds ago the doorbell card appears; otherwise a note "Doorbell rang at HH:mm during the alarm."
9. Repeat step 1 while Netflix (or another app) is playing. (SAFE-06)
   - **Record:** whether the alarm screen appeared over the app, and whether the app paused, lowered its sound or carried on. Either is acceptable to record; only a missing alarm screen is a failure.
10. Repeat step 1, then put the TV in standby and wake it. (SAFE-07)
    - **Record:** what is on screen after waking.

**With Home Assistant (optional, no detector needed):** add a template binary sensor with `device_class: smoke` driven by a helper toggle, start household alerts, and turn the helper on and off. **Pass:** the same screens without the TEST label, and the time shown matches Home Assistant's. Turning Home Assistant off during an alarm must show "Connection to Home Assistant lost: alarm status unknown", never clear the alarm. (SAFE-08, SAFE-10)
