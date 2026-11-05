# Minecraft Reputation — 0.2.0-alpha

Minecraft Java 1.21.1 / NeoForge, Java 21.

## Features
- Per-player reputation (starts 100, range -100..200) and suspicion (starts 0, range 0..100).
- Both values persist through death and world reloads.
- Six reputation tiers: Legend (151..200), Trusted (101..150), Neutral (51..100), Outcast (1..50), Wanted (-49..0), Nemesis (-100..-50).
- Killing a hostile monster: +1, with a five-second cooldown per player to discourage farming.
- Killing an animal: -2; villager: -20; another player: -30.
- Villagers flee players at reputation 0 or below, display angry particles, and display happy particles around legendary players.
- Cows, pigs and sheep flee players at reputation 0 or below. At -50 or lower, nearby animals emit slime-like spit particles and briefly slow the player. At 151 or higher, animals display heart particles.\n- Monsters drop their current target when it is a player with reputation 151 or higher (checked periodically).
- Monsters target nearby players at reputation -50 or lower when they have no target or a higher-reputation player as target.
- Periodic action-bar display of reputation, rank and suspicion.

## Commands
- `/reputation get`
- `/reputation help`
- `/reputation set <value>` (OP)
- `/reputation add <delta>` (OP)
- `/reputation suspicion <value>` (OP)

## Testing checklist
1. Download the successful `minecraft-reputation-jar` artifact from GitHub Actions and extract the JAR.
2. Place it in the `mods` folder of a Minecraft 1.21.1 NeoForge installation. Remove older versions.
3. Create a world with cheats and test `/reputation get`.
4. Kill a zombie, a cow, and a villager; verify score changes.
5. Run `/reputation set 0` near villagers, cows, pigs and sheep; watch them move away.\n6. Run `/reputation set -100` and stand near animals to test slime-spit particles and slowness.\n7. Run `/reputation set 200` near villagers and animals to test happy/heart particles.
6. Run `/reputation set -100` near hostile monsters; observe targeting.
7. Run `/reputation set 200` near hostile monsters; observe whether targeting is cleared.
8. Test `/reputation add -10` and `/reputation suspicion 70`.
9. Test death and reload persistence.

## Known limitations
This is an experimental alpha. Villager flight and monster targeting are basic server-side behaviors; vanilla AI may override them. A true client-rendered HUD, projectile spit, animal attacks, trading discounts, witness system, suspicion investigations, trials and custom NPCs are not implemented. Multiplayer behaviors need further testing. A successful CI build has not yet been verified for this update.
