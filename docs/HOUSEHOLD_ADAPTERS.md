# Household adapters: experimental test setup

Configure Settings > Connections > Home Assistant first, then Settings > Household alerts & room actions. All entity/script names and credentials stay encrypted on the TV. No setup is sent to GitHub. No network discovery is performed.

## Doorbell and person detection

Enter two real `binary_sensor` entities: one reflecting a doorbell press, and one reflecting a confirmed **person** detection, not general motion. Enter the matching `camera` entity. The current adapter handles **off to on** edges only; Ring installations exposing event entities instead need a household HA template/helper automation that produces those edges. Never equate motion with person detection. Verify Ring subscription/Smart Alert support independently.

The explicitly started foreground service authenticates against HA's WebSocket API, subscribes to state changes, deduplicates repeated events and requests the configured camera's HLS stream through `camera/stream`. The camera must support HA streaming. Only same-origin media requests are permitted; other hosts, redirects, invalid TLS and unconsented HTTP are refused. Media3 logging is disabled to keep private stream paths out of library diagnostics.

Enable the TV's optional overlay permission, then deliberately start Household alerts. A permanent system notification exposes connection state, Dismiss and Stop controls. The service is not a boot receiver and does not promise survival across reboot, firmware sleep policy or process termination. Test and restart it explicitly when necessary. No fully powered-off TV is woken by this APK.

Ring takes priority over reminder toasts and shows Connecting before video arrives. It shows a live-stream label only after a first decoded frame; buffering/error states replace that label. The silent camera card dismisses after 45 seconds or with its button/Back, leaving underlying activity selection intact. It does not deliberately pause another app or change global volume. Cross-app focus, protected-video overlays, sound restoration, standby and exact return behaviour **must be tested** on each TV. No recording is created.

## Frame native mode and Sony Xbox

Select `tcl`, `sony` or `frame` as the room capability profile. Xbox is Sony-only; Samsung TV Plus is Frame-only. Dock changes take effect when returning Home. Buttons invoke only explicitly named HA scripts through the configured authenticated connection. An HTTP success is an action request, not evidence that HDMI changed.

For Frame Ring entry, supply an owner-reviewed script which records the current native mode/input and switches to the Android streamer's input when appropriate. The return script must restore that captured state, including Art Mode when that was the prior state. Never blindly return to Art Mode during native TV Plus viewing, and never wake a fully off TV without separate owner consent. The APK cannot implement or render above Tizen natively. TV/streamer models, HA integration support, CEC and exact entity actions remain setup prerequisites. No unverified Samsung endpoint is invented here.

## Reminder bridge

An authorised HA automation can forward an actual due reminder as a `unified_tv_reminder` event with `text` and an optional `room` (`tcl`, `sony`, `frame`). The source must expose the real reminder text; Alexa polling delays remain a source limitation. The app rejects events older than 30 seconds, truncates text to 240 characters and drops reminders while Ring owns the alert layer. The toast lasts seven seconds, is not focusable and does not pause playback. No toast can overlay native Tizen through an Android window.

No Alexa source automation has been installed by this development work. Do not claim delivery until a real due reminder passes end to end.

## Fixtures and deliberate voice requests

An optional HA calendar entity supplies `start_time`/`end_time` for Tottenham fixtures. The app checks the UK matchday, shows kick-off time or Live, and hides after a 15-minute grace period. It never renders calendar message text, scores or results. The calendar must cover all relevant competitions and postponements. Refresh is every 15 minutes while Home is active; failed requests hide the card. The Live TV action remains blocked by the same unverified-protection policy.

Ask your TV offers local app/profile/camera commands, remote keyboard input and optional system speech recognition with `en-GB`. Recognition depends on the TV's installed provider and may use its cloud. Optional deliberate Assist requests go to the user's configured HA conversation agent, not an embedded OpenAI key. Keep that agent's exposed controls appropriately scoped; do not expose a Live TV launch action until VPN packet protection passes. OpenAI realtime media discovery and a trusted relay are still absent. Child media profiles do not secure HA, Android or external apps.

## Sources

Protocol and camera command follow the [HA WebSocket API](https://contributors.home-assistant.io/core/internals/websocket-api/) and [official camera implementation](https://github.com/home-assistant/core/blob/dev/homeassistant/components/camera/__init__.py). Media decoding follows [Media3 format support](https://developer.android.com/media/media3/exoplayer/supported-formats); actual device decoder, HDR, audio and subtitle capabilities still govern playback.
