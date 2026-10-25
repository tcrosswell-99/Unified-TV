# Preview dependencies

Original preview source follows the repository MIT licence. Existing upstream notices are preserved untouched.

AndroidX Media3 ExoPlayer, HLS and UI 1.5.1: Android Open Source Project, Apache License 2.0, https://github.com/androidx/media . AndroidX and transitive notices remain packaged by Gradle; dependency report: `launcher/gradlew -p preview :app:dependencies`.

No third-party film stills, reference screenshots, proprietary fonts or brand assets are bundled. App icons come from installed packages at runtime. JUnit and org.json are test-only dependencies. This is an experimental debug-signed preview, not a production release.

OkHttp 4.12.0: Square, Apache-2.0, https://github.com/square/okhttp . It carries the authenticated HA WebSocket only, with redirects disabled and platform TLS. No HTTP logging interceptor is included. Robolectric is a test-only dependency (Apache-2.0).

The Settings focus states and dock focus persistence fixes are original project code. The isolated deviceCheck variant introduces no new dependency or bundled asset.

The Preview 0.3 ambient mesh, cached blur, material, dock migration and VPN handoff code are original under the project licence. The bundled coastal landscape was generated with OpenAI image generation on 24 October 2026 for this project; it is an imagined scene, not a third-party stock photograph or a film still. No reference artwork was copied. No new runtime dependency was added.


Preview 0.4 includes five AI-generated gallery JPGs supplied by the owner, who expressly authorised public inclusion as example artwork on 24 October 2026. They are preserved unedited at `preview/app/src/main/assets/gallery/example-1.jpg` through `example-5.jpg`; they are not third-party film stills or private household photographs. Source filenames identify Gemini-generated images, as supplied by the owner. Original gallery/discovery/settings code follows the project licence; no new runtime dependency was added.


Team fixtures (25 October 2026) can come from **API-Football** (api-sports.io) using the household's own API key, added from a phone through phone setup and stored encrypted on the TV; no key is in the source and nothing else is sent to that service. API-Football data is used under the household's own account and its terms. Team badges are drawn by the app from a team's short name and colours; no club crest or logo is bundled or downloaded. The dock's service symbols are original simple vector drawings, not copies of brand artwork.

Film and programme posters (25 October 2026, optional) come from **The Movie Database (TMDB)** using the household's own free API key or read-access token, added from a phone and stored encrypted on the TV; no key is in the source. Only a cleaned title and, when known, a year are sent, to `api.themoviedb.org`, and pictures are fetched from `image.tmdb.org`; never a file name, folder, server name or address. Matches and pictures are cached on the TV. TMDB data and images are used under the household's own account and TMDB's terms of use. **This product uses the TMDB API but is not endorsed or certified by TMDB.** No TMDB logo or artwork is bundled.

## QR Code generator library (Java), Project Nayuki, MIT License

Phone setup (25 October 2026) draws its QR codes with Project Nayuki's QR Code generator library, vendored unchanged from https://github.com/nayuki/QR-Code-generator (commit 3c6d0b3cefb4e049dc337e82237c9644399716a8, `java/src/main/java/io/nayuki/qrcodegen/`: `QrCode.java`, `QrSegment.java`, `BitBuffer.java`, `DataTooLongException.java`) at `preview/app/src/io/nayuki/qrcodegen/`. Each file keeps its copyright and licence header. It has no dependencies, uses no network, files or reflection, and adds no Gradle dependency. Licence text:

```
Copyright © 2026 Project Nayuki. (MIT License)
https://www.nayuki.io/page/qr-code-generator-library

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

* The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

* The Software is provided "as is", without warranty of any kind, express or
  implied, including but not limited to the warranties of merchantability,
  fitness for a particular purpose and noninfringement. In no event shall the
  authors or copyright holders be liable for any claim, damages or other
  liability, whether in an action of contract, tort or otherwise, arising from,
  out of or in connection with the Software or the use or other dealings in the
  Software.
```

Phone setup's own page (served by the TV on the home network) is original project code: inline CSS only, no script, fonts, images or third-party resources.
