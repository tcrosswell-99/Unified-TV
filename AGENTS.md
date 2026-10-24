# Working on Unified TV

This independent GitHub fork is **not** the original CleanYourTV project. Read [README.md](README.md), [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md), [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) and [docs/ROADMAP.md](docs/ROADMAP.md) before changing code.

**North star: Content first, underlying technology hidden.**

For separately owner-authorised cleanup only, consult the inherited `PROMPT.md`; it is not part of preview setup. Never run it automatically.

## Repo map

- `launcher-ui/`: inherited CleanYourTV UI Android application; candidate starting point for our premium home screen.
- `launcher/`, `tools/`, `examples/`: inherited CleanYourTV lightweight launcher and optional cleanup/recovery tooling.
- `docs/PRODUCT_SPEC.md`: design and household requirements. It describes goals, **not features already implemented**.
- `docs/COMPATIBILITY.md`: supported versus unproven capabilities.
- `README.cleanyourtv.md` and `docs/upstream/CLEANYOURTV_UI_README.md`: upstream reference docs, not Unified TV's release instructions.

## Implementation requirements

1. Never claim Ring interrupts **native Samsung Art Mode**, or that Amazon Music or GPT voice streaming works, without real-device tests.
2. Platform separation is deliberate: native Android on Sony/TCL and native Tizen Web Application on Samsung Frame; native Samsung launch and Art Mode behaviour needs a verified household coordinator.
3. VLC is excluded. The NAS is a poster-first library using embedded Media3 on Android and AVPlay on Tizen, with supported private authentication.
4. Always use contextual artwork for a highlighted app, film or programme. Never display an unrelated background and call it content aware.
5. Focus latency and device recovery outrank visual effects. Reduced graphics must retain UX parity on TCL and ageing Sony.
6. All household integrations are optional; never hardcode IPs, tokens, personal paths, Ring/child/family images, or preferences into public source.
7. Ring takes visual priority over Alexa toasts, voice sessions and background transitions.
8. Guard Live TV with verified NordVPN behaviour. A status icon alone is not a kill switch.
9. Never run or propose blanket bloatware cleanup. Read device firmware and package states, obtain explicit user consent, record backups and verify rollback.
10. Update the public docs, tests, copyright/licence information and compatibility table for every user-visible change.

## Development process

Keep feature branches focused. Before opening a PR, state what was verified and what remains untested. Preserve original commit history, MIT notices, Free My TV notices and all bundled third-party licences. Don't import code from TV Detox, CleanFrame or design showcases without auditing attribution and licence suitability.

Avoid running inherited install/debloat prompts or ADB scripts on a real television unless the user explicitly requests that specific procedure and recovery is established. The repository's inherited workflows must be reviewed before being adopted as Unified TV automation.

## Validation and inherited references

Run `python3 -m unittest discover -s tests -v` for repository tools/docs. CI uses `CLEANYOURTV_STRICT` for push checks. In `launcher/`, the inherited check is `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`. Its APK limit is 524,288 B (512 KB), and its minSdk is 26. That size rule applies only to the inherited lightweight launcher, not the separate media preview.

Preview checks use the same wrapper with the preview project: unit tests, lint and APK assembly. Keep the non-HOME identity and secret-storage protections. Document every untested integration.

Inherited tooling references: `tools/disable.sh`, `examples/README.md`, `.github/maintainer/MAINTAINER.md`. They do not authorise cleanup or automated moderation.

Optional workflow references (use only when applicable and authorised):
- [addon](docs/workflows/addon.md)
- [docs-notes](docs/workflows/docs-notes.md)
- [launcher-switch](docs/workflows/launcher-switch.md)
- [perf-ab](docs/workflows/perf-ab.md)
- [press](docs/workflows/press.md)
- [showcase](docs/workflows/showcase.md)
- [theme](docs/workflows/theme.md)
- [translation](docs/workflows/translation.md)
- [tv-list](docs/workflows/tv-list.md)
