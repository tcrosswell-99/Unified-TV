# NAS, playback and profiles — v3.4

**G3** follows G2 Visual, although PR #9 reportedly implements editable dock, Apps grid and NAS paging already. Preserve code and audit actual PR merge/commit before assigning status. Source implementation is not physical acceptance.

Synology allowed logical roots: `/video/Kids`, `/video/Movie`, `/video/TV Show`; no unrelated directories by default. DLNA may expose browseable **folders**, not Synology Photos albums, a secure child boundary or full metadata catalogue. Use actual source capability negotiation and explicit user-visible limitations. Authenticated catalogue adapter only where configured and approved; do not fabricate ratings, posters, watched status, search or resume.

Films and TV belong in Home content browsing/rows, not automatically replacing six-app dock slots. Embedded playback must prove codec/audio/subtitles/seek/error/reconnect on actual TCL; Samsung and Sony need independent proof. Enforce child access at server/auth source, not only by hiding UI folders. Preserve existing media work; G2 priority means do not expand G3 while G2 is incomplete unless independent agent owns it without conflicts.

**Synology Photos is undecided:** choose a distinct later integration path only after owner consent and technical feasibility: Synology Photos supported API/auth (albums, permissions, token handling) versus DLNA/file-folder browsing (folders only). Do not label DLNA folder navigation as album integration. No default access to private household photos; local opt-in, privacy-safe thumbnails and cache deletion.

## v3.4 product UX decisions

Minimal Home remains separate from the **Library** destination, which contains Movies, TV Shows, Continue Watching, Recently Added and Favourites when supported. Two profile types only: Adults and Kids; each has separate Unified-TV-managed watch history and favourites. Cross-service history is not implied. Universal search is deferred. Automatic NAS discovery is required without normal-user IP entry; hardware limitations must be documented rather than silently substituting manual entry. Source folder paths in this document are the original owner's setup, not global defaults. Read Settings module 12 for configuration, discovery and family access controls.
