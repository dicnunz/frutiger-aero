# Credits and licenses

Original mod code, world builders, procedurally generated textures, and synthesized room ambience were assembled by GPT-6.1 Sol Max for Nicholas Dunzelman and are covered by [MIT](../LICENSE). Minecraft is Mojang and Microsoft's work and is not redistributed here.

## Adapted shader

**Aero-MakeUp** adapts **MakeUp Ultra Fast 9.5g by Javier Garduño**, under LGPL-3.0. Upstream: [source](https://github.com/javiergcim/MakeUpUltraFast), [official shader page](https://modrinth.com/shader/makeup-ultra-fast-shaders). The imported upstream revision is `ad2d9327ad485429e4eaf2b3f98cd738ada609de`.

Changes are cool daylight, neutral ambient fill, turquoise water, softer shadows, ceramic material tags, lower GPU defaults, and explicit Iris support for the retreat dimension. The redistributed shader zip contains readable GLSL source, the upstream `LICENSE`, upstream `Credits`, and `AERO-CHANGES.md`. Keep those notices with shader copies. MakeUp's upstream [license](licenses/LGPL-3.0.txt) and [credits](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/Credits) remain distinct from this mod's MIT license.

LGPL-3.0 incorporates the GPL-3.0 terms. A [GPL-3.0 text copy](licenses/GPL-3.0.txt), obtained from the [SPDX license list](https://github.com/spdx/license-list-data/blob/main/text/GPL-3.0-only.txt), is included for packaging alongside the upstream LGPL notice.

## Runtime dependencies

| Component | Source | License |
| --- | --- | --- |
| Fabric Loader | [FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader) | Apache-2.0 |
| Fabric API | [FabricMC/fabric](https://github.com/FabricMC/fabric) | Apache-2.0 |
| Iris 1.8.7 | [IrisShaders/Iris](https://github.com/IrisShaders/Iris) | LGPL-3.0-only |
| Sodium 0.6.9 | [CaffeineMC/sodium](https://github.com/CaffeineMC/sodium) | PolyForm Shield 1.0.0 |

The renderer jar versions, official download URLs, and SHA-512 hashes are recorded in [modrinth-lock.json](../dependencies/modrinth-lock.json). Setup downloads those jars locally. Their licenses and the licenses of their bundled libraries remain their own; the MIT notice does not relicense them.

## Trailer music

**Dream Culture** by **Kevin MacLeod (incompetech.com)** is licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). [Original track, ISRC USUAN1300046](https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1300046). It is separate from the mod's synthesized in-game ambience.

When a trailer uses the track, publish the credit and license link with that trailer and identify editing or mixing. A ready-to-use attribution is:

> Music: [Dream Culture](https://incompetech.com/music/royalty-free/index.html?Search=Search&isrc=USUAN1300046) by Kevin MacLeod (incompetech.com), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Excerpted and mixed with Minecraft gameplay audio.

The [music credit file](../media/music/CREDITS.md) accompanies the source. The version 2 trailer excerpts this track and mixes it with native Minecraft audio.

## References

- [CARI / Sofi Xian, Frutiger Aero](https://cari.institute/aesthetics/frutiger-aero): glass, glossy surfaces, blue and green materials, and nature imagery. No reference images are copied.
- [Tensori, POOLS](https://store.steampowered.com/app/2663530/POOLS/): quiet spatial pacing and changing room acoustics. No game assets are copied.
- [Create's developers](https://github.com/Creators-of-Create/Create), [Vault Hunters](https://vaulthunters.gg/patch-notes/21.0.0), and [Boris the Brave](https://www.boristhebrave.com/2026/07/05/oatmeal-spice/): physical systems, optional discoveries, authored building, and variation that changes decisions. The [research notes](research/experience.md) distinguish source evidence from design inferences. No code or assets from these mods are copied.

Third-party sources and licenses were checked on 2026-10-08. References inform the design; they do not imply endorsement or prove long-term player retention.
