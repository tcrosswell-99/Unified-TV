# Workflow: Add a theme

**When to use this:** a new look that the keys in `themes/README.md` already carry.

1. Create `themes/<name>/config.json` using only keys from the table in `themes/README.md`;
   `themes/default/config.json` is the starting point.
2. Add `wallpaper.jpg` or a `wallpapers/` folder if it uses them: dark, 1920x1080, under about 300 KB each,
   and art you have the right to use.
3. Push it to a real TV with `tools/theme.sh themes/<name>` and take `screenshot.jpg` of Home with the focus
   on the dock: a JPEG at 1280x720 (16:9), at most 300 KB. Not a mock-up, not an emulator.
   `tests/test_themes.py` fails without it.
4. Add a row to the table in `themes/README.md` showing the screenshot, like the others.
5. A look that needs a new key is a launcher switch first, in its own PR.

Always push with the theme tools, never by copying `config.json` by hand. On **Android 10**, the launcher only applies a `config.json` written through the theme tools (`tools/theme.sh` or `tools/theme.ps1`, which write it through the permission-gated `ThemeProvider` with `content write`). A file copied in with a plain `adb push` or `cp` is ignored there and the last trusted theme stays. Android 8 and 9 (no `content write`) and Android 11+ behave as before. *(Unified TV note: code-checked, not yet verified on a real Android 10 TV.)*

Then run the tests: `python3 -m unittest discover -s tests -v`.
