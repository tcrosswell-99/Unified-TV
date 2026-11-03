# Preview dependencies

Original preview source follows the repository MIT licence. Existing upstream notices are preserved untouched.

AndroidX Media3 ExoPlayer, HLS and UI 1.5.1: Android Open Source Project, Apache License 2.0, https://github.com/androidx/media . AndroidX and transitive notices remain packaged by Gradle; dependency report: `launcher/gradlew -p preview :app:dependencies`.

No third-party film stills, reference screenshots, proprietary fonts or brand assets are bundled. App icons come from installed packages at runtime. JUnit and org.json are test-only dependencies. This is an experimental debug-signed preview, not a production release.

OkHttp 4.12.0: Square, Apache-2.0, https://github.com/square/okhttp . It carries the authenticated HA WebSocket only, with redirects disabled and platform TLS. No HTTP logging interceptor is included. Robolectric is a test-only dependency (Apache-2.0).

The Settings focus states and dock focus persistence fixes are original project code. The isolated deviceCheck variant introduces no new dependency or bundled asset.

The Preview 0.3 ambient mesh, cached blur, material, dock migration and VPN handoff code are original under the project licence. The bundled coastal landscape was generated with OpenAI image generation on 24 October 2026 for this project; it is an imagined scene, not a third-party stock photograph or a film still. No reference artwork was copied. No new runtime dependency was added.


Preview 0.4 includes five AI-generated gallery JPGs supplied by the owner, who expressly authorised public inclusion as example artwork on 24 October 2026. They are preserved unedited at `preview/app/src/main/assets/gallery/example-1.jpg` through `example-5.jpg`; they are not third-party film stills or private household photographs. Source filenames identify Gemini-generated images, as supplied by the owner. Original gallery/discovery/settings code follows the project licence; no new runtime dependency was added.


Team fixtures and the UK team list (owner decision OD-22, 25 October 2026) come by default from **TheSportsDB** (https://www.thesportsdb.com), a community-edited sports database, through its free public JSON API (version 1) with the free public test key that TheSportsDB documents for small and non-commercial projects. That key is public and the same for everyone; it is not a household secret. Only UK league names and numbers, team numbers, round and season numbers and club names are sent, to `www.thesportsdb.com` over HTTPS (a club's name: the household's chosen club, to find its number, and a name typed in the picker only when an adult presses "Look up on TheSportsDB"); team names, short codes, colours and fixture dates and times are kept on the TV. Scores, results, crests, badges and pictures in its replies are never read or shown. Settings > Updates & About > Open-source licences and the team picker credit TheSportsDB. Because the free key lists only about 10 teams a league, the Premier League, Championship and League One are completed from Unified TV's own list in `preview/app/res/raw/football_teams.json` (club names, other names and kit colours, written for this project from public league membership, marked with its season; facts only, no crests or artwork; MIT like the rest of this repository). Its terms, the exact free key and the free tier's limits were **not** re-checked on thesportsdb.com itself when this was written (the site could not be reached; the limits used come from client libraries' published measurements); confirm them before any wider release, and move to a supporter key if TheSportsDB's terms require one.

Team fixtures can instead come from **API-Football** (api-sports.io), optionally, using the household's own API key, added from a phone through phone setup and stored encrypted on the TV; no key is in the source and nothing but the team's number is sent to that service. API-Football data is used under the household's own account and its terms. Team badges are drawn by the app from a team's short name and colours (TheSportsDB's colours when it gives them); no club crest or logo is bundled or downloaded.

### Trademarks on the dock

The dock's marks for YouTube (also shown on the SmartTube tile the household names YouTube), Netflix, Disney+ and BBC iPlayer are simple white monochrome shapes drawn in code by the app (`DockGlyphs.java`): YouTube's rounded play button, Netflix's N ribbon and BBC's three lettered blocks. Disney+ uses Disney's own 2024 wordmark, from an SVG the owner supplied on 25 October 2026 ("Disney_2024_Print.svg", source not recorded), kept as path data in `BrandMarks.java` and drawn in plain white. No image file is bundled, and no brand colour is used. These names and marks belong to their owners; they are shown only to identify the app the tile opens, and Unified TV is not affiliated with or endorsed by them. The other dock symbols (Live TV's television, Music's notes, the library shelf) are original drawings.

Unified TV's own app icon and TV banner (`res/drawable/ic_launcher_*.xml`, `res/drawable/app_banner.xml`) are original vector drawings: a white monoline "Unified TV" wordmark on the app's dark blue gradient. No font or third-party artwork is used.

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

The Optional clean-up's ADB client (`preview/app/src/io/unifiedtv/adb/`: `AdbLink`, `AdbKey`, `AdbCommands`) is original project code, written from the ADB protocol as Android's open-source `system/core/adb` documents it (message header, CNXN/AUTH/OPEN/WRTE/OKAY/CLSE, the RSA token signature and adbd's public-key format). No ADB library or other third-party code was copied, and no new runtime dependency was added. Its tested package lists come from this repository's own `examples/*/debloat.txt` (MIT, CleanYourTV contributors), generated by `preview/tools/cleanup_lists.py`.

## WireGuard tunnel library for Android, WireGuard LLC, Apache License 2.0

Unified TV's own NordVPN connection (OD-29, 3 November 2026) uses the official WireGuard tunnel library for Android, `com.wireguard.android:tunnel` **1.0.20260102** from Maven Central (https://www.wireguard.com/, source https://git.zx2c4.com/wireguard-android), declared in `preview/app/build.gradle`. Its own notice: "Copyright © 2017-2025 WireGuard LLC. All Rights Reserved. SPDX-License-Identifier: Apache-2.0". It brings `GoBackend` and its `VpnService` (`com.wireguard.android.backend.GoBackend$VpnService`, guarded by `BIND_VPN_SERVICE`), and its own native libraries (`libwg-go.so`, `libwg.so`, `libwg-quick.so` for arm64-v8a, armeabi-v7a, x86 and x86_64), which the WireGuard project builds from wireguard-go (MIT) and the Go runtime (BSD-3-Clause); those two notices were not re-read from the AAR itself when this was written. Its Maven dependencies, `androidx.annotation:annotation` 1.9.1 and `androidx.collection:collection` 1.5.0, are Android Open Source Project libraries under Apache-2.0. Unified TV's code uses only `GoBackend`, `Tunnel` and `Config.parse` (`WireGuardTunnel.java`); nothing in the library was changed. The NordLynx server details it is given come from NordVPN's unofficial API (docs/VPN_PROTECTION.md); NordVPN and NordLynx are NordVPN's trademarks and Unified TV is not affiliated with or endorsed by NordVPN. "WireGuard" is a registered trademark of Jason A. Donenfeld.

Licence text (Apache License 2.0, also covering the AndroidX, Media3, OkHttp and Robolectric entries above):

```

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
