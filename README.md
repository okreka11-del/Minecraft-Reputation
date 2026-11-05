# Minecraft Reputation — 0.3.0-alpha

Minecraft Java 1.21.1, NeoForge, Java 21.

## Reputation
- Individual score starts at 100, range -100 to 200, persists through death.
- Killing hostile mobs +1 (five-second reward cooldown), animals -2, villagers -20, players -30.
- Six ranks and commands: `/reputation get`, `help`, `set`, `add`, `suspicion`.

## World reactions
- Villagers flee notorious players; reputation 0 or below blocks villager trading.
- Cows, pigs and sheep flee notorious players, and animal interaction/feeding is blocked at reputation 0 or below, even when holding food.
- At -50 or below, animals produce CLOUD vapor-like particles and briefly slow nearby players.
- At -50 or below, monsters and iron golems deal 3x incoming damage to the player.
- Iron golems target nearby notorious players.
- At 151 or above, hostile mobs clear their target if it is the renowned player; pillagers also try to target other monsters. Vanilla AI may reassign targets.
- At 151 or above, villagers show happy particles and animals show hearts.
- Village block breaking is canceled for score 0 or below. The temporary village detector uses nearby villagers (24-block radius), not actual village boundaries.
- For score 1..150, breaking blocks near villagers costs 1 reputation per block. At 151+ there is no penalty.
- A client HUD draws a fixed upper-left bar rather than the previous periodic actionbar text.

## Known limitations / tests
**This code has not yet been verified to compile or run in GitHub Actions.**
- Multiplayer client synchronization of the HUD reputation score is not implemented/verified; the client may display its default value.
- Existing vanilla AI may override mob targeting and navigation.
- Pillagers' vanilla hostility may override the alliance logic; damage from pillagers is canceled for players at 151+.
- Animal breeding by existing in-love animals or other means is not comprehensively prevented; feeding interaction is blocked.
- Vapor is implemented with CLOUD particles, not a physical projectile.
- Village protection is proximity-based, not ownership-aware.

## Suggested test
1. Install the newly built JAR in Minecraft 1.21.1 NeoForge (remove older JARs).
2. Set reputation to -100 and test villager trading, feeding cows/sheep/pigs, golem aggression, monster damage and block breaking near villagers.
3. Set reputation to 200 and test pillager hostility, animal hearts, villager happiness and breaking village blocks.
4. Check upper-left HUD in singleplayer and multiplayer, and whether it updates after `/reputation set`.
5. Report any build errors or unexpected AI behavior with screenshots/logs.


## Settlement prototype (November 2026)

This branch includes a **server-side prototype**, not a finished village overhaul.

* An operator standing near villagers registers a settlement with `/settlement register` (requires permission level 2). Registered settlement centres, faction ownership and build stages persist through `SavedData` in the dimension.
* `/settlement status` displays the closest registered settlement within 96 blocks.
* At reputation **−80 or lower**, `/settlement capture` starts occupation when at least three pillagers are nearby. At reputation **+151 or higher**, `/settlement liberate` starts liberation once nearby pillagers are defeated. **These are early test conditions, not a finished raid/victory detector.**
* Every Minecraft day, the settlement attempts one non-destructive stage of small fence/lookout decorations (up to three stages). Existing blocks are **never replaced**. This is deliberately **not yet** a full fortress conversion, automatic world generation or building restoration.
* Operator commands `/specialist guard`, `/specialist builder`, `/specialist armorer` assign persistent roles to the nearest villager (first two) or pillager (last) within six blocks. Guards rally nearby iron golems when pillagers attack; builders repair injured golems once per day; pillager armorers periodically grant nearby pillagers brief Resistance I. These are **functional behaviors**, but do not yet include unique models, workstations, housing, schedules, profession registration, or trading.
* The prototype avoids scanning all entities in the world: specialist work only runs around active players. Decoration placement checks loaded chunks, solid ground and empty space.

### Verification status

Code changes are committed to GitHub. A full NeoForge Gradle build and Minecraft runtime test **have not been verified** in the current environment. The GitHub Actions workflow attempts a Java 21 Gradle build on pushes; check its latest result before treating a downloadable artifact as a working JAR. No independent Minecraft client/server smoke test has been completed.

### Planned next stage

Register real professions and job-site blocks with textures and loot tables; integrate generated structures into village world generation; make occupation depend on actual raid victory and village state; persist builder tasks; add protected player-owned building regions, NPC pathfinding and proper trading screens; test multiplayer and dimension loading.
