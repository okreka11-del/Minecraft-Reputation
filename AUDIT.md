# Minecraft Reputation — code audit and next-build gates (2026-11-06)

Repository: `okreka11-del/Minecraft-Reputation`; integration branch: `feature/large-village-overhaul`; PR #1. Target Java 21, Minecraft 1.21.1, NeoForge.

**This is a source/CI audit, NOT a claim that the mod has been play-tested.** GitHub Gradle `build` succeeds on the listed recent fixes, but CI currently reports `test NO-SOURCE` and `testJunit NO-SOURCE`. Minecraft client, dedicated server, multiplayer and save-migration runtime tests remain required.

## Changes verified by CI (compiled; gameplay unverified)

- `5276fbd`: eliminate unintended immunity of heroes to ordinary monsters; dark-player monster damage no longer tripled.
- `c7cf1fc`: reputation monster pathfinding no longer pulls an already-engaged mob toward a different player.
- `ad8e261`: dragon portal now checks empty space and solid ground before placing a block.
- `0baa61c` and `9dd213b`: village buffs respect settlement faction; remove duplicate potion handler.
- `9fd86a3`: preserve/recover UUID-owned legacy hero homes where original footprint can be identified; refresh signs after chunks load; place two-part bed.
- `5418214`: village stage increases based on built houses/structures, not unconditional stage 3.
- `5b9a065`: custom workstation assignment requires the `reputation` block namespace.

**Warning:** older cottages without owner UUID, altered/removed legacy house furniture, or unknown original positions cannot always be migrated automatically. Migration must never claim an unrelated player build.

## Mechanic-by-mechanic source review

| Subsystem | In source | Important gap / verification |
| --- | --- | --- |
| Core reputation | Persistent player attachments; initial score 0; -100..200 range; 11 ranks | Need unit tests for thresholds, save/load and death |
| Reputation rewards | Monster kills: +1, maximum 10/day; one-off hunter quest | Confirm day rollover and PvP anti-farm on server |
| Gifts | Per-day cap 10 and time cooldown; NPC/animal/golem gift sources | Verify multiple-player drops and chunk reload |
| Factions | Six standings (villagers, pillagers, animals, monsters, golems, dragon) | Verify independent standings and score interactions |
| Villager trade/dialogue | Reputation pricing, GUI dialogue, server-validated packet | Dialogue options mostly static; no branching quests/economy |
| Animal behavior | Approach hero / flee villain, cosmetic negative reaction | Real AI may override navigation; test breeding interactions |
| Hostile AI | Light target pursuit; dark allegiance/follow; repulsive fart | Dedicated server/multiplayer simulation still untested |
| Golems | Golem retaliation and guard coordination | Target-switching conflicts possible; test multiplayer |
| Dragon | Proximity/rank-gated chat commands and portal | Portal not a full framed structure; test End dimension behavior |
| HUD | Pixel-style reputation HUD with rank legend | Fractional 1.30x text scale can make rendering less crisp |
| Journal | Seven sections, J key, interactive pages | GUI-scale-dependent shrinking; full visual test needed |
| Village overview | V key and server overview packet | Basic view, not complete village administration |
| Special NPCs | Guard, builder, pillager armorer commands/AI | Only three functional special roles; no separate NPC models |
| 20 professions | Registry, workstation blocks, voxel models, assignments | Most professions lack distinct AI, schedules, trading/quests, skins |
| Settlements | SavedData, capture/liberate commands, visit-triggered buildings | Not proper independent worldgen; raid/occupation is prototype |
| Hero houses | Per-player UUID and stored coordinates, signs, locate command | No owner access control; terrain/survival/legacy scenarios need game test |
| Armies/G panel | Not implemented as requested | Recruit/follow/guard/patrol/attack UI, packet security and save persistence |
| Economy/outposts | Some workstation, decoration and loot assets | Real markets, production chains, prisons, captives and raids not complete |
| Assets/localization | English/Russian language files, 20 block model/recipe families | JSON/runtime validation not automated; some models reuse vanilla textures |
| Docs/release | CI produces installable JAR artifact | README is outdated (states score starts 100, old rewards/version) |

## Open priorities (fix before claiming feature-complete)

1. **P0:** Add non-empty automated tests; verify server load, client UI, mixed player ranks, world restart, and saved data migration.
2. **P0:** Replace overly broad village break-block rule: a villager within 24 blocks currently makes ordinary player mining/edits punishable or forbidden, including natural terrain. Introduce structure ownership/provenance before enforcing destruction penalties.
3. **P1:** Complete hero house protections and transfer/restoration policies; test unloaded chunks, multiple players, 16-character usernames, existing structures.
4. **P1:** Finish real G-key army panel with server-authoritative commands, permissions, unit caps and persistence.
5. **P1:** Implement distinct functional behavior/quests/trades/schedules for all 20 professions, not just labels and workstations.
6. **P1:** Implement genuine structure generation integrated with village/outpost worldgen and gradual rebuilding; protect player builds.
7. **P1:** Replace static trade/dialogue and placeholder village economy with actual loops and consequences.
8. **P2:** Clean up dead/unreachable interaction branches, unused legacy classes, repeated mob scans, and duplicate event logic.
9. **P2:** Make HUD and journal legible at each GUI scale, and audit every recipe, model, blockstate and translation key.
10. **P2:** Update README, Gradle/Actions deprecation warnings, and release notes to accurate verified behavior.

## Required execution order

For each functional fix: inspect code -> patch -> commit -> check **actual branch-head** CI workflow runs -> inspect failure logs and repair before moving on -> where possible add regression coverage -> report verified versus unverified. A successful Gradle compilation is **not** a Minecraft gameplay test. Keep PR #1 open, do not label the current mod or JAR final, and preserve existing worlds.
