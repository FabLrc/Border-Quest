package net.borderquest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public class BorderQuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        dispatcher.register(
            Commands.literal("bq")
                // /bq  (sans sous-commande) → statut
                .executes(BorderQuestCommand::status)

                // /bq status — affiche l'objectif actuel
                .then(Commands.literal("status")
                    .executes(BorderQuestCommand::status))

                // /bq submit — soumet les items de l'inventaire
                .then(Commands.literal("submit")
                    .executes(BorderQuestCommand::submit))

                .then(Commands.literal("submitxp")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(BorderQuestCommand::submitXp)))

                // /bq reset — remet à zéro (op niveau 2)
                .then(Commands.literal("reset")
                    .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .executes(BorderQuestCommand::reset))

                // /bq skip — passe au stade suivant sans remplir l'objectif (op)
                .then(Commands.literal("skip")
                    .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .executes(BorderQuestCommand::skip))

                // /bq reload — recharge l'état depuis le fichier (op)
                .then(Commands.literal("reload")
                    .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .executes(BorderQuestCommand::reload))

                // /bq setaltar [nom] — enregistre le bloc regardé comme autel (op)
                .then(Commands.literal("setaltar")
                    .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .executes(BorderQuestCommand::setAltar)
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> setAltarNamed(ctx, StringArgumentType.getString(ctx, "name")))))

                // /bq removealtar — retire le bloc regardé des autels (op)
                .then(Commands.literal("removealtar")
                    .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .executes(BorderQuestCommand::removeAltar))

                // /bq ladder — classement des donateurs (accessible à tous)
                .then(Commands.literal("ladder")
                    .executes(BorderQuestCommand::ladder))
        );
    }

    // -----------------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) {
            ctx.getSource().sendFailure(noManager());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> mgr.getStatusText(), false);
        return 1;
    }

    private static int submit(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(
                ModTranslations.t(TranslationKeys.CMD_PLAYER_ONLY).withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> mgr.submitItems(player), false);
        return 1;
    }

    private static int submitXp(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(
                ModTranslations.t(TranslationKeys.CMD_PLAYER_ONLY).withStyle(ChatFormatting.RED));
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ctx.getSource().sendSuccess(() -> mgr.submitXp(player, amount), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        mgr.getState().reset();
        mgr.save();
        mgr.applyBorder();
        mgr.updateSidebar();

        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
            ModTranslations.t(TranslationKeys.BROADCAST_RESET).withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int skip(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        if (mgr.isLastStage()) {
            ctx.getSource().sendFailure(
                ModTranslations.t(TranslationKeys.CMD_ALREADY_FINAL).withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        var state = mgr.getState();
        for (var req : mgr.getResolvedRequirements()) {
            state.submittedItems.put(req.itemId(), req.count());
        }
        for (var xpReq : mgr.getResolvedXpRequirements()) {
            state.submittedXp = xpReq.count();
        }
        state.currentStage++;
        state.submittedItems.clear();
        state.submittedXp = 0;
        mgr.save();
        mgr.applyBorder();
        mgr.updateSidebar();

        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
            ModTranslations.t(TranslationKeys.BROADCAST_SKIP, state.currentStage + 1)
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        BorderQuestConfig.load();
        ModTranslations.load();
        mgr.load();
        mgr.applyBorder();
        mgr.updateSidebar();
        ctx.getSource().sendSuccess(() -> ModTranslations.t(TranslationKeys.CMD_RELOADED).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setAltar(CommandContext<CommandSourceStack> ctx) {
        return setAltarNamed(ctx, "");
    }

    private static int setAltarNamed(CommandContext<CommandSourceStack> ctx, String name) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_PLAYER_ONLY).withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_LOOK_BLOCK).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (mgr.addAltar(pos, name)) {
            String label = name.isBlank() ? "" : " \"" + name + "\"";
            ctx.getSource().sendSuccess(() -> ModTranslations.t(TranslationKeys.CMD_ALTAR_SET,
                label, pos.getX(), pos.getY(), pos.getZ(), mgr.getAltarCount())
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            if (!name.isBlank()) {
                mgr.setAltarName(pos, name);
                ctx.getSource().sendSuccess(() -> ModTranslations.t(TranslationKeys.CMD_ALTAR_NAME, name)
                    .withStyle(ChatFormatting.GREEN), false);
            } else {
                ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_ALTAR_DUPLICATE)
                    .withStyle(ChatFormatting.YELLOW));
            }
        }
        return 1;
    }

    private static int ladder(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        List<Map.Entry<String, Integer>> top = mgr.getTopDonors(10);
        if (top.isEmpty()) {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.LADDER_EMPTY).withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        MutableComponent t = Component.empty();
        t.append(ModTranslations.t(TranslationKeys.LADDER_HEADER).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < top.size(); i++) {
            String name = mgr.getState().playerNames.getOrDefault(top.get(i).getKey(), "???");
            ChatFormatting color = (i == 0) ? ChatFormatting.GOLD : (i == 1) ? ChatFormatting.GRAY : ChatFormatting.WHITE;
            t.append(ModTranslations.t(TranslationKeys.LADDER_ENTRY, i + 1, name, top.get(i).getValue()).withStyle(color));
        }
        ctx.getSource().sendSuccess(() -> t, false);
        return 1;
    }

    private static int removeAltar(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_PLAYER_ONLY).withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_LOOK_ALTAR).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (mgr.removeAltar(pos)) {
            ctx.getSource().sendSuccess(() -> ModTranslations.t(TranslationKeys.CMD_ALTAR_REMOVED, mgr.getAltarCount())
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            ctx.getSource().sendFailure(ModTranslations.t(TranslationKeys.CMD_ALTAR_NOT_FOUND)
                .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    /** Retourne la position du bloc visé par le joueur (portée 5 blocs), ou null. */
    private static BlockPos getLookedBlock(ServerPlayer player) {
        HitResult hit = player.pick(5.0, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hit).getBlockPos();
    }

    // -----------------------------------------------------------------------

    private static Component noManager() {
        return ModTranslations.t(TranslationKeys.CMD_NOT_INIT).withStyle(ChatFormatting.RED);
    }
}
