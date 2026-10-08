# Version 2.0 validation

Tested in native Minecraft Java 1.21.1 with Fabric Loader 0.16.5, Fabric API 0.105.0, Iris 1.8.7 and Sodium 0.6.9 on an Apple M3 Mac. These checks establish observed behavior, not long-term retention or equivalence to larger mods.

- All three route types: ordinary native corridor movement, all three generated circuits solved by native client interaction packets, twelve credits awarded, repeat interaction unable to award twice, both optional specimens gathered, native field map, completion and return home.
- Garden: ordinary placement, mining and inventory pickup of the correct self drop; workshop bundle count and price; expansion preserves existing construction and creates new land.
- Persistence: after closing Minecraft and reopening the same world, complete progress, inventory and probed construction match the checkpoint.
- Dimension return: the original dimension and game mode are restored; reentry does not duplicate the starter kit.
- Generator: `tools/verify-generators.java` checks 4,096 seeds and 57,344 circuits for connectivity, reciprocal doors, cycles, side branches, exact objective counts, valid solutions, unsolved starts, determinism and defensive copies. It found 4,096 distinct configurations and 184 room graphs. This verifies generation properties, not human enjoyment.
- Controls: G returns home through the registered native key binding and avoids Iris's R shader-reload binding.
- Shader and trailer: the adapted shader compiles in the retreat with its Overworld programs. Selected native capture samples range from 54 to 58 FPS; the 44.63-second export has 1,339 uniformly timed frames at 30 FPS, passes full audio/video decoding, and mixes app audio with licensed music. There are no titles, HUD, menus, or text overlays.
- Clean setup: official renderer downloads match locked SHA-512 hashes; extracted Iris libraries match the verified archive; repeat setup preserves existing shader preferences.

`tools/verify-native.py` reproduces the local engine checks with `runClient -Pdemo -Pfresh`. The opt-in driver only exists for QA and recording; ordinary installs do not poll control files. QA teleports prepare test positions, while corridor checks and recorded gameplay use native input and collision. The trailer contains edited native window captures with app audio, rather than generated gameplay.

Multiplayer, arbitrary mod combinations, and other GPU/OS combinations have not been validated. The expedition and garden are shared per world. The mod changes vanilla glass textures globally, which is documented in installation instructions.
