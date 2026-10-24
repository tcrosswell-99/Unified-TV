# Preview dependencies

Original preview source follows the repository MIT licence. Existing upstream notices are preserved untouched.

AndroidX Media3 ExoPlayer, HLS and UI 1.5.1: Android Open Source Project, Apache License 2.0, https://github.com/androidx/media . AndroidX and transitive notices remain packaged by Gradle; dependency report: `launcher/gradlew -p preview :app:dependencies`.

No third-party film stills, reference screenshots, proprietary fonts or brand assets are bundled. App icons come from installed packages at runtime. JUnit and org.json are test-only dependencies. This is an experimental debug-signed preview, not a production release.

OkHttp 4.12.0: Square, Apache-2.0, https://github.com/square/okhttp . It carries the authenticated HA WebSocket only, with redirects disabled and platform TLS. No HTTP logging interceptor is included. Robolectric is a test-only dependency (Apache-2.0).

The Settings focus states and dock focus persistence fixes are original project code. The isolated deviceCheck variant introduces no new dependency or bundled asset.

The Preview 0.3 ambient mesh, cached blur, material, dock migration and VPN handoff code are original under the project licence. The bundled coastal landscape was generated with OpenAI image generation on 24 October 2026 for this project; it is an imagined scene, not a third-party stock photograph or a film still. No reference artwork was copied. No new runtime dependency was added.


Preview 0.4 includes five AI-generated gallery JPGs supplied by the owner, who expressly authorised public inclusion as example artwork on 24 October 2026. They are preserved unedited at `preview/app/src/main/assets/gallery/example-1.jpg` through `example-5.jpg`; they are not third-party film stills or private household photographs. Source filenames identify Gemini-generated images, as supplied by the owner. Original gallery/discovery/settings code follows the project licence; no new runtime dependency was added.
