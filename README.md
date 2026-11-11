# REDUXion

A client-side Fabric mod for Minecraft that overrides item models, armor textures and armor trims (REDUX packs) on top of any resource pack. Author: **Kirill_Burnside**.

Supported versions: **Minecraft 1.21.1** and **1.21.11** (Fabric Loader 0.18.4, Java 21).

## Project layout

| Folder | Contents |
|---|---|
| `common/` | Shared core: `.redux` pack format and protected `.reduxp` packs, rules, `.bbmodel` baking, pack protection, studio UI |
| `mc-1.21.1/` | Adapter and mixins for Minecraft 1.21.1 |
| `mc-1.21.11/` | Adapter and mixins for Minecraft 1.21.11 |
| `proguard/reduxion.pro` | ProGuard obfuscation config |
| `scripts/obf.sh` | Script that obfuscates a built jar |

## Building

```bash
./gradlew build -x test
```

The jars are written to `mc-1.21.1/build/libs/` and `mc-1.21.11/build/libs/`.

The build needs access to `maven.fabricmc.net`, `meta.fabricmc.net` and Mojang's servers (`piston-meta`, `piston-data`, `libraries.minecraft.net`).

## Obfuscation (release builds)

1. Download ProGuard 7.x and put its jars in a folder such as `/tmp/pg` (or set `PROGUARD_DIR`).
2. Export the classpath: `./gradlew :mc-1.21.1:dumpDeps :mc-1.21.11:dumpDeps`.
3. Run the script for each version, passing the Minecraft intermediary jar from the fabric-loom cache (`~/.gradle/caches/fabric-loom/.../minecraft-merged-intermediary*.jar`):

```bash
scripts/obf.sh 1.21.1  <intermediary-jar-1.21.1>
scripts/obf.sh 1.21.11 <intermediary-jar-1.21.11>
```

Output: `reduxion-mc<version>-1.0.0-obf.jar` and `mapping-<version>.txt` in `build/libs/`. Mappings are not committed; keep them privately.

Do not add `-dontpreverify` to the ProGuard config: it causes a VerifyError on Java 21.

## License

Copyright (c) 2026 Kirill_Burnside. All rights reserved.

This is proprietary software. The source code is published for viewing only; no permission is granted to copy, modify, redistribute or reuse it. See [LICENSE](LICENSE).
