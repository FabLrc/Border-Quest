package net.borderquest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Désactive la spawn protection pour que tous les joueurs puissent
 * casser des blocs dans la zone de jeu, quelle que soit leur distance au spawn.
 */
@Mixin(MinecraftServer.class)
public class SpawnProtectionMixin {

    /**
     * @author BorderQuest
     * @reason Tous les joueurs doivent pouvoir interagir librement dans la zone.
     */
    @Overwrite
    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        return false;
    }
}
