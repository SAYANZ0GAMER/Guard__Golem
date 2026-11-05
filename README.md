# GuardGolem (Paper plugin)

Target: **Minecraft Java Edition, Paper 26.3, Java 25**.

Adds a craftable **Guard Golem Spawn Egg**. The golem is a vanilla Iron Golem that
acts as a personal bodyguard for the player who spawned it.

## Build
Requires JDK 25 and Maven.

    mvn clean package

The jar is created at `target/GuardGolem-1.0.0.jar`.
If Maven cannot find `paper.version`, edit that property in `pom.xml` to the newest
`26.3.*` build of `io.papermc.paper:paper-api` on https://repo.papermc.io.

## Install
1. Stop the server.
2. Copy the jar into `plugins/`.
3. Start the server (Paper 26.3, Java 25).
4. No resource pack, no datapack and no client mod are needed. Vanilla clients can join.

## Recipe (crafting table)

    [      ] [Carved Pumpkin] [      ]
    [Iron Blk] [Iron Block  ] [Iron Blk]
    [      ] [Iron Block    ] [      ]

Result: 1 x Guard Golem Spawn Egg (shown in the recipe book automatically).

## Behaviour
- Right-click a block (or a liquid surface) with the egg to spawn a Guard Golem there.
  Works anywhere, in Survival, and consumes the egg (not in Creative).
- Looks, sounds, size, health and animations are the vanilla Iron Golem.
- Name: "Guard Golem" (egg: "Guard Golem Spawn Egg").
- If a player or mob hits the owner, every Guard Golem of that owner within 64 blocks
  targets the attacker (arrows and other projectiles count, via the shooter).
- A Guard Golem never targets or damages its owner on its own.
- If the owner hits it, it turns hostile to the owner for 30 s (refreshed by each hit).
  The anger ends on timeout, owner death, or owner logout.
- The owner is stored as a UUID in the entity's persistent data, so ownership survives
  restarts and other players are never treated as the owner.
- Normal Iron Golems (village, player-built, spawned by vanilla eggs) are untouched.

## Technical notes / limits (please read)
- A server plugin cannot register a brand-new entity *type* that vanilla clients render.
  The Guard Golem is therefore a vanilla `IronGolem` tagged via PersistentDataContainer.
  This is the only way to keep the exact vanilla model/animations on an unmodified client.
- The egg is the vanilla `IRON_GOLEM_SPAWN_EGG` item with a custom name and tag, so its
  texture/colours are identical by definition. Only the name differs.
- Dispensers are blocked from firing the egg (they would create an ownerless golem).
- `/summon` and vanilla Iron Golem eggs create normal Iron Golems, not Guard Golems.
- Not Folia-compatible (uses the Bukkit scheduler).
- If your server has claim/protection plugins, they can deny the egg through the normal
  PlayerInteractEvent result, which this plugin respects.
