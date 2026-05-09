package net.borderquest.mixin;

import net.borderquest.BorderQuest;
import net.borderquest.BorderQuestConfig;
import net.borderquest.BorderQuestManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bloque l'accès à certaines dimensions jusqu'à un stade minimum configurable.
 * Exemple : le Nether est verrouillé jusqu'au stade 5, l'End jusqu'au stade 7.
 * Cible la méthode teleportTo(TeleportTransition) (26.1.x+).
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDimensionMixin {

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void bq_onTeleportTo(TeleportTransition target, CallbackInfoReturnable<ServerPlayer> cir) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) return;
        if (target == null) return;

        String worldId = target.newLevel().dimension().identifier().toString();
        BorderQuestConfig cfg = BorderQuestConfig.get();
        int currentStage1Based = mgr.getState().currentStage + 1;

        for (BorderQuestConfig.WorldLock lock : cfg.worldLocks) {
            if (lock.worldId.equals(worldId) && currentStage1Based < lock.requiredStage) {
                ServerPlayer self = (ServerPlayer) (Object) this;
                self.sendSystemMessage(
                    Component.literal("[BorderQuest] Ce monde est verrouille jusqu'au stade "
                        + lock.requiredStage
                        + " (vous etes au stade " + currentStage1Based + ").")
                        .withStyle(ChatFormatting.RED),
                    true
                );
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
