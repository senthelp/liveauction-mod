# Donut SMP Live Visual Auction (Fabric, MC 26.1.2, Java 25)

Build:
1. Edit `gradle.properties` -> set `fabric_version` to the Fabric API build for 26.1.2 (fabricmc.net/develop).
2. With JDK 25 + Gradle installed: `gradle build`  (or push to GitHub; the included Actions workflow builds it).
3. Take `build/libs/donut-smp-live-visual-auction-1.0.0.jar` (not `-sources`) -> `.minecraft/mods/` along with Fabric API.

Use: `/auction` -> pick an item -> HUD card appears. Bids are read from server messages containing "sent you $" and "Money".
