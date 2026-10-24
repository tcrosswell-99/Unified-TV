# Working on Unified TV

This independent GitHub fork is **not** the original CleanYourTV project. Read [README.md](README.md), [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md), [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) and [docs/ROADMAP.md](docs/ROADMAP.md) before changing code.

**North star: Content first, underlying technology hidden.**

## Code map and status

- `launcher-ui/`: inherited CleanYourTV UI Android application; candidate starting point for our premium home screen.
- `launcher/`, `tools/`, `examples/`: inherited CleanYourTV lightweight launcher and optional cleanup/recovery tooling.
- `docs/PRODUCT_SPEC.md`: design and household requirements. It describes goals, **not features already implemented**.
- `docs/COMPATIBILITY.md`: supported versus unproven capabilities.
- `README.cleanyourtv.md` and `docs/upstream/CLEANYOURTV_UI_README.md`: upstream reference docs, not Unified TV's release instructions.

## Implementation requirements

1. Never claim Ring interrupts **native Samsung Art Mode**, or that Amazon Music or GPT voice streaming works, without real-device tests.
2. Platform separation is deliberate: Android launcher on Sony/TCL and compatible external streamers; Samsung native Tizen behaviour needs its own verified bridge.
3. Do not create a VLC tile. The NAS is a poster-first library; VLC is its underlying playback mechanism.
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
