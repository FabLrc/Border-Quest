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
    private static final String[] DONOR_PREFIXES = {"\u00a76#1 ", "\u00a77#2 ", "\u00a77#3 "};

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

        t.append(ModTranslations.t(TranslationKeys.HUD_HEADER).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (manager.isLastStage()) {
            t.append(ModTranslations.t(TranslationKeys.HUD_FREEDOM).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            t.append(ModTranslations.t(TranslationKeys.HUD_FREEDOM_SUB).withStyle(ChatFormatting.YELLOW));
            return t;
        }

        int stageNum    = state.currentStage + 1;
        int totalStages = BorderQuestManager.STAGES().size() - 1;
        StageDefinition stage = manager.getCurrentStage();

        t.append(ModTranslations.t(TranslationKeys.HUD_STAGE, stageNum, totalStages).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        t.append(ModTranslations.t(TranslationKeys.HUD_SEPARATOR).withStyle(ChatFormatting.DARK_GRAY));
        t.append(Component.literal(stage.title + "\n").withStyle(ChatFormatting.WHITE));
        t.append(ModTranslations.t(TranslationKeys.HUD_RADIUS).withStyle(ChatFormatting.GRAY));
        t.append(ModTranslations.t(TranslationKeys.HUD_RADIUS_VALUE, (int) stage.borderRadius).withStyle(ChatFormatting.WHITE));
        t.append(Component.literal("\n"));
        t.append(ModTranslations.t(TranslationKeys.HUD_RESOURCES_HEADER).withStyle(ChatFormatting.YELLOW));

        for (StageDefinition.ItemReq req : manager.getResolvedRequirements()) {
            int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
            boolean done  = submitted >= req.count();
            String name   = req.itemId().replace("minecraft:", "");
            ChatFormatting color = done ? ChatFormatting.GREEN : ChatFormatting.RED;
            String symbol = done ? "\u2714 " : "\u2718 ";
            t.append(ModTranslations.t(TranslationKeys.HUD_ITEM_PROGRESS, symbol, name, submitted, req.count()).withStyle(color));
        }

        int totalXpRequired = manager.getResolvedXpRequirements().stream().mapToInt(StageDefinition.XpReq::count).sum();
        if (totalXpRequired > 0) {
            int submittedXp = state.submittedXp;
            boolean done = submittedXp >= totalXpRequired;
            ChatFormatting color = done ? ChatFormatting.GREEN : ChatFormatting.RED;
            String symbol = done ? "\u2714 " : "\u2718 ";
            t.append(ModTranslations.t(TranslationKeys.HUD_XP_PROGRESS, symbol, submittedXp, totalXpRequired).withStyle(color));
        }

        return t;
    }

    private Component buildFooter(BorderQuestManager manager) {
        QuestState state = manager.getState();
        if (state.playerDonations.isEmpty()) return Component.empty();

        MutableComponent t = Component.empty();
        t.append(Component.literal("\n"));
        t.append(ModTranslations.t(TranslationKeys.HUD_DONORS_HEADER).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        List<Map.Entry<String, Integer>> top = state.playerDonations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        for (int i = 0; i < top.size(); i++) {
            String name = state.playerNames.getOrDefault(top.get(i).getKey(), "???");
            t.append(ModTranslations.t(TranslationKeys.HUD_DONOR_ENTRY, DONOR_PREFIXES[i], name, top.get(i).getValue()));
        }

        return t;
    }
}
