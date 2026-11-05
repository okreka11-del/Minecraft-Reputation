# Minecraft Reputation

Minecraft Java 1.21.1 / NeoForge. Java 21.

## Implemented
- Individual reputation starts at 100, clamped to -100..200.
- Suspicion starts at 0.
- Reputation and suspicion persist through death.
- `/reputation get` shows both values.
- `/reputation set <value>` requires operator permissions.
- Killing a hostile monster grants +1 reputation.
- Killing an animal costs -2 reputation.
- Killing a villager costs -20 reputation.
- Reputation changes are shown as action-bar messages.

## Test
1. Download the successful `minecraft-reputation-jar` artifact from GitHub Actions and extract the JAR.
2. Install in the `mods` folder of a Minecraft 1.21.1 NeoForge profile.
3. In a test world with cheats enabled, run `/reputation get`.
4. Defeat a hostile monster, kill an animal, and kill a villager; verify each change with `/reputation get`.
5. Re-enter the world and test persistence through death.

## Not implemented yet
World reactions, villagers' trading discounts, mob targeting changes, NPCs, trials, multiplayer behavior refinements, and graphical HUD.

## Build
GitHub Actions runs Gradle on Java 21. This code change has not yet been confirmed to compile in CI.
