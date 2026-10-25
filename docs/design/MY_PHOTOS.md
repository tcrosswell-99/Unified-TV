# My Photos on the Wallpaper: access design

The owner decided on 25 October 2026 that Synology photos follow as a separate task, **with the access method designed first**. This is that design. The owner approved it the same day, and **Phase 1 (DLNA picture folders) is built on Android** (`PhotoSource`, `PhotoCache`, `PhotoFoldersActivity`, `WallpaperPolicy`); it is unit-tested only. The app has Adult and Child profiles and no Guest profile yet: anything but Adult gets Artwork. Phase 2 (Synology Photos albums) is not built. Status: register rows WP-03, WP-10 and WP-11.

## What it has to do (spec 33.2 to 33.4, WP-03, WP-10, WP-11)

- Show only pictures the owner chose: a named album or folder, never "all photos".
- Never show private pictures in Child or Guest profiles unless the owner says so, per TV and per profile.
- Need no typing in the normal journey; addresses and accounts only under Advanced (spec 27.4).
- Fall back to the curated artwork when the NAS is offline, never to a blank screen; keep a cache only if the owner allows it.
- Keep photos on the home network: no cloud, no face recognition, no upload.

## Three ways in

| Route | How Unified TV would read photos | Setup for the household | Albums? | Profile safety | Verdict |
| --- | --- | --- | --- | --- | --- |
| **A. DLNA picture folders** (Synology Media Server) | The DLNA client already used for films (`DlnaClient`, `DlnaScope`), asking for image items instead of video | None beyond today: the NAS is already found automatically; the owner picks a picture folder the same way as film folders | Folders only, no albums | DLNA has no accounts, so anything on the media server is visible to anyone on the network. Child/Guest must simply not see the setting or the pictures | **Phase 1** |
| **B. Synology Photos API** | Synology's own web API: sign in, list a **shared album**, fetch thumbnails at TV size | The owner creates a DSM user that can read one shared album only, and signs in once on the TV (Advanced) or by a sign-in link shown on the TV | Yes, real albums, the owner's own curation | Access limited by that DSM user on the NAS itself: the strongest separation | **Phase 2** |
| C. SMB or WebDAV file shares | Reading files directly | Share paths, users and passwords | No | Weak | Not used |

Route C fails the "no typing, no plumbing" rule and adds a file-protocol stack for little gain.

## Recommendation

1. **Phase 1, folders through DLNA.**
   - **Picking:** Settings → Appearance → Wallpaper → **Source: My Photos** lists the NAS's picture folders (found as today, no typing). The owner picks one or more; they are saved encrypted, as the film folders are, and `DlnaScope` keeps browsing inside them.
   - **Profiles:** the source is per TV and per profile, and Child and Guest always get Artwork.
   - **Display:** pictures are fetched at no more than 1920 × 1080 (DLNA resources often include smaller renditions) and, if the owner switches **Keep a copy for when the NAS is off** on, cached in the app's private storage (bounded, about 200 MB).
   - **NAS offline:** the cache is used, then Artwork.
   - **Limit:** stated on screen: "These pictures are visible to any device on your home network that can see your media server."
2. **Phase 2, albums through Synology Photos,** once the owner wants albums:
   - **Access:** a read-only DSM user that can see one shared album, created by the owner on the NAS.
   - **Sign-in:** once, from Advanced, or a sign-in link shown on the TV and opened on a phone, if Synology's API allows it (to be checked against the DSM version on the owner's NAS).
   - **Storage:** credentials go in the encrypted Vault and never in logs. Nothing goes outside the home network.

## What needs checking on the owner's NAS before building

- Whether Synology Media Server lists the photo library's folders over DLNA, and offers smaller image renditions.
- The DSM and Synology Photos versions, for Phase 2's API and sign-in options.
- Which folders or albums the owner wants on the TV; these are the owner's choice and stay off-repository.

## Acceptance (already in the register)

- **WP-03:** only the chosen folders or albums appear.
- **WP-10:** with the NAS unplugged, the cache or Artwork shows, with no blank screen.
- **WP-11:** Child and Guest never see private pictures.
- Each needs a real NAS and TV run, recorded with evidence.
