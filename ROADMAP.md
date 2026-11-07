# Minecraft Reputation — five integrated milestones

Target: Minecraft Java 1.21.1 / NeoForge. Keep one source repository and one release JAR. Development milestones are not separate incompatible mod distributions.

## Milestone 1 — HUD and reputation book
- New player score: **0 (Neutral)**, not +100 or +168.
- Reputation clamp: -100..200; per-player persisted data, server-authoritative sync.
- All eleven reference ranks and correctly mapped thresholds.
- Rounded Minecraft XP-like bar, pixel font, current rank only, hover legend adapted to GUI scale.
- Reputation book: score, action history, faction relationships, settlement status, quests, unlocks and help.
- Preserve existing player scores in saved worlds; do not silently reset them.
- Validate multiplayer HUD, locale and client-server sync.

## Milestone 2 — Light and Dark
- Separate relationships with villagers, pillagers, animals, hostile mobs, golems and dragon.
- Reputation-gated buffs, debuffs, gifts and penalties with anti-farm cooldowns.
- Special dragon conversations and choices.
- Separate player reputation and faction reputation where appropriate.

## Milestone 3 — Armies, orders and G key
- Server-authoritative recruit/follow/guard/patrol/attack/retreat/disband.
- Formation, unit caps, persistence, ownership, target safety and multiplayer permissions.
- Key G opens the command wheel/panel, with proper key mapping and network packets.
- Settlement defence and pillager offensive AI.

## Milestone 4 — Villagers, illagers and interaction
- Twenty unique professions, plus guards/builders/weaponsmith concepts as separate NPC roles.
- Distinct Minecraft-style outfits and 3D workstations; schedules, XP, levels and profession assignment.
- Right click: Trade / Talk / Quests; Shift-right-click: trade.
- Reputation-dependent greetings, pricing, relationships and actual quest rewards.
- Village governance, comfortable administration screen and specialist AI.

## Milestone 5 — Settlements and integrated final release
- Worldgen: villages, fountains, markets, workshops, inn, homes, hero house, fences and watchtowers.
- Pillager outposts: prisons, farms, taverns, barracks, towers, markets and captives.
- Real loot, craftable items, settlement economy and supply chain.
- Capture, liberation and gradual rebuilding without destroying player builds.
- Run Gradle, JSON/model checks, automated tests where available, and real in-game tests.
- Release **one** final installable mod JAR, with tested download link and full report.

## Release discipline
1. Work in the same integration branch until a milestone is coherent.
2. No 'final' claims before successful build and required gameplay verification.
3. Do not publish placeholder textures or incomplete gameplay as finished.
4. Keep a changelog with verified / incomplete / not-tested distinctions.
5. Never confuse source archives or dev artifacts with an installable JAR.
