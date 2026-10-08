# Version 2.0.1 validation

Tested in native Minecraft Java 1.21.1 with Fabric Loader 0.16.5, Fabric API 0.105.0, Iris 1.8.7 and Sodium 0.6.9 on an Apple M3 Mac. These checks establish observed behavior, not long-term retention or equivalence to larger mods.

- All three route types: ordinary native corridor movement, all three generated circuits solved by native client interaction packets, twelve credits awarded, repeat interaction unable to award twice, both optional specimens gathered, native field map, completion and return home.
- Garden: ordinary placement, mining and inventory pickup of the correct self drop; workshop bundle count and price; expansion preserves existing construction and creates new land.
- Persistence: after closing Minecraft and reopening the same world, complete progress, inventory and probed construction match the checkpoint.
- Dimension return: the original dimension and game mode are restored; reentry does not duplicate the starter kit.
- Generator: `tools/verify-generators.java` checks 4,096 seeds and 57,344 circuits for connectivity, reciprocal doors, cycles, side branches, exact objective counts, valid solutions, unsolved starts, determinism and defensive copies. It found 4,096 distinct configurations and 184 room graphs. This verifies generation properties, not human enjoyment.
- Controls: G returns home through the registered native key binding and avoids Iris's R shader-reload binding.
- Shader: the adapted shader compiles in the retreat with its Overworld programs.
- Recording correction: the first version 2 trailer was withdrawn after visible stutter was reported. Averaged in-game FPS and uniform export timestamps did not establish smooth source motion. The opt-in driver now updates native first-person look every render instead of every game tick. Capture validation checks all source frame gaps and every native render interval across each complete take; it rejects stalls before editing. The exported trailer uses normal-speed native footage, app audio and licensed music, with no titles, HUD, menus, or text overlays.
- Replacement trailer: all 9 complete source takes were checked, including capture start and final hold, totaling 4,670 actual framebuffer images. The longest uncut source is 34.899 seconds. The worst native render interval was 42.486 ms; the worst capture interval was 45.628 ms. No interval exceeded 50 ms; no writer frames were dropped or failed. AAC packet coverage spans each entire source interval. The final 30.867-second, 1280×720 trailer decoded all 926 video frames and its complete audio without errors; consecutive decoded video frames are distinct. See the [full-take measurements](recording-validation.json). These results describe this recording on this Mac.
- Clean setup: official renderer downloads match locked SHA-512 hashes; extracted Iris libraries match the verified archive; repeat setup preserves existing shader preferences.

`tools/verify-native.py` reproduces the local engine checks with `runClient -Pdemo -Pfresh`. The opt-in driver only exists for QA and recording; ordinary installs do not poll control files. QA teleports prepare test positions, while corridor checks and recorded gameplay use native input and collision. The trailer contains edited captures of the native Minecraft framebuffer with synchronized Minecraft app audio.

Multiplayer, arbitrary mod combinations, and other GPU/OS combinations have not been validated. The expedition and garden are shared per world. The mod changes vanilla glass textures globally, which is documented in installation instructions.
