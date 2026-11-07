# Venzils PVP Essentials (Fabric, client-side, MC 26.2)
**Included:** Spotify now-playing (desktop app, no login), FPS/CPS HUD (left + optional right click), reach display, nearest-TNT fuse timer, butterfly-knife model on every sword, face-of-your-skin mod icon, JSON config (`config/venzilspvp.json`).
**Not included (use these mods alongside):** performance -> Sodium, Lithium, FerriteCore, Entity Culling, ImmediatelyFast, ModernFix. Voice chat -> Simple Voice Chat (install on client + server). Skyboxes -> a skybox resource pack (e.g. FabricSkyBoxes-compatible packs). Config GUI -> Mod Menu.
## Build
1. Install JDK 25 (26.x needs it). 2. Open https://fabricmc.net/develop, pick 26.2, copy the versions into `gradle.properties`. 3. `gradle build` -> `build/libs/venzils-pvp-essentials-1.0.0.jar` into `.minecraft/mods`.

## Build with GitHub Actions (no local setup)
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The build runs on every push, or press **Run workflow**.
3. When it's green, open the run and download the **venzils-pvp-essentials** artifact (the jar is inside). Put the jar in `.minecraft/mods`.
4. For a permanent download link, tag a release: `git tag v1.0.0 && git push --tags`; the jar appears under **Releases**.
The workflow auto-picks the newest Fabric API for `minecraft_version` in `gradle.properties`. If the build fails with "No Fabric API found", 26.2 support hasn't been published yet.
