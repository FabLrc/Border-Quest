package net.borderquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.*;

public class BiomeResourceResolver {

    private static final Map<String, List<BiomeMapping>> CATEGORY_MAPPINGS = new HashMap<>();

    private static final Map<String, String> CATEGORY_FALLBACKS = new HashMap<>();

    static {
        List<BiomeMapping> logMappings = new ArrayList<>();

        logMappings.add(new BiomeMapping("minecraft:oak_log",
            Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
            Biomes.FOREST, Biomes.FLOWER_FOREST,
            Biomes.SWAMP, Biomes.MEADOW,
            Biomes.RIVER, Biomes.WINDSWEPT_HILLS,
            Biomes.WINDSWEPT_GRAVELLY_HILLS,
            Biomes.WINDSWEPT_FOREST,
            Biomes.STONY_PEAKS, Biomes.STONY_SHORE));

        logMappings.add(new BiomeMapping("minecraft:spruce_log",
            Biomes.TAIGA, Biomes.SNOWY_TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.SNOWY_PLAINS, Biomes.GROVE, Biomes.SNOWY_SLOPES));

        logMappings.add(new BiomeMapping("minecraft:birch_log",
            Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST));

        logMappings.add(new BiomeMapping("minecraft:acacia_log",
            Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA));

        logMappings.add(new BiomeMapping("minecraft:jungle_log",
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE));

        logMappings.add(new BiomeMapping("minecraft:dark_oak_log",
            Biomes.DARK_FOREST, Biomes.PALE_GARDEN));

        logMappings.add(new BiomeMapping("minecraft:mangrove_log",
            Biomes.MANGROVE_SWAMP));

        logMappings.add(new BiomeMapping("minecraft:cherry_log",
            Biomes.CHERRY_GROVE));

        CATEGORY_MAPPINGS.put("logs", logMappings);
        CATEGORY_FALLBACKS.put("logs", "minecraft:oak_log");
    }

    public static Set<ResourceKey<Biome>> scanBiomes(ServerLevel level, double radius) {
        Set<ResourceKey<Biome>> found = new HashSet<>();
        int step = Math.max(4, (int) (radius / 10));
        step = Math.min(step, 16);
        int r = (int) radius;

        for (int x = -r; x <= r; x += step) {
            for (int z = -r; z <= r; z += step) {
                Holder<Biome> biomeEntry = level.getBiome(new BlockPos(x, 64, z));
                biomeEntry.unwrapKey().ifPresent(found::add);
            }
        }

        return found;
    }

    public static StageDefinition.ItemReq resolveCategory(String category, int count,
                                                           Set<ResourceKey<Biome>> presentBiomes) {
        List<BiomeMapping> mappings = CATEGORY_MAPPINGS.get(category);
        if (mappings == null) {
            BorderQuest.LOGGER.warn("[BorderQuest] Categorie inconnue : {}", category);
            return new StageDefinition.ItemReq("minecraft:cobblestone", count);
        }

        for (BiomeMapping mapping : mappings) {
            for (ResourceKey<Biome> biome : mapping.biomes) {
                if (presentBiomes.contains(biome)) {
                    return new StageDefinition.ItemReq(mapping.itemId, count);
                }
            }
        }

        String fallback = CATEGORY_FALLBACKS.getOrDefault(category, "minecraft:cobblestone");
        BorderQuest.LOGGER.info("[BorderQuest] Aucun biome correspondant pour '{}', fallback: {}", category, fallback);
        return new StageDefinition.ItemReq(fallback, count);
    }

    private static class BiomeMapping {
        final String itemId;
        final List<ResourceKey<Biome>> biomes;

        @SafeVarargs
        BiomeMapping(String itemId, ResourceKey<Biome>... biomes) {
            this.itemId = itemId;
            this.biomes = List.of(biomes);
        }
    }
}
