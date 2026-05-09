package net.borderquest;

import net.borderquest.map.MapIntegrationManager;
import net.borderquest.ModTranslations;
import net.borderquest.TranslationKeys;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BorderQuest implements ModInitializer {

    public static final String MOD_ID = "borderquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static BorderQuestManager manager;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(BorderQuestCommand::register);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BorderQuestConfig.load();
            ModTranslations.load();
            manager = new BorderQuestManager(server);
            manager.load();
            manager.setMapManager(new MapIntegrationManager(server));
            manager.applyBorder();
            manager.initSidebar();
            manager.updateSidebar();
            LOGGER.info("[BorderQuest] Mod charge — stade {}/{}",
                manager.getState().currentStage + 1,
                BorderQuestManager.STAGES().size() - 1);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.save();
                LOGGER.info("[BorderQuest] Etat sauvegarde.");
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) manager.tick();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (manager == null) return;

            ServerPlayer player = handler.player;
            ServerLevel world = server.overworld();

            safeSpawnTeleport(player, world, manager);

            player.sendSystemMessage(ModTranslations.t(TranslationKeys.JOIN_WELCOME));

            server.execute(() -> manager.updateSidebar());
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (manager == null) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (!manager.isAltar(pos)) return InteractionResult.PASS;

            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            Component result = manager.donateFromHand(serverPlayer);
            serverPlayer.sendSystemMessage(result, true);
            return InteractionResult.SUCCESS;
        });

        LOGGER.info("[BorderQuest] Initialise !");
    }

    private void safeSpawnTeleport(ServerPlayer player, ServerLevel world,
                                   BorderQuestManager mgr) {
        double px = player.getX();
        double pz = player.getZ();
        double py = player.getY();

        double cx = mgr.getBorderCenterX();
        double cz = mgr.getBorderCenterZ();
        double radius = mgr.getCurrentStage().borderRadius;

        boolean outsideBorder = Math.abs(px - cx) > radius || Math.abs(pz - cz) > radius;
        if (outsideBorder) {
            px = cx;
            pz = cz;
        }

        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) px, (int) pz);

        if (outsideBorder || py < topY - 2) {
            player.teleportTo(world, px, topY + 0.5, pz, Set.of(), player.getYRot(), player.getXRot(), false);
            LOGGER.info("[BorderQuest] Joueur {} teleporte a la surface ({}, {}, {})",
                player.getName().getString(), (int) px, topY, (int) pz);
        }
    }
}
