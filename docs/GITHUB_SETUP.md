# GitHub setup checklist for maintainers

Repository settings that help people find Unified TV. These need a maintainer with admin rights; nothing here can be changed by a pull request. Keep every claim honest: planned features stay labelled as planned ([AGENTS.md](../AGENTS.md) rule 1).

## 1. Detach the fork

GitHub hides forks from repository search by default, and a fork with fewer stars than its parent rarely appears in results at all.

- [ ] Ask GitHub Support to **detach the fork** (Support → "Detach a fork"; the repository keeps its history).
- [ ] Afterwards, check that every credit and licence notice is still in place: [LICENSE](../LICENSE), [launcher-ui/NOTICE](../launcher-ui/NOTICE), [launcher-ui/LICENSE.free-my-tv](../launcher-ui/LICENSE.free-my-tv), [CREDITS.md](../CREDITS.md) and the credit section of [README.md](../README.md). Detaching changes GitHub's fork link, not our obligation to credit CleanYourTV, CleanYourTV UI and Free My TV.

## 2. Description and website

Settings → General (or the ⚙ next to **About**). Suggested description (under 350 characters):

> Calm, advert-free home screen for Android TV & Google TV: one familiar experience on every TV in the house (Sony and TCL on Android, Samsung Frame on Tizen), with optional reversible cleanup and planned Home Assistant alerts. Early-development fork of CleanYourTV.

- [ ] Paste the description.
- [ ] Leave the website empty until GitHub Pages (section 6) exists.

## 3. Topics

About → ⚙ → Topics. GitHub allows 20:

`android-tv` `google-tv` `android-tv-launcher` `tv-launcher` `home-screen` `smart-tv` `debloat` `bloatware-removal` `adb` `sony-bravia` `tcl` `samsung-tv` `home-assistant` `ring-doorbell` `smart-home` `nas` `media-library` `privacy` `family` `open-source`

- [ ] Add all 20.

## 4. Social preview

- [ ] Settings → General → **Social preview** → upload [`docs/marketing/social-preview.png`](marketing/social-preview.png) (1280×640). This is the image shown when the repository link is shared.

## 5. Community features

- [ ] Settings → General → Features → enable **Discussions**, with categories: Announcements, Q&A, Ideas (link [docs/IDEAS.md](IDEAS.md)), Show your setup, Real-TV test reports.
- [ ] Keep **Issues** on, with the labels suggested in [CONTRIBUTING.md](../CONTRIBUTING.md).
- [ ] Keep the CI badge at the top of the README green: the `CI` workflow runs the Python suite, the launcher build and size gate, and the launcher-ui build on pushes to `main` and on pull requests.

## 6. Releases and pages

- [ ] Once the current work is merged, publish a **release** from [CHANGELOG.md](../CHANGELOG.md). Say clearly what is inherited, what is new and what is untested. Do not attach an APK presented as a finished Unified TV build.
- [ ] Before the first durably signed preview release, create the preview signing key on your own computer with `python3 preview/tools/make_preview_key.py --github`, which stores the four `UNIFIEDTV_PREVIEW_*` repository secrets. Back up its folder; never commit it. See [PREVIEW_0_5_RELEASE.md](PREVIEW_0_5_RELEASE.md#signing-and-updates).
- [ ] Optional: enable **GitHub Pages** from the `/docs` folder. Later, generate one page per TV model from `examples/` (model, Android version, what the community list covers, how to restore) so long-tail searches such as "TCL C645 remove adverts" can find it. Keep the inherited lists credited to CleanYourTV and its contributors.

## 7. Outreach

Only after the README and status table are accurate and CI is green:

- [ ] Ask the CleanYourTV maintainer whether Unified TV can be listed in upstream's `SHOWCASE.md` (their repository, their decision).
- [ ] Post in r/AndroidTV, r/googletv and r/homeassistant, and on the XDA forums, with screenshots labelled "concept" until real ones exist.
- [ ] Suggest the project to relevant "awesome" lists (Android TV, Home Assistant) once there is a release.
- [ ] Show HN on the first real release, not before.

Never share household screenshots that show names, addresses, network names, camera pictures or account details.
