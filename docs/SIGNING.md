# Signing and key custody

Android only installs an update over an app signed with the **same key**. Lose the key and that app can never be updated again; leak it and someone else could publish an "update". This page is the procedure for keeping the Unified TV signing key safe. It must be followed before the first launcher release (owner decision OD-08, 25 October 2026; tests SIGN-01 to SIGN-08 in [ACCEPTANCE_REGISTER.csv](ACCEPTANCE_REGISTER.csv)).

## Two apps, two identities

| App | Package | Signed with | Why |
| --- | --- | --- | --- |
| Unified TV (the launcher) | `io.unifiedtv.launcher` | **The owner's durable key**, from its first install | It will become the TV's Home, so every version must update in place and keep its settings. |
| Unified TV Preview | `io.unifiedtv.preview` | Unchanged: a build machine's debug key | It stays installed exactly as it is. The preview on the TCL was signed by a throwaway CI key that no longer exists, so nothing can ever update it; it is never uninstalled to "fix" that. |
| Review builds | `io.unifiedtv.launcher.devicecheck`, `io.unifiedtv.preview.devicecheck` | A debug key | For trying a build on a TV. They install beside the real apps and can be removed without touching them. |

Why not move the preview to the new key? Android has supported key rotation since Android 9, so the TCL's Android 11 is not the obstacle. Rotation needs the **old** private key to sign the change, and the preview's old key is gone. A separate launcher package with the durable key from day one avoids any reinstall.

Samsung Tizen uses its own author and distributor certificates; nothing here applies to the Frame.

## Creating the key (once, on the owner's computer)

Agents never create, see or store the key. On your own Windows PC, in PowerShell:

```powershell
cd $HOME
Invoke-WebRequest https://raw.githubusercontent.com/tcrosswell-99/Unified-TV/claude/modest-davinci-dq3wrc/preview/tools/make_preview_key.py -OutFile make_preview_key.py
python3 make_preview_key.py
```

It needs Java's `keytool`. If `winget` cannot download Java, fetch it directly:

```powershell
Invoke-WebRequest https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip -OutFile jdk17.zip
Expand-Archive jdk17.zip -DestinationPath $HOME\jdk17 -Force
$env:JAVA_HOME = (Get-ChildItem $HOME\jdk17 -Directory)[0].FullName
```

The tool:

- makes `C:\Users\<you>\UnifiedTV-signing\` (outside any repository) holding `unifiedtv-preview.p12` (the key), `password.txt`, `keystore.base64.txt` and `secrets.txt`, readable only by you;
- generates a long random password; it never appears on a command line;
- prints the certificate's SHA-256 fingerprint (public; safe to share);
- refuses to replace a key that already exists.

The file and secret names say "preview" because they were created before the split; they sign the launcher.

## Backup (do this straight away)

Keep **two** copies, in different places:

1. **A password manager entry** (for example 1Password, Bitwarden or KeePass) named "Unified TV signing key", with `unifiedtv-preview.p12` attached and the password from `password.txt` in the password field. Note the fingerprint in the entry.
2. **An offline copy**: the whole `UnifiedTV-signing` folder on a USB stick kept somewhere other than the PC, for example with other important documents.

Check a backup can actually be opened (it asks for the password):

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore E:\UnifiedTV-signing\unifiedtv-preview.p12 -storetype PKCS12
```

**Pass:** it lists `unifiedtv-preview` with the same SHA-256 fingerprint. Repeat this before every release and at least every six months.

## GitHub (so tagged releases are signed)

Add four repository secrets under **Settings → Secrets and variables → Actions** (or run the tool with `--github` if the GitHub CLI is installed):

| Secret | Value |
| --- | --- |
| `UNIFIEDTV_PREVIEW_KEYSTORE_BASE64` | the whole contents of `keystore.base64.txt` |
| `UNIFIEDTV_PREVIEW_KEYSTORE_PASSWORD` | the password |
| `UNIFIEDTV_PREVIEW_KEY_ALIAS` | `unifiedtv-preview` |
| `UNIFIEDTV_PREVIEW_KEY_PASSWORD` | the same password |

Only the tag-triggered release workflow reads them. Ordinary CI never does: its builds are test-signed. The workflow decodes the key into the runner's temporary folder and deletes it at the end of the job; it never prints a secret. Every release includes `SIGNING.txt` with the launcher's certificate fingerprint, which must match yours.

## Never

- Never commit the key, the password or `keystore.base64.txt` (`*.p12`, `*.jks`, `*.keystore` and `*.pfx` are git-ignored as a safety net).
- Never paste the password or key into a chat, issue, pull request, email or screenshot. An agent never needs them.
- Never uninstall or clear a configured app to get round a signature mismatch. `preview/tools/install_preview.py` refuses a mismatch and a downgrade.

## If something goes wrong

- **The PC is lost, but a backup exists:** restore the folder from the password manager or USB copy and carry on.
- **Every copy of the key is lost:** the installed launcher can never be updated. The only way forward is a new package identity and a fresh setup on each TV. That is why there are two backups.
- **The key may have leaked** (for example, pasted somewhere public): delete the four GitHub secrets at once. Then create a new key and rotate the launcher to it with `apksigner rotate` (APK Signature Scheme v3), signing the change with the old key. This works on Android 9 and later, including the TCL. Ask for this to be planned before doing it.
- **A release says "test-signed":** the secrets were missing. Do not install that launcher as the household launcher.

## Before the first launcher release (checklist)

1. Key created; fingerprint written down.
2. Both backups made and the `keytool -list` check passes on the backup.
3. Four GitHub secrets added.
4. A tagged release's `SIGNING.txt` shows "durable" and your fingerprint (SIGN-02, SIGN-07).
5. On the TCL: install that launcher, set something up, install the next version over it, and confirm the settings survived (SIGN-03). Record the result in the register.
