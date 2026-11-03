# Signing, releases and update continuity — v3.4

**G0 release prerequisite; owner approval required before generating or exposing private keys.** Inspect installed package IDs, signing certificate SHA-256 fingerprints, APK/WGT version and manifests, repo CI signing, and whether any old signing material is recoverable. Do not assume a lost GitHub ephemeral key can sign an update. Android supports signing key rotation from Android 9 subject to valid signing lineage; **without the old key, rotation for the old preview install is not a viable upgrade path**. Do not uninstall the existing preview to hide this.

Use separate launcher package ID and durable release key from first install; preserve preview as-is. Write a **key custody plan** covering owner, generation device, protected encrypted backup in two independent locations, recovery verification, password separation, access log, no repo/CI plaintext secrets, compromise/revocation response, renewal and continuity. Do not include keys or passwords in developer ZIP. Record public certificate fingerprints only.

For Tizen WGT, document author/distributor certificates and Samsung device entitlement/expiry constraints. Android TCL and Sony may share a signing identity only if package/update design and owner consent support it; verify per-target. Prove version N→N+1 in-place updates with settings retained, package identity unchanged, signing accepted and rollback instructions on each physical TV. Separate package IDs do not automatically share preferences; new launcher setup/migration must be explicit and consented.

Owner runs local signing inspection/key tool and install commands; cloud agent reviews redacted output. Do not mark upgrade verified from CI alone.
