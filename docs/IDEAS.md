# Feature ideas

> **These are ideas, not features.** Nothing here is promised, scheduled or working unless its status says so, and even "Partly inherited" means only that related code exists from CleanYourTV, CleanYourTV UI or a community add-on. Every idea is optional and off by default. Agreed work moves to the [Roadmap](ROADMAP.md); requirements live in the [Product Spec](PRODUCT_SPEC.md); alert order is in the [Home view design note](HOME_VIEW.md).

**Status key:** **Idea**: worth discussing, no design yet. **Planned**: in the Roadmap or Product Spec. **Partly inherited**: related code already in this repository.

Household data (addresses, tokens, names, photos, teams, routes) is always configured on the TV or kept locally, never committed to this repository.

## Home and safety

| Idea | Status | Data source |
| --- | --- | --- |
| Smoke or carbon monoxide alarm takes over the screen with plain instructions | **Built in Preview 0.5, not tested on a TV.** Owner decision OD-07, 25 October 2026: ranks above the doorbell | Home Assistant alarm entities |
| Doorbell: live picture when someone presses or is detected | Planned (needs real-TV testing) | Ring integration in Home Assistant |
| Baby monitor picture-in-picture on sound or movement | Idea | A local camera or sound sensor via Home Assistant |
| Door or garage left open at bedtime | Idea | Door and garage sensors, a bedtime schedule |
| Parcel delivered | Planned | Doorbell/camera events |
| Washing machine or dishwasher finished | Idea | Appliance or smart-plug power sensors |
| Someone arriving home | Idea | Presence or device-tracker entities |
| Bin day reminder the evening before | Planned | A calendar sensor |
| Smart lock state (shown only; a TV remote never unlocks a door) | Partly inherited | Lock entities; [`addons/smart-home`](../addons/smart-home/README.md) already shows locks read-only |
| Camera alerts over playback | Partly inherited | [`addons/frigate-glance`](../addons/frigate-glance/README.md) with Frigate |

## Family

| Idea | Status | Data source |
| --- | --- | --- |
| Profiles, with a kids mode that shows only chosen apps | Idea | Local settings on each TV |
| Child lock on editing the dock | Planned | Local PIN ([app customisation](APP_CUSTOMISATION.md)) |
| Bedtime wind-down: dimmer theme, quieter volume, a gentle "time for bed" | Partly inherited | [`addons/quiet-hours`](../addons/quiet-hours/README.md), [`addons/sleep-timer`](../addons/sleep-timer/README.md) |
| Family calendar glance for today | Idea | A Home Assistant calendar |
| Birthdays with a family photo | Idea | Photos stored locally on the NAS or TV only, never in Git |
| Simple mode for grandparents: fewer, bigger tiles, plain words | Idea | Local settings |

## Sport and events

| Idea | Status | Data source |
| --- | --- | --- |
| Kick-off countdown with which app or channel shows the match | Planned | A fixtures calendar, configured per household |
| "It's starting" nudge a few minutes before kick-off | Idea | Same fixtures source |
| Opt-in scores with a spoiler shield (off by default, hidden during recordings) | Idea | A fixtures/scores sensor |
| Formula 1 race-weekend mode | Partly inherited | The launcher's race calendar, from [`tools/race_calendar.py`](../tools/race_calendar.py) |
| Rugby and cricket fixtures | Idea | A fixtures calendar |

## Content

| Idea | Status | Data source |
| --- | --- | --- |
| New episode of a series you follow | Idea | The NAS library or a media server |
| Film night: a random pick from your own films | Idea | The NAS library |
| TMDB watchlist and favourites | Partly inherited | CleanYourTV UI's TMDB sign-in ([guide](upstream/CLEANYOURTV_UI_README.md)) |
| Continue watching | Idea | Only where an app or media server exposes real progress; never guessed |

## Ambient and practical

| Idea | Status | Data source |
| --- | --- | --- |
| Rain on the way | Idea | A weather sensor |
| Weather glance | Partly inherited | [`addons/weather`](../addons/weather/README.md) |
| Next trains or buses, commute time | Idea | A travel sensor configured locally |
| Cheapest electricity window today (for example Octopus Agile) | Idea | An energy-price sensor |
| Now playing on Sonos or Spotify | Idea | Media-player entities |
| Photo frame from your own library (for example Immich or Google Photos) | Partly inherited | [`addons/photo-screensaver`](../addons/photo-screensaver/README.md) shows local pictures; other libraries are ideas |
| Internet down, NAS nearly full | Idea | Router and NAS sensors |
| Remote control battery low | Idea | Battery level where the TV exposes it |

## Control

| Idea | Status | Data source |
| --- | --- | --- |
| Cinema mode: lights dim when a film starts | Idea | Home Assistant light scenes |
| Console wake and switch to its input | Planned | Home Assistant console and TV integrations (Sony reference) |
| Soundbar volume from the TV remote | Idea | Media-player entities, where the device allows it |
| Home Assistant panel for scenes, lights and climate | Partly inherited | [`addons/smart-home`](../addons/smart-home/README.md) |

## Adding an idea

Open a [Discussion](https://github.com/tcrosswell-99/Unified-TV/discussions) or an issue. Say who in the household it helps, where the data comes from, how it fails quietly, and which [Home view priority](HOME_VIEW.md#alert-priority) it would have.
