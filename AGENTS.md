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

## Build, test and CI (inherited tooling)

This section describes the inherited CleanYourTV and CleanYourTV UI tooling as it stands today, so agents run the same checks CI does. It is **not** a Unified TV installation procedure, and it does not replace any rule above.

### If you were asked to clean up a real TV

[PROMPT.md](PROMPT.md) is the inherited upstream CleanYourTV prompt, **not** a Unified TV procedure. Under rule 9, first follow [docs/INSTALLATION_AND_RECOVERY.md](docs/INSTALLATION_AND_RECOVERY.md): read the model, firmware and package states, get explicit consent for each change, record backups and verify rollback. Only then, and only for the specific procedure the person asked for:

- Use the existing tools instead of writing new ones. `tools/disable.sh` (twin `tools/disable.ps1`) writes a recovery record through `tools/recovery.py` before the first change, and `tools/restore.sh` (twin `tools/restore.ps1`) replays it.
- Disable, never uninstall: no `pm uninstall`, no root, nothing flashed. Never disable Android core packages or start a factory/test package.
- Keep the Home guard: the stock launcher is disabled only after another Home is installed and confirmed.
- Close ADB afterwards and confirm with `tools/adb-closed.py`.
- Backups (`backup/`), screenshots (`shots/`), notes (`work/`) and ADB keys (`tools/adbkey/`) are gitignored and must never be committed.

### Repo map

| Path | What it is |
| --- | --- |
| `README.md` | Unified TV's front page. People and agents should also read this file, [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). |
| `README.cleanyourtv.md` | The inherited CleanYourTV front page: TV table, Tools table, launcher settings, themes and press card. Many doc tests read it. `README.ja.md`, `README.vi.md` and `README.zh-CN.md` translate it; `README.pt-BR.md` translates `docs/upstream/CLEANYOURTV_UI_README.md`. |
| `PROMPT.md` | Inherited upstream agent prompt for cleaning a TV. Read its banner first. |
| `examples/` | One folder per tested TV model (`debloat.txt`, sometimes notes and a `config.json`), indexed in `examples/README.md`. Upstream evidence, not Unified TV support claims. |
| `launcher/` | Inherited reference launcher `com.gathin.tvhome`: plain Java, `minSdk` 26, no `INTERNET` permission. |
| `launcher-ui/` | Inherited CleanYourTV UI app `io.cleanyourtv.ui`, built without Gradle by `launcher-ui/build.sh`. |
| `preview/` | One codebase, two separately installed apps (Gradle flavours): the Unified TV Preview `io.unifiedtv.preview` (no HOME intent at all; never uninstalled or re-signed) and the Unified TV launcher `io.unifiedtv.launcher` (a HOME entry that ships switched off until the owner chooses it; signed with the owner's durable key, see [docs/SIGNING.md](docs/SIGNING.md)). Read [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) and [docs/ACCEPTANCE_REGISTER.csv](docs/ACCEPTANCE_REGISTER.csv) before changing it. |
| `addons/` | Optional module-card apps, each its own Gradle project, never built into the launcher. |
| `themes/`, `tools/`, `tests/` | Theme folders (keys in `themes/README.md`); the ADB client `tools/tv.py` and scripts; the Python `unittest` suite. |
| `docs/` | Unified TV design docs (`docs/PRODUCT_SPEC.md`, `docs/HOME_VIEW.md`, `docs/IDEAS.md`), `docs/TROUBLESHOOTING.md`, `docs/devices/` and the inherited [workflows](#workflows-inherited). |
| `.github/workflows/` | `ci.yml` (tests, launcher build and APK size gate, standalone preview APK, launcher-ui build), `android-preview-release.yml` (prerelease from an `android-preview-v*` tag), `addons.yml`, `maintainer-bot.yml`, `sync-upstream.yml` (manual-review guidance only). |
| `.github/maintainer/MAINTAINER.md` | Inherited upstream review policy, kept for reference. Review it before adopting it for Unified TV. |

### Checks CI runs

| Check | Command or rule |
| --- | --- |
| Python suite | `python3 -m venv tools/venv`, `tools/venv/bin/pip install -r tools/requirements.txt`, then `python3 -m unittest discover -s tests -v`. On pushes to `main`, CI sets `CLEANYOURTV_STRICT=1`, which also checks the TV-model count in `README.cleanyourtv.md`. |
| Launcher | JDK 17, Android SDK platform 34, build-tools 34.0.0. In `launcher/`: `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`. Lint stays at zero errors. Optional release signing: set all four of `UNIFIEDTV_KEYSTORE`, `UNIFIEDTV_KEYSTORE_PASSWORD`, `UNIFIEDTV_KEY_ALIAS` and `UNIFIEDTV_KEY_PASSWORD` (Gradle properties or environment variables); otherwise the debug key signs it. Never commit a keystore. |
| APK size gate | `launcher/app/build/outputs/apk/release/app-release.apk` must be under 524,288 B (512 KB). Do not change the gate or lower `minSdk` (26) in a feature PR. The size rule applies only to the inherited lightweight launcher, not the separate media preview. |
| Android Preview | JDK 17, Android SDK platform 35, build-tools 35.0.0. `bash preview/build.sh` runs both apps' unit tests and lint, builds the preview, the launcher and a launcher review build (`.devicecheck`), verifies each signature and package, and runs `preview/tools/home_check.py`: the preview may have no HOME intent; the launcher's must ship switched off. Keep those identities and the secret-storage protections, never put a key in the repository or CI logs, and document every untested integration. |
| Launcher UI | In `launcher-ui/`: `bash build.sh` with the example settings (no TMDB key, throwaway signing key) must produce a non-empty `CleanYourTV-UI.apk`. |
| Add-ons | `addons.yml` builds only the add-ons a change touches: `./gradlew --no-daemon testReleaseUnitTest assembleRelease` when the add-on has unit tests, otherwise `assembleRelease`. |

Never commit an APK or other binary, a secret, an IP address, a serial number or an ADB key. A real-device claim needs the model, firmware and result; say **Not tested** otherwise.

### Workflows (inherited)

Step-by-step recipes inherited from CleanYourTV, kept in `docs/workflows/`. Check each against the Unified TV rules above before following it.

- [`tv-list.md`](docs/workflows/tv-list.md): add or update a tested TV's debloat list and its table rows.
- [`launcher-switch.md`](docs/workflows/launcher-switch.md): add an off-by-default switch to `launcher/`, within the size gate.
- [`translation.md`](docs/workflows/translation.md): add a launcher language or fill missing strings.
- [`theme.md`](docs/workflows/theme.md): add a theme from existing keys, with a screenshot from a real TV.
- [`addon.md`](docs/workflows/addon.md): add a module-card app that never touches `launcher/`.
- [`showcase.md`](docs/workflows/showcase.md): list a project that lives in its own repository.
- [`press.md`](docs/workflows/press.md): add an article and update the press counts.
- [`docs-notes.md`](docs/workflows/docs-notes.md): add a troubleshooting entry or a `PROMPT.md` fix, keeping pinned facts.
- [`perf-ab.md`](docs/workflows/perf-ab.md): check a launcher change for performance regressions with `tools/bench.py`.
