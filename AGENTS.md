# Border Quest — Agent Instructions

Server-side Fabric mod for Minecraft 26.1. Single-module Gradle project.

## Build

Only `gradlew.bat` exists (no Unix `gradlew`). On macOS/Linux use:
```
java -jar gradle/wrapper/gradle-wrapper.jar build
```
Requires Java 25.

Output: `build/libs/border-quest-<mcversion>-<version>.jar` (version from `gradle.properties`, or override with `-Pmod_version=X.Y.Z`).

No test, lint, or typecheck commands exist.

CI:
- `build.yml` runs on all branches/PRs (compile check, no release)
- `release.yml` triggers on tag `v*` → builds, creates GitHub Release, publishes to Modrinth

Branches:
- `develop` — main dev branch (no releases)
- `1.21.11` — stable branch for Minecraft 1.21.11 releases
- Tags created on version branches only (`git tag vX.Y.Z` from `1.21.11`)

## Source layout

```
src/main/java/net/borderquest/        # all mod code (11 core + 5 map + 3 mixin files = 19)
  BorderQuest.java                    # ModInitializer entrypoint
  BorderQuestManager.java             # core game loop, border, progression
  BorderQuestCommand.java             # /bq command tree
  BorderQuestConfig.java              # config/borderquest.json
  QuestState.java                     # persisted state (Gson, <world>/data/borderquest_state.json)
  StageDefinition.java                # stage/reward data model with records, unlockRecipes
  BiomeResourceResolver.java          # biome → item resolution
  SidebarDisplay.java                 # tab-list HUD
  DiscordWebhook.java                 # async HTTP post
  ModTranslations.java                # server-side translation loader (Gson, classpath JSON)
  TranslationKeys.java                # constants for all translation keys (~48)
  mixin/SpawnProtectionMixin.java     # @Overwrite disables vanilla spawn protection (26.1.2)
  mixin/PlayerDimensionMixin.java     # gates Nether/End per stage
  mixin/RecipeLockMixin.java          # 4 inner mixins: crafting table, player 2x2, furnace, recipe book
  map/                                # BlueMap/Dynmap/JourneyMap/Xaero (reflection)
src/main/resources/
  fabric.mod.json                     # id=borderquest, environment=server
  assets/borderquest/lang/en_us.json  # English translations
  assets/borderquest/lang/fr_fr.json  # French translations
remappedSrc/                          # Loom-generated, do not edit
server/                               # actual server runtime
```

## Key conventions

- **Server-side only** (`"environment": "server"`). No client-side code.
- BlueMap est désactivé temporairement (pas de version compatible 26.1 disponible). Les autres intégrations cartographiques utilisent la réflexion.
- `@Overwrite` on `SpawnProtectionMixin` is intentional — disables spawn protection entirely so all players can break blocks in the zone.
- Commands use Fabric API's `CommandRegistrationCallback`. All start with `/bq`.
- Gson serialization for both config and saved state. Config auto-generated on first server start.
- Recipe locking via `unlockRecipes` per stage. Uses 4 inner mixin classes in `RecipeLockMixin.java` targeting `CraftingMenu`, `InventoryMenu`, `AbstractFurnaceBlockEntity`, and `ServerGamePacketListenerImpl` (Mojang mappings 26.1.2).
- Translation system via `ModTranslations.java` + `TranslationKeys.java`. All user-facing messages use `ModTranslations.t(TranslationKeys.XXX, args...)` returning `MutableComponent`. Language files are in `assets/borderquest/lang/`. Config option `language` defaults to `"en_us"`. Fallback chain: configured → `en_us` → raw key. `ModTranslations.load()` is called after config load in `SERVER_STARTED` and on `/bq reload`.
- **Update `CHANGELOG.md`** with every user-facing change. Follow the existing format and add a new `## [Unreleased]` section if none exists.

## gradle.properties

Minecraft 26.1.2, Fabric Loom 1.16.1, Fabric API 0.148.0+26.1.2, Loader 0.19.2. Java 25 source/target.
