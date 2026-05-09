package net.borderquest;

import com.mojang.brigadier.CommandDispatcher;
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
                Component.literal("Cette commande doit etre executee par un joueur.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> mgr.submitItems(player), false);
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
            Component.literal("[BorderQuest] Reinitialise au stade 1 par un operateur !").withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int skip(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        if (mgr.isLastStage()) {
            ctx.getSource().sendFailure(
                Component.literal("Deja au dernier stade !").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        var state = mgr.getState();
        for (var req : mgr.getResolvedRequirements()) {
            state.submittedItems.put(req.itemId(), req.count());
        }
        state.currentStage++;
        state.submittedItems.clear();
        mgr.save();
        mgr.applyBorder();
        mgr.updateSidebar();

        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("[BorderQuest] Stade passe ! Maintenant au stade " + (state.currentStage + 1))
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        BorderQuestConfig.load();
        mgr.load();
        mgr.applyBorder();
        mgr.updateSidebar();
        ctx.getSource().sendSuccess(() -> Component.literal("[BorderQuest] Config et etat recharges !").withStyle(ChatFormatting.GREEN), false);
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
            ctx.getSource().sendFailure(Component.literal("Commande reservee aux joueurs.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("Regardez un bloc pour le definir comme autel.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (mgr.addAltar(pos, name)) {
            String label = name.isBlank() ? "" : " \"" + name + "\"";
            ctx.getSource().sendSuccess(() -> Component.literal(
                "[BorderQuest] Autel" + label + " enregistre en "
                + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " (total: " + mgr.getAltarCount() + ")").withStyle(ChatFormatting.GREEN), false);
        } else {
            if (!name.isBlank()) {
                mgr.setAltarName(pos, name);
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "[BorderQuest] Nom de l'autel mis a jour : \"" + name + "\"").withStyle(ChatFormatting.GREEN), false);
            } else {
                ctx.getSource().sendFailure(Component.literal("[BorderQuest] Ce bloc est deja un autel.").withStyle(ChatFormatting.YELLOW));
            }
        }
        return 1;
    }

    private static int ladder(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        List<Map.Entry<String, Integer>> top = mgr.getTopDonors(10);
        if (top.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Aucun don enregistre.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        MutableComponent t = Component.empty();
        t.append(Component.literal("=== Classement des donateurs ===\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < top.size(); i++) {
            String name = mgr.getState().playerNames.getOrDefault(top.get(i).getKey(), "???");
            ChatFormatting color = (i == 0) ? ChatFormatting.GOLD : (i == 1) ? ChatFormatting.GRAY : ChatFormatting.WHITE;
            t.append(Component.literal("#" + (i + 1) + " " + name + " - " + top.get(i).getValue() + "\n").withStyle(color));
        }
        ctx.getSource().sendSuccess(() -> t, false);
        return 1;
    }

    private static int removeAltar(CommandContext<CommandSourceStack> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendFailure(noManager()); return 0; }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Commande reservee aux joueurs.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("Regardez un autel pour le retirer.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (mgr.removeAltar(pos)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "[BorderQuest] Autel retire (restants: " + mgr.getAltarCount() + ")").withStyle(ChatFormatting.GREEN), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("[BorderQuest] Ce bloc n'est pas un autel.").withStyle(ChatFormatting.YELLOW));
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
        return Component.literal("[BorderQuest] Le mod n'est pas encore initialise.").withStyle(ChatFormatting.RED);
    }
}
