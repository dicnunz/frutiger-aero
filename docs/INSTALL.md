# Install Frutiger Aero 2.0.1

Use Minecraft **Java Edition 1.21.1**. This is a Fabric mod; Bedrock Edition and other Minecraft versions are unsupported. The ordinary launcher requires a Minecraft account that owns Java Edition.

## Modrinth App or Prism Launcher

Download `Frutiger-Aero-2.0.1.mrpack` from the [GitHub release](https://github.com/dicnunz/frutiger-aero/releases/latest), then import it as a new instance. The manifest downloads the pinned Fabric API, Iris and Sodium from the official Modrinth CDN with SHA-1 and SHA-512 verification. Our mod and complete adapted shader source are included as overrides. Sign in with an account that owns Minecraft Java, launch, open a world and enter `/aero`.

## Minecraft launcher

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1, using Loader **0.16.5** or a compatible newer version. [Fabric's installation guide](https://docs.fabricmc.net/players/installing-fabric/) covers the official launcher; choose 1.21.1 in the installer.
2. Put `frutiger-aero-2.0.1.jar` and [Fabric API **0.105.0+1.21.1**](https://modrinth.com/mod/fabric-api/version/WTaAx4ah) in that instance's `mods` folder. Use the regular jar, not the `-sources.jar`. Source builds are described below.
3. Launch the Fabric profile, create or open a world, and enter `/aero` in chat. The mod prepares the retreat in its own `aero:retreat` dimension; it does not build the retreat in your overworld. Wait for the preparation indicator to finish.
4. Right-click the compass console to choose Poolways, Canopy, or Gallery. Restore three flows, then use the exit compass. Optional specimens give cuttings for the garden.

M opens the journey map or the guide at home. G returns home and leaves the unfinished journey available to continue. Right-clicking the Field Guide opens instructions; the Return Stone takes you home. `/aero leave` restores the location and game mode from which you entered.

The workshop trades 2 earned credits for 32 porcelain, aqua tiles, sapphire tiles, glass, or foliage, or for 8 lights. Expanding the garden costs 12 credits and adds 10 blocks to each side length. These are real inventory items. Building and mining are allowed inside the garden plot; the hub and expedition architecture are protected.

Use a new world for initial v2 testing. The old v1 fixed tour, video, and release archives do not demonstrate the v2 loop. The v2 garden and expedition are stored with the world, so keep the mod installed when continuing that save. Multiplayer is not validated; the current garden and expedition rooms are shared per world.

## Optional shader

Add [Iris **1.8.7 for Fabric 1.21.1**](https://modrinth.com/mod/iris/version/1.8.7+1.21.1-fabric) and [Sodium **0.6.9 for Fabric 1.21.1**](https://modrinth.com/mod/sodium/version/mc1.21.1-0.6.9-fabric) to the same `mods` folder. Copy `Aero-MakeUp-shader.zip` from `dist` or the release download into `shaderpacks`. In Minecraft, open **Options → Video Settings → Shader Packs**, select it, and enable shaders.

These renderer versions are deliberately pinned, rather than described as the latest versions. Official download URLs and complete SHA-512 hashes are in [modrinth-lock.json](../dependencies/modrinth-lock.json). They were checked against the official Modrinth version API on 2026-10-08.

```text
iris-fabric-1.8.7+mc1.21.1.jar
b4b806f151f5bd46e409e0d27e611a0444a98bbcef72e52163e6f989e90a3097d9dea8ee45fbb7e4f41d517eeaab4a62d18f7502032c3f8d66ff9e4ee5dd18f9

sodium-fabric-0.6.9+mc1.21.1.jar
892728b3c26038446b9f573695db9e791fa320994ed34f89be5c766fa2e735b46adb9efa8f6688d5a5b7bb8e9793ff09e72f06fb5ff074137d5b833f1fe27227
```

Shaders are optional for gameplay. The mod also replaces the standard glass, cyan glass, and light-blue glass textures with its clearer variants, including outside the retreat. Shader appearance and speed depend on the graphics driver and settings.

## Build and run from source

Install a **JDK 21**, with `java` and `javac` available in the terminal, plus **Python 3.9 or newer**. [Fabric's Java 21 guidance](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.1/players/installing-java/windows.md) explains the version requirement. Gradle 8.10.2 is provided through the checked-in wrapper; no separate Gradle installation is needed.

From the checkout root on macOS or Linux:

```sh
java -version
python3 tools/setup-runtime.py
sh tools/gradle.sh build
sh tools/gradle.sh runClient
```

On Windows:

```powershell
java -version
py -3 tools\setup-runtime.py
.\gradlew.bat build
.\gradlew.bat runClient
```

The setup script uses only Python's standard library. It downloads Iris and Sodium according to the lock, verifies the hashes before replacing files, and extracts `antlr4-runtime`, `glsl-transformer`, and `jcpp` from the verified Iris jar into `dependencies/iris-libs`. Fabric API is supplied separately by Loom. No Minecraft files are bundled or fetched by this script.

When `dist/Aero-MakeUp-shader.zip` is present, setup copies it into `run/shaderpacks` and enables it only if no Iris configuration exists. Existing shader settings are preserved. A source-only checkout without that optional zip still has a usable renderer; install the release shader separately to obtain the adapted appearance. Shader source and notices are inside the zip.

The first Gradle run needs internet access to resolve Minecraft, mappings, Fabric, and game assets. `build/libs/frutiger-aero-2.0.1.jar` is the installable result. `runClient` uses an isolated `run` directory, creates the development world on first launch, and reopens it on later launches. On macOS, `Play.command` invokes this same development launch after setup. Ordinary installed mod jars do not auto-create a world.

For a separate development save, use `sh tools/gradle.sh runClient -Pfresh`. This creates another world without deleting the earlier garden. The checked-in block textures and room sounds are sufficient to build; rerunning asset generators is not required.
