package net.borderquest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SidebarDisplay {

    private static final String OBJECTIVE_NAME = "bq_sidebar";

    private final MinecraftServer server;

    public SidebarDisplay(MinecraftServer server) {
        this.server = server;
    }

    public void init() {
        var scoreboard = server.getScoreboard();
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) scoreboard.removeObjective(existing);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, null);
    }

    public void update(BorderQuestManager manager) {
        Component header = buildHeader(manager);
        Component footer = buildFooter(manager);
        var packet = new ClientboundTabListPacket(header, footer);
        server.getPlayerList().broadcastAll(packet);
    }

    public void clear() {
        var packet = new ClientboundTabListPacket(Component.empty(), Component.empty());
        server.getPlayerList().broadcastAll(packet);
    }

    // -----------------------------------------------------------------------

    private Component buildHeader(BorderQuestManager manager) {
        MutableComponent t = Component.empty();
        QuestState state = manager.getState();

        t.append(Component.literal("\u2605 Border Quest \u2605\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (manager.isLastStage()) {
            t.append(Component.literal("LA BARRIERE EST TOMBEE !\n").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            t.append(Component.literal("Felicitations, vous avez tout accompli !").withStyle(ChatFormatting.YELLOW));
            return t;
        }

        int stageNum    = state.currentStage + 1;
        int totalStages = BorderQuestManager.STAGES().size() - 1;
        StageDefinition stage = manager.getCurrentStage();

        t.append(Component.literal("Stade " + stageNum + "/" + totalStages).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        t.append(Component.literal(" \u2014 ").withStyle(ChatFormatting.DARK_GRAY));
        t.append(Component.literal(stage.title + "\n").withStyle(ChatFormatting.WHITE));
        t.append(Component.literal("Rayon actuel : ").withStyle(ChatFormatting.GRAY));
        t.append(Component.literal((int) stage.borderRadius + " blocs\n").withStyle(ChatFormatting.WHITE));
        t.append(Component.literal("\n"));
        t.append(Component.literal("Ressources a collecter :\n").withStyle(ChatFormatting.YELLOW));

        for (StageDefinition.ItemReq req : manager.getResolvedRequirements()) {
            int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
            boolean done  = submitted >= req.count();
            String name   = req.itemId().replace("minecraft:", "");
            ChatFormatting color = done ? ChatFormatting.GREEN : ChatFormatting.RED;
            String symbol = done ? "\u2714 " : "\u2718 ";
            t.append(Component.literal("  " + symbol + name + " : " + submitted + "/" + req.count() + "\n").withStyle(color));
        }

        return t;
    }

    private Component buildFooter(BorderQuestManager manager) {
        QuestState state = manager.getState();
        if (state.playerDonations.isEmpty()) return Component.empty();

        MutableComponent t = Component.empty();
        t.append(Component.literal("\n"));
        t.append(Component.literal("Top Donateurs\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        List<Map.Entry<String, Integer>> top = state.playerDonations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        String[] prefixes = {"\u00a76#1 ", "\u00a77#2 ", "\u00a77#3 "};
        for (int i = 0; i < top.size(); i++) {
            String name = state.playerNames.getOrDefault(top.get(i).getKey(), "???");
            t.append(Component.literal(prefixes[i] + name + " - " + top.get(i).getValue() + "\n"));
        }

        return t;
    }
}
