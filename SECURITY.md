# Unified TV security policy

Unified TV runs close to household televisions, local networks and potentially personal photos, a media NAS, Ring cameras and voice services. That calls for a conservative approach.

## Current status

This independent fork is in early development; no Unified TV releases or verified installations have been published. Inherited upstream scripts and launchers must be evaluated in the context of the exact television and firmware before use.

## Report vulnerabilities privately

**Do not post exploits, family images, IP addresses or credentials in public issues.** Use the **Security** tab on [this Unified TV repository](https://github.com/tcrosswell-99/Unified-TV/security) and GitHub's private vulnerability reporting if available. If it is not enabled, request a private contact route from the repository owner rather than disclosing the details publicly.

Do not send Unified TV-specific vulnerabilities to the original CleanYourTV or CleanYourTV UI authors. For bugs in their original unmodified code, also follow their own security policies.

## Scope and safeguards

- **Television changes:** package disabling, launcher replacement, remote mappings, input and audio control, recovery.
- **Network and accounts:** Home Assistant tokens, Ring snapshots/events, NAS storage URLs, music accounts and API credentials.
- **VPN:** avoid claiming traffic is protected from a mere connected indicator; validate route lockout under tunnel failure.
- **Voice:** require clear microphone state and tightly scoped, explicitly permitted home/device commands.
- **Permissions:** minimal Android permissions, no hard-coded service credentials, no debug interfaces left on after setup.

## Developer/reviewer rules

No destructive or mass system package modifications without model/firmware-specific evidence, advance consent and a restore path independent of the launcher. Never upload logs with real household data. Avoid dependencies/scripts from unreviewed repositories and review GitHub Actions permissions before enabling new workflows.

Information in the public specification is an architectural example. Do not commit addresses, serial numbers, Ring event payloads, NAS filenames or personal photographs from an actual household.
