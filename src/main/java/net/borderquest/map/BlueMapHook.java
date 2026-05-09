package net.borderquest.map;

import net.borderquest.BorderQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Intégration BlueMap temporairement désactivée.
 * BlueMap n'a pas encore publié de version compatible Minecraft 26.1.
 * 
 * TODO : Réactiver quand une version compatible BlueMap API sera disponible.
 * Dépendance à ajouter dans build.gradle :
 *     compileOnly "de.bluecolored:bluemap-api:<version-26.1>"
 */
public class BlueMapHook {

    public BlueMapHook(MinecraftServer server) {
        BorderQuest.LOGGER.warn("[BorderQuest] BlueMapHook cree mais non fonctionnel (pas de BlueMap 26.1 disponible)");
    }

    public void register() {
    }

    public void updateBorder(double centerX, double centerZ, double radius) {
    }

    public void addAltarMarker(BlockPos pos, String name) {
    }

    public void removeAltarMarker(BlockPos pos) {
    }
}
