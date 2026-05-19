# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [2.1.0] — 2026-05-19

### Added

- **XP as a stage requirement**: Stages can now require experience points in addition to items.
  New config field: `borderExpansionDurationSeconds` (default 10 seconds for border animation).
  New stage field: `xpRequirements` (list of `{ "count": <int> }`).
  New command: `/bq submitxp <amount>` — donate XP points toward the current stage objective.
  XP progress is shown in `/bq status`, the tab-list sidebar HUD, and the web dashboard.
  XP donations are taken from the player's total experience points (not levels).
  Translation keys added: `borderquest.status.xp_*`, `borderquest.submit.xp_*`, `borderquest.hud.xp_progress`.

- **Translation system**: All mod messages are now translatable via JSON language files.
  Added `borderquest.command.language` config option (`"en_us"` by default, `"fr_fr"` also bundled).
  New files: `ModTranslations.java`, `TranslationKeys.java`, `lang/en_us.json`, `lang/fr_fr.json`.

- **Web dashboard + admin panel**: Embedded HTTP server (JDK `HttpServer`, zero dependencies)
  serving a live dashboard and admin panel on a configurable port (default 8123).
  - Public dashboard: stage progress, resource checklist, donation leaderboard, altar list,
    player count — auto-refreshes every 5 seconds.
  - Admin panel (password-protected): buttons for `/bq reload`, `/bq skip`, `/bq reset`,
    and a JSON config editor with save/reload.
  - Admin password randomly generated on first launch, printed once in server log,
    modifiable via config file or the admin panel itself.
  - Config section: `"dashboard": { "enabled": true, "port": 8123, "bindAddress": "127.0.0.1", "password": "..." }`
  - Thread-safe: all state reads/writes scheduled via `server.execute()` with `CompletableFuture`.
  New files: `DashboardServer.java`, `DashboardApi.java`, `dashboard.html`.

## [2.0.0-beta] — 2026-05-09

### Added

- **Migration to Minecraft 26.1.2**: Full support for the new unobfuscated
  Minecraft version with Mojang's official mappings (Yarn discontinued).
- **Java 25**: Build requires JDK 25 (was Java 21).
- **Fabric Loom 1.16.1**: Migrated to the new `net.fabricmc.fabric-loom` plugin.
- **Gradle 9.5**: Build system updated.

### Changed

- **All source code**: Migrated from Yarn to Mojang official mappings
  (17 files, 700+ line changes). Covers entity classes, networking packets,
  commands, recipes, sound system, and particle APIs.
- **Build system**: `modImplementation` → `implementation`,
  `remapJar` → `jar`. No more Yarn mappings line.
- **Fabric API**: Updated to 0.148.0+26.1.2 with Mojang-convention renames
  (`ServerTickEvents.END_LEVEL_TICK`, `ServerLevelEvents`, etc.).
- **BlueMap**: Temporarily disabled (no compatible API for 26.1 yet).

### Fixed

- **Spawn teleport**: Players outside border but above ground are now teleported.
- **Biome fallback**: Logs category fallback uses oak_log (was sandstone).
- **Altar color**: Orange color index on Xaero maps (was white).

## [1.1.0] — 2026-05-09

### Added

- **Recipe locking**: Lock specific item recipes behind configurable stages.
  Each stage has an `unlockRecipes` list. Recipes are cumulative — at stage N,
  all recipes from stages 0 through N are unlocked. Covers crafting table,
  player inventory (2×2), furnace/blast furnace/smoker, and recipe book.
- **Dynamic versioning**: Version is now set via git tags (`vX.Y.Z`).
  Local builds use the `gradle.properties` fallback.
- **Separate branches per Minecraft version**: `develop` (main development),
  `1.21.11` (stable releases), plus future version branches.
- **CI workflows**:
  - `build.yml` — compiles on every push/PR (all branches, no release).
  - `release.yml` — triggered by tags `v*`; builds, creates a GitHub Release,
    and publishes to Modrinth automatically.
- **JAR naming**: Includes both the mod version and the Minecraft version
  (`border-quest-<mcversion>-<version>.jar`).
- **AGENTS.md** — instruction file for AI agents working in this repo.

### Changed

- **Build system**: Replaced `build.bat` with direct `gradlew.bat` calls in CI.
  Removed `build.bat` and `install.bat`.
- **Documentation**: README updated with build instructions, branch strategy,
  and recipe locking documentation.

## [1.0.0] — 2026-05-09

### Added

- Base mod: progressive world border, altar system, stage rewards,
  dimension locks, donation leaderboard, altar particles, chat announcements,
  HUD sidebar.
- Map integrations: BlueMap, Dynmap, JourneyMap, Xaero's Minimap & World Map
  (reflection-based).
- Discord webhook on stage completion.
- Fully configurable via `config/borderquest.json`.
- Server-side only (no client installation required).
