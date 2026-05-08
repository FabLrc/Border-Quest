# Border Quest — Agent Instructions

Server-side Fabric mod for Minecraft 1.21. Single-module Gradle project.

## Build

Only `gradlew.bat` exists (no Unix `gradlew`). On macOS/Linux use:
```
java -jar gradle/wrapper/gradle-wrapper.jar build
```
Requires Java 21.

Output: `build/libs/border-quest-1.0.0.jar`

No test, lint, or typecheck commands exist. CI runs `build.bat` on `windows-latest`.

## Source layout

```
src/main/java/net/borderquest/        # all mod code (11 files + 5 map hooks + 6 mixins)
  BorderQuest.java                    # ModInitializer entrypoint
  BorderQuestManager.java             # core game loop, border, progression
  BorderQuestCommand.java             # /bq command tree
  BorderQuestConfig.java              # config/borderquest.json
  QuestState.java                     # persisted state (Gson, <world>/data/borderquest_state.json)
  StageDefinition.java                # stage/reward data model with records, unlockRecipes
  BiomeResourceResolver.java          # biome → item resolution
  SidebarDisplay.java                 # tab-list HUD
  DiscordWebhook.java                 # async HTTP post
  mixin/SpawnProtectionMixin.java     # @Overwrite disables vanilla spawn protection
  mixin/PlayerDimensionMixin.java     # gates Nether/End per stage
  mixin/RecipeLockMixin.java          # 4 inner mixins: crafting table, player 2x2, furnace, recipe book
  map/                                # BlueMap/Dynmap/JourneyMap/Xaero (reflection)
src/main/resources/fabric.mod.json    # id=borderquest, environment=server
remappedSrc/                          # Loom-generated, do not edit
server/                               # actual server runtime
```

## Key conventions

- **Server-side only** (`"environment": "server"`). No client-side code.
- BlueMap is the only compile-time map dependency (`compileOnly`). All other map integrations use reflection.
- `@Overwrite` on `SpawnProtectionMixin` is intentional — disables spawn protection entirely so all players can break blocks in the zone.
- Commands use Fabric API's `CommandRegistrationCallback`. All start with `/bq`.
- Gson serialization for both config and saved state. Config auto-generated on first server start.
- Recipe locking via `unlockRecipes` per stage. Uses 4 inner mixin classes in `RecipeLockMixin.java` targeting `CraftingScreenHandler`, `PlayerScreenHandler`, `AbstractFurnaceBlockEntity`, and `ServerPlayNetworkHandler`. The `ServerRecipeManager` API (not `RecipeManager`) is used for recipe lookup: `getFirstMatch()` and `get(NetworkRecipeId)`.

## gradle.properties

Minecraft 1.21.11, Yarn 1.21.11+build.4, Fabric Loom 1.13.6, Fabric API 0.141.3, Loader 0.18.4. Java 21 source/target.
