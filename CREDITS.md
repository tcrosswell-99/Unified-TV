# Credits, licences and origin

Unified TV is an **independent public GitHub fork**, currently in early development. Credit for inherited work belongs to its authors. Keeping the original Git history and the licence notices is a condition of our development and release process.

## Code and ancestry

| Source | Contribution to the inherited repository | Licence/notice |
| --- | --- | --- |
| [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin | Original Android launcher, device-specific package lists, restore scripts, diagnostics and tooling | Original [MIT licence](LICENSE), copyright 2026 Gathin |
| [CleanYourTV UI](https://github.com/matheus-caldeira/cleanyourtv-ui) by Matheus Caldeira | Direct parent of this fork, TV UI, content browsing, configuration, original modifications | [launcher-ui/NOTICE](launcher-ui/NOTICE), copyright 2026 Matheus Caldeira |
| [Free My TV](https://github.com/simbasjungles/free-my-tv) contributors | Incorporated home screen, settings, screensaver and recommendations code in the parent repository | MIT; see original `launcher-ui/LICENSE.free-my-tv` and `launcher-ui/NOTICE` |

Keep **all existing licence texts, file headers and required notices** when moving, adapting or distributing inherited code. New Unified TV original contributions are tracked through subsequent commits. Do not replace earlier copyright notices with a single new owner.

## Research references, not integrated dependencies

The following informed research and product design but **have not been copied into the new fork in this foundation change**:

- **[CleanFrame by Nathan Clark (`nthnclrk`)](https://github.com/nthnclrk/CleanFrame):** original Samsung The Frame/Tizen project that informed our calm, single-dock layout, remote focus interactions and approach to pre-rendered glass. Credit for those design ideas belongs to Nathan Clark. **This is inspiration only:** no CleanFrame source code, graphics, photos, font files or animations have been copied into Unified TV. CleanFrame's source is MIT licensed, copyright (c) 2026 Nathan Clark; if we incorporate code in future, its original copyright and licence notices must accompany that code. CleanFrame is not affiliated with or endorsing Unified TV.
- [TV Detox](https://github.com/Ansub/tv-detox), optional performance/cleanup techniques (MIT). If code is incorporated later, preserve its copyright 2026 Ansub Khan and MIT notice.
- [Vision Stream concept](https://dribbble.com/shots/21647921-Vision-Stream-Next-Gen-Smart-TV-App-UI) and [TV Interface motion](https://dribbble.com/shots/5773537-TV-Interface-motion), **visual references only**.
- Apple tvOS Liquid Glass era design principles, **reference only**. Do not copy Apple's proprietary interface, fonts, animations or icons into release assets.

For inherited bundled fonts, Material Symbols, brand logos, TMDB, Wikidata and Open-Meteo dependencies, see existing `LICENSE`, `launcher-ui/NOTICE` and bundled third-party notices. Audit assets again before public binary releases.

## Trademark and independence

Apple, Android, Google TV, Sony, TCL, Samsung, Ring, Alexa, Xbox, Netflix, Disney+, BBC, VLC, Jellyfin, Synology, NordVPN, OpenAI and other marks belong to their respective owners. Mention of these services explains optional integrations and does not imply partnership, sponsorship, certification or endorsement.

The project is **not affiliated with the upstream maintainers**, and Unified TV issues or security reports must not be sent to the original developers unless they concern the original projects themselves.

## Contributions

New contributors retain credit in Git history. To contribute third-party code or assets, provide source, exact licence, authorship and redistribution evidence in the pull request. Only original or appropriately licensed screenshots/imagery belong in a public release.

Preview embedded playback uses AndroidX Media3 (Apache-2.0) and its transitive libraries. Gradle retains packaged third-party licence notices. Original Unified TV preview components do not import proprietary mockup assets or fonts. See `preview/THIRD_PARTY.md`.


Preview 0.4 includes five AI-generated gallery JPGs supplied by the owner, who expressly authorised public inclusion as example artwork on 24 October 2026. They are preserved unedited at `preview/app/src/main/assets/gallery/example-1.jpg` through `example-5.jpg`; they are not third-party film stills or private household photographs. Source filenames identify Gemini-generated images, as supplied by the owner. Original gallery/discovery/settings code follows the project licence; no new runtime dependency was added.
