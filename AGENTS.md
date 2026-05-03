# Citadel

A Paper plugin (Kotlin/Gradle) that runs the lobby side of Decked Out 2 on the Tracked Out server. Handles deck management, shops, queuing, leaderboards, trophies, spectating, and in-game UI.

## Project Structure

```
src/main/kotlin/org/trackedout/citadel/
├── Citadel.kt                  # Plugin entry point
├── TrophyManager.kt            # Renders ToT trophies (signs, heads, holograms)
├── InventoryManager.kt         # Card/deck inventory sync with Dunga Dunga
├── LeaderboardTaskRunner.kt    # Leaderboard display updates
├── StatusTaskRunner.kt         # Dungeon status polling
├── ScheduledTaskRunner.kt      # Periodic task scheduling
├── PlayerExtensions.kt         # Player utility extensions
├── WorldGuardExtensions.kt     # WorldGuard region helpers
├── APIExtensions.kt            # Dunga Dunga API helpers
├── commands/                   # All slash commands (/do, /tots, /log-event, etc.)
├── listeners/                  # Event listeners (join, death, queue, echo shard)
├── inventory/                  # GUI views (deck management, shops, spectate)
├── shop/                       # Shop command and data
├── mongo/                      # MongoDB data classes (trophies, events, scores, etc.)
└── config/                     # Config and Brilliance data loading
```

## Build & Deploy

Build mods and deploy to the lobby server (from `~/tracked-out/davybones`, not the citadel directory):
```bash
KUBECONTEXT=burn just build-mods && export ST=$(date -u +%FT%TZ) && KUBECONTEXT=burn just reload-lobby && kubectl --context burn logs -n davybones deployments/lobby --since-time="$ST"
```

You must ALWAYS reload-lobby after building to verify the plugin loads without errors. Never skip this step.

After verifying the plugin reloads cleanly, commit the changes with a descriptive message. Stage only the files you changed — don't use `git add .`. Use imperative mood, sentence case, no prefix, no trailing period (e.g. "Add /tots command with tp, list, search, info subcommands").

## Local Dev

Depends on [Agronet](https://github.com/trackedout/agronet-fabric). The cached jar is fine unless you've changed Agronet — and `build-mods` rebuilds it automatically.

Run tests:
```bash
./gradlew test              # Unit tests only
./gradlew testInteg         # Integration tests only
```

## Key Dependencies

- **Paper API** 1.20.1 — Minecraft server API
- **MongoDB Kotlin Sync Driver** — Direct DB access for trophies, leaderboards, status
- **Dunga Dunga API** (OkHttp/Moshi) — REST client for events, inventory, scores
- **FancyHolograms** — Hologram rendering (trophy displays, leaderboards)
- **WorldGuard** — Region-based event handling
- **ACF (Aikar Command Framework)** — Command registration and parsing
- **rtag** — NBT tag manipulation for items
- **scoreboard-library** — Sidebar scoreboard rendering

## In-Game Verification

Read sign content:
```bash
KUBECONTEXT=burn just exec lobby rcon-cli data get block <x> <y> <z> front_text
```

Check block type:
```bash
KUBECONTEXT=burn just exec lobby rcon-cli execute if block <x> <y> <z> minecraft:<block_type>
```

**WARNING**: Never use `setblock`, `fill`, or any destructive commands. Use `execute if block` or `data get block` only.
