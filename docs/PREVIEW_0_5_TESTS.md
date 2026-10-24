# Preview 0.5: on-TV checks

Preview 0.5 is code-checked and unit-tested only. These are the checks that need the TCL (Android TV 11), the NAS and Home Assistant. Record each result in [FEATURE_STATUS.md](FEATURE_STATUS.md) with the build, date and outcome. Say **Not tested** for anything skipped.

Use a fresh install of the 0.5 test build, without clearing the 0.4 data, so the existing saved connections are kept. Run each check twice: once with NordVPN off, once with NordVPN on.

## Your films and series (NAS)

1. With no library set up, start Unified TV, wait ten seconds on Home, then open **Your Films & TV**.
   - **Pass:** with one media server on the network, it goes straight to its folders, with no search screen and no list.
   - **Pass:** with several, a list of their names appears.
   - **Pass:** no address is shown or asked for.
2. Open each of the three collections and add it. Save. Leave and come back.
   - **Pass:** the saved collections open straight away.
3. Open a folder with more than 24 entries that mixes music or photos with videos.
   - **Pass:** **Show more** appears until the last video, and every video is reachable.
4. Check the cards.
   - **Pass:** titles with server artwork show their own poster and year.
   - **Pass:** titles without artwork show a plain card, never another title's picture.
5. With NordVPN on: repeat step 1.
   - **Pass:** the server is still found.
   - **If it isn't:** record whether NordVPN's "allow devices on your home network" setting was on.

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
