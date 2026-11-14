# Minecraft Reputation — integration development (Minecraft 1.21.1 / NeoForge)

**Status: work in progress, not a verified release.** Requires Java 21. All work belongs in the integration branch `feature/large-village-overhaul` and [PR #1](https://github.com/okreka11-del/Minecraft-Reputation/pull/1). See [ROADMAP.md](ROADMAP.md) for the five milestones and [AUDIT.md](AUDIT.md) for verified source findings and outstanding risks.

## Implemented in source (gameplay testing pending)

- Player reputation starts at **0 (Neutral)** for new players; allowed range **−100..200**, with eleven rank thresholds. Existing saved player reputation must be preserved.
- Reputation is persisted per player and synchronized to the client. A HUD and seven-section journal (J) expose reputation and related systems; readability across GUI scales remains unverified.
- Monster-kill reputation rewards are capped at **10 per Minecraft day**, with a separate daily cap of **10 gifts**. Quest progress is not intended to stop when the kill reward cap is reached. Multiplayer and day-rollover regression tests are still required.
- Separate faction standings cover villagers, pillagers, animals, monsters, golems and the dragon. NPC reactions and AI are implemented in part, but vanilla targeting/pathfinding may conflict.
- Settlement registration, capture/liberation and specialist guard/builder/armorer behaviors are prototypes, not complete world generation or occupation gameplay. Hero houses have owner-UUID recovery support with migration limitations.
- Workstations and registration exist for twenty professions, but distinct schedules, trade loops, quests, AI and textures are not complete for every role.

## Critical known limitations

- **Village block-breaking penalties currently rely on villagers within 24 blocks rather than proven structure ownership.** This can incorrectly punish normal mining or player construction; do not treat it as safe protection logic. See AUDIT.md P0.
- The requested server-authoritative **G-key army command panel** and complete army persistence/permissions are not implemented.
- Full independent village/outpost world generation, economy, captives, rebuilding and multiplayer permissions remain incomplete.
- Automated tests are currently absent (`test NO-SOURCE`, `testJunit NO-SOURCE` reported in the audit). Passing Gradle CI does **not** establish that the mod works in a Minecraft client or dedicated server.
- Older player houses without identifiable owner UUID/footprint may not be migrated automatically; migration must never claim unrelated player builds.

## Build and verification

Check [GitHub Actions](https://github.com/okreka11-del/Minecraft-Reputation/actions) for the **current branch-head commit**. A successful workflow confirms its build steps only, not game behavior. Do not label development artifacts as a final JAR.

Before release, test on Minecraft Java 1.21.1 with the matching NeoForge version: client and dedicated server startup; HUD/J at multiple GUI scales; save/reload and old-world migration; multiple players with different reputation; daily caps and quests; NPC hostility; settlement construction and ownership safety. Capture crash logs and reproducible steps for failures.

## Delivery plan

1. HUD and reputation book.
2. Light and Dark faction behavior.
3. Armies, orders and G panel.
4. Villagers, pillagers and interactions.
5. Settlements, world generation and final integration.

**One integration branch, one eventual release JAR, no claims of in-game verification without actual gameplay tests.**
