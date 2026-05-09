package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.borderquest.map.MapIntegrationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.borderquest.StageDefinition.CategoryReq;
import static net.borderquest.StageDefinition.ItemReq;

public class BorderQuestManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Raccourci vers la liste des stades (définie dans la config). */
    public static List<StageDefinition> STAGES() { return BorderQuestConfig.get().stages; }

    private final MinecraftServer server;
    private QuestState state;
    private final Path savePath;

    private List<ItemReq> resolvedRequirements = new ArrayList<>();
    private Set<ResourceKey<Biome>> detectedBiomes = new HashSet<>();
    private SidebarDisplay sidebarDisplay;

    /** Tous les item IDs qui apparaissent dans au moins un unlockRecipes (tous stades confondus). */
    private Set<String> allLockableItems = new HashSet<>();
    /** Item IDs débloqués par les stades 0..currentStage. */
    private Set<String> unlockedRecipes = new HashSet<>();

    /** Centre de la barrière (calé sur le spawn monde). */
    private double borderCenterX = 0.5;
    private double borderCenterZ = 0.5;

    /** Ticks de célébration restants (200 = 10 s). */
    private int celebrationTicksLeft = 0;

    /** Compteur pour le refresh périodique de la sidebar (toutes les 200 ticks). */
    private int sidebarRefreshCounter = 0;

    /** Compteur pour les particules autour des autels. */
    private int altarParticleCounter = 0;

    /** Gestionnaire d'intégrations cartographiques (BlueMap, Dynmap). Peut être null. */
    private MapIntegrationManager mapManager;

    private static final Random RANDOM = new Random();

    // -----------------------------------------------------------------------

    public BorderQuestManager(MinecraftServer server) {
        this.server = server;
        this.savePath = server.getWorldPath(LevelResource.ROOT).resolve("borderquest_state.json");
        this.state = new QuestState();
        this.sidebarDisplay = new SidebarDisplay(server);
    }

    public void setMapManager(MapIntegrationManager mm) { this.mapManager = mm; }
    public MinecraftServer getServer() { return server; }

    // -----------------------------------------------------------------------
    // Persistance
    // -----------------------------------------------------------------------

    public void load() {
        if (Files.exists(savePath)) {
            try {
                String json = Files.readString(savePath);
                state = GSON.fromJson(json, QuestState.class);
                if (state == null) state = new QuestState();
                if (state.submittedItems == null) state.submittedItems = new HashMap<>();
                if (state.playerDonations == null) state.playerDonations = new HashMap<>();
                if (state.playerNames == null) state.playerNames = new HashMap<>();
                if (state.altarPositions == null) state.altarPositions = new java.util.ArrayList<>();
                if (state.altarNames == null)     state.altarNames     = new java.util.HashMap<>();
                state.currentStage = Math.max(0, Math.min(state.currentStage, STAGES().size() - 1));
                BorderQuest.LOGGER.info("[BorderQuest] Etat charge : stade {}", state.currentStage + 1);
            } catch (IOException e) {
                BorderQuest.LOGGER.error("[BorderQuest] Impossible de charger l'etat", e);
                state = new QuestState();
            }
        } else {
            state = new QuestState();
            BorderQuest.LOGGER.info("[BorderQuest] Nouvel etat cree (stade 1)");
        }
    }

    public void save() {
        try {
            Files.createDirectories(savePath.getParent());
            Files.writeString(savePath, GSON.toJson(state));
        } catch (IOException e) {
            BorderQuest.LOGGER.error("[BorderQuest] Impossible de sauvegarder l'etat", e);
        }
    }

    // -----------------------------------------------------------------------
    // Résolution biome -> requirements
    // -----------------------------------------------------------------------

    public void resolveRequirements() {
        StageDefinition stage = getCurrentStage();
        resolvedRequirements = new ArrayList<>();
        if (stage.requirements != null) resolvedRequirements.addAll(stage.requirements);

        if (stage.categoryRequirements != null && !stage.categoryRequirements.isEmpty()) {
            detectedBiomes = BiomeResourceResolver.scanBiomes(server.overworld(), stage.borderRadius);
            for (CategoryReq catReq : stage.categoryRequirements) {
                ItemReq resolved = BiomeResourceResolver.resolveCategory(
                    catReq.category(), catReq.count(), detectedBiomes);
                resolvedRequirements.add(resolved);
            }
        }
    }

    public List<ItemReq> getResolvedRequirements() { return resolvedRequirements; }

    // -----------------------------------------------------------------------
    // Déblocage de recettes
    // -----------------------------------------------------------------------

    /** Recalcule l'ensemble des recettes débloquées en fonction du stade actuel. */
    public void computeUnlockedRecipes() {
        allLockableItems.clear();
        unlockedRecipes.clear();
        for (int i = 0; i < STAGES().size(); i++) {
            StageDefinition stage = STAGES().get(i);
            if (stage.unlockRecipes != null) {
                allLockableItems.addAll(stage.unlockRecipes);
                if (i <= state.currentStage) {
                    unlockedRecipes.addAll(stage.unlockRecipes);
                }
            }
        }
    }

    /**
     * Vérifie si un item est débloqué pour le craft.
     * Les items qui n'apparaissent dans aucun unlockRecipes sont toujours disponibles.
     */
    public boolean isRecipeUnlocked(String itemId) {
        if (!allLockableItems.contains(itemId)) return true;
        return unlockedRecipes.contains(itemId);
    }

    // -----------------------------------------------------------------------
    // Centre de la barrière
    // -----------------------------------------------------------------------

    /** Lit le spawn monde pour centrer la barrière, avec fallback (0, 0). */
    private void refreshBorderCenter() {
        try {
            BlockPos pos = server.overworld().getRespawnData().pos();
            borderCenterX = pos.getX() + 0.5;
            borderCenterZ = pos.getZ() + 0.5;
            BorderQuest.LOGGER.info("[BorderQuest] Centre barriere -> ({}, {})", (int) borderCenterX, (int) borderCenterZ);
        } catch (Exception e) {
            BorderQuest.LOGGER.warn("[BorderQuest] Impossible de lire le spawn, centre = (0, 0)");
            borderCenterX = 0.5;
            borderCenterZ = 0.5;
        }
    }

    public double getBorderCenterX() { return borderCenterX; }
    public double getBorderCenterZ() { return borderCenterZ; }

    // -----------------------------------------------------------------------
    // Gestion de la barrière
    // -----------------------------------------------------------------------

    public void applyBorder() {
        refreshBorderCenter();
        StageDefinition stage = STAGES().get(state.currentStage);
        double diameter = stage.getDiameter();
        BorderQuestConfig cfg = BorderQuestConfig.get();

        WorldBorder owBorder = server.overworld().getWorldBorder();
        owBorder.setCenter(borderCenterX, borderCenterZ);
        owBorder.setSize(diameter);
        owBorder.setDamagePerBlock(cfg.borderDamagePerBlock);
        owBorder.setWarningBlocks(cfg.borderWarningBlocks);

        var nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            WorldBorder netherBorder = nether.getWorldBorder();
            netherBorder.setCenter(borderCenterX / cfg.netherScale, borderCenterZ / cfg.netherScale);
            netherBorder.setSize(diameter / cfg.netherScale);
            netherBorder.setDamagePerBlock(cfg.borderDamagePerBlock);
            netherBorder.setWarningBlocks(cfg.borderWarningBlocks);
        }

        resolveRequirements();
        computeUnlockedRecipes();
        if (mapManager != null) mapManager.updateBorder(borderCenterX, borderCenterZ, stage.borderRadius);
        BorderQuest.LOGGER.info("[BorderQuest] Barriere appliquee : rayon={} centre=({},{})",
            (int) stage.borderRadius, (int) borderCenterX, (int) borderCenterZ);
    }

    private void animateBorderExpansion(double newDiameter) {
        long now = System.currentTimeMillis();
        double scale = BorderQuestConfig.get().netherScale;
        server.overworld().getWorldBorder()
            .lerpSizeBetween(server.overworld().getWorldBorder().getSize(), newDiameter, 10000L, now);
        var nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            nether.getWorldBorder()
                .lerpSizeBetween(nether.getWorldBorder().getSize(), newDiameter / scale, 10000L, now);
        }
    }

    // -----------------------------------------------------------------------
    // Sidebar
    // -----------------------------------------------------------------------

    public void initSidebar() { sidebarDisplay.init(); }
    public void updateSidebar() { sidebarDisplay.update(this); }

    // -----------------------------------------------------------------------
    // Tick (feux d'artifice)
    // -----------------------------------------------------------------------

    public void tick() {
        // Refresh périodique de la sidebar (toutes les 200 ticks = 10 s)
        sidebarRefreshCounter++;
        if (sidebarRefreshCounter >= 200) {
            sidebarRefreshCounter = 0;
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                updateSidebar();
            }
        }

        // Particules autour des autels
        BorderQuestConfig cfg = BorderQuestConfig.get();
        if (cfg.altarParticlesEnabled && state.altarPositions != null && !state.altarPositions.isEmpty()) {
            altarParticleCounter++;
            if (altarParticleCounter >= cfg.altarParticlePeriodTicks) {
                altarParticleCounter = 0;
                spawnAltarParticles();
            }
        }

        if (celebrationTicksLeft <= 0) return;
        celebrationTicksLeft--;
        // Un burst tous les 10 ticks (0,5 s), soit 20 bursts sur 10 s
        if (celebrationTicksLeft % 10 == 0) {
            spawnFireworkBurst();
        }
    }

    private void spawnAltarParticles() {
        ServerLevel world = server.overworld();
        for (String posKey : state.altarPositions) {
            String[] parts = posKey.split(",");
            if (parts.length != 3) continue;
            try {
                double x = Double.parseDouble(parts[0]) + 0.5;
                double y = Double.parseDouble(parts[1]) + 1.2;
                double z = Double.parseDouble(parts[2]) + 0.5;
                world.sendParticles(ParticleTypes.END_ROD, x, y, z, 3, 0.3, 0.3, 0.3, 0.04);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void spawnFireworkBurst() {
        ServerLevel world = server.overworld();
        double radius = Math.min(getCurrentStage().borderRadius * 0.8, 20);

        for (int i = 0; i < 4; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double dist  = RANDOM.nextDouble() * radius;
            double fx = borderCenterX + Math.cos(angle) * dist;
            double fz = borderCenterZ + Math.sin(angle) * dist;
            int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) fx, (int) fz);
            double fy = topY + 1 + RANDOM.nextDouble() * 3;

            FireworkRocketEntity rocket = new FireworkRocketEntity(
                world, fx, fy, fz, buildFirework());
            world.addFreshEntity(rocket);
        }
    }

    private ItemStack buildFirework() {
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        FireworkExplosion.Shape shape = shapes[RANDOM.nextInt(shapes.length)];

        // Couleurs vives aléatoires
        int[] palette = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFF8800, 0xFFFFFF};
        int color1 = palette[RANDOM.nextInt(palette.length)];
        int color2 = palette[RANDOM.nextInt(palette.length)];
        int fade   = palette[RANDOM.nextInt(palette.length)];

        FireworkExplosion explosion = new FireworkExplosion(
            shape,
            new IntArrayList(new int[]{color1, color2}),
            new IntArrayList(new int[]{fade}),
            RANDOM.nextBoolean(), // trail
            RANDOM.nextBoolean()  // twinkle
        );

        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponents.FIREWORKS,
            new Fireworks(1, List.of(explosion)));
        return stack;
    }

    // -----------------------------------------------------------------------
    // Célébration (titre + son + feux d'artifice)
    // -----------------------------------------------------------------------

    private void celebrateStageComplete(boolean isFinal, StageDefinition newStage) {
        // Titre plein écran
        Component title    = isFinal
            ? ModTranslations.t(TranslationKeys.TITLE_FREEDOM).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            : ModTranslations.t(TranslationKeys.TITLE_EXPANSION).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        Component subtitle = isFinal
            ? ModTranslations.t(TranslationKeys.SUBTITLE_FREEDOM).withStyle(ChatFormatting.YELLOW)
            : ModTranslations.t(TranslationKeys.SUBTITLE_EXPANSION, (int) newStage.borderRadius, newStage.title)
                  .withStyle(ChatFormatting.WHITE);

        server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));
        server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));

        // Son pour chaque joueur (à sa position)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.overworld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                isFinal ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.MASTER,
                2.0f, 1.0f
            );
        }

        // Déclenche les feux d'artifice (durée configurable)
        celebrationTicksLeft = BorderQuestConfig.get().celebrationDurationTicks;
    }

    // -----------------------------------------------------------------------
    // Logique de progression
    // -----------------------------------------------------------------------

    public QuestState getState() { return state; }
    public StageDefinition getCurrentStage() { return STAGES().get(state.currentStage); }
    public boolean isLastStage() { return state.currentStage >= STAGES().size() - 1; }

    public boolean isStageComplete() {
        if (isLastStage()) return true;
        for (ItemReq req : resolvedRequirements) {
            if (state.submittedItems.getOrDefault(req.itemId(), 0) < req.count()) return false;
        }
        return true;
    }

    public Component submitItems(ServerPlayer player) {
        if (isLastStage())
            return ModTranslations.t(TranslationKeys.SUBMIT_ALREADY_DONE).withStyle(ChatFormatting.GOLD);

        if (resolvedRequirements.isEmpty())
            return ModTranslations.t(TranslationKeys.SUBMIT_NO_REQUIREMENTS).withStyle(ChatFormatting.YELLOW);

        boolean submittedAnything = false;
        StringBuilder log = new StringBuilder();
        String playerUuid = player.getStringUUID();
        String playerName = player.getName().getString();
        int totalDonated = 0;

        for (ItemReq req : resolvedRequirements) {
            int remaining = req.count() - state.submittedItems.getOrDefault(req.itemId(), 0);
            if (remaining <= 0) continue;
            Item targetItem = BuiltInRegistries.ITEM.get(Identifier.parse(req.itemId()))
                .map(net.minecraft.core.Holder.Reference::value).orElse(Items.AIR);
            if (targetItem == Items.AIR) continue;
            int toTake = Math.min(remaining, countInInventory(player, targetItem));
            if (toTake <= 0) continue;
            removeFromInventory(player, targetItem, toTake);
            state.submittedItems.merge(req.itemId(), toTake, Integer::sum);
            submittedAnything = true;
            totalDonated += toTake;
            int newTotal = Math.min(state.submittedItems.get(req.itemId()), req.count());
            log.append(String.format("  +%d %s (%d/%d)\n", toTake,
                req.itemId().replace("minecraft:", ""), newTotal, req.count()));
        }

        if (!submittedAnything)
            return ModTranslations.t(TranslationKeys.SUBMIT_NO_RESOURCES).withStyle(ChatFormatting.RED);

        state.playerDonations.merge(playerUuid, totalDonated, Integer::sum);
        state.playerNames.put(playerUuid, playerName);

        // Annonce publique si le don dépasse le seuil configuré
        BorderQuestConfig cfgAnnounce = BorderQuestConfig.get();
        if (cfgAnnounce.donationAnnouncementsEnabled && totalDonated >= cfgAnnounce.donationAnnounceMinItems) {
            server.getPlayerList().broadcastSystemMessage(
                ModTranslations.t(TranslationKeys.BROADCAST_SUBMIT, playerName, totalDonated),
                false
            );
        }

        save();
        updateSidebar();

        if (isStageComplete()) {
            advanceStage();
            return ModTranslations.t(TranslationKeys.SUBMIT_COMPLETE, log.toString().trim())
                .withStyle(ChatFormatting.GREEN);
        }
        return ModTranslations.t(TranslationKeys.SUBMIT_PROGRESS, log.toString().trim())
            .withStyle(ChatFormatting.GREEN);
    }

    private void advanceStage() {
        // Récupérer les récompenses avant d'incrémenter
        List<StageDefinition.Reward> rewards = STAGES().get(state.currentStage).rewards;

        state.currentStage++;
        state.submittedItems.clear();
        save();

        StageDefinition newStage = STAGES().get(state.currentStage);
        boolean isFinal = isLastStage();
        double scale = BorderQuestConfig.get().netherScale;

        server.overworld().getWorldBorder().setCenter(borderCenterX, borderCenterZ);
        animateBorderExpansion(newStage.getDiameter());
        var nether = server.getLevel(Level.NETHER);
        if (nether != null) nether.getWorldBorder().setCenter(borderCenterX / scale, borderCenterZ / scale);

        resolveRequirements();
        computeUnlockedRecipes();
        updateSidebar();

        if (mapManager != null) mapManager.updateBorder(borderCenterX, borderCenterZ, newStage.borderRadius);

        // Annonce chat
        Component announcement = isFinal
            ? ModTranslations.t(TranslationKeys.BROADCAST_FREEDOM).withStyle(ChatFormatting.GOLD)
            : ModTranslations.t(TranslationKeys.BROADCAST_STAGE, state.currentStage,
                (int) newStage.borderRadius, newStage.title)
              .withStyle(ChatFormatting.AQUA);
        server.getPlayerList().broadcastSystemMessage(announcement, false);

        // Récompenses pour tous les joueurs connectés
        if (rewards != null && !rewards.isEmpty()) {
            distributeRewards(rewards);
        }

        // Notification Discord
        String discordMsg = isFinal
            ? "LIBERTÉ ! La barrière est tombée !"
            : "Stade " + state.currentStage + " validé — rayon " + (int) newStage.borderRadius
                + " blocs (" + newStage.title + ")";
        DiscordWebhook.sendAsync(discordMsg);

        // Célébration (titre + son + feux d'artifice)
        celebrateStageComplete(isFinal, newStage);
    }

    private void distributeRewards(List<StageDefinition.Reward> rewards) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (StageDefinition.Reward r : rewards) {
                try {
                    switch (r.type) {
                        case "item" -> {
                            if (!r.itemId.isBlank()) {
                                Item item = BuiltInRegistries.ITEM.get(Identifier.parse(r.itemId))
                                    .map(net.minecraft.core.Holder.Reference::value).orElse(Items.AIR);
                                if (item != Items.AIR) {
                                    ItemStack stack = new ItemStack(item, r.count);
                                    if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                                        player.drop(stack, false, false);
                                    }
                                }
                            }
                        }
                        case "effect" -> {
                            if (!r.effectId.isBlank()) {
                                BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(r.effectId))
                                    .ifPresent(e -> player.addEffect(
                                        new MobEffectInstance(e, r.duration, r.amplifier)));
                            }
                        }
                        case "xp" -> {
                            if (r.amount > 0) player.giveExperiencePoints(r.amount);
                        }
                    }
                } catch (Exception e) {
                    BorderQuest.LOGGER.warn("[BorderQuest] Erreur distribution recompense: {}", e.getMessage());
                }
            }
            player.sendSystemMessage(ModTranslations.t(TranslationKeys.REWARD_DISTRIBUTED));
        }
    }

    // -----------------------------------------------------------------------
    // Affichage /bq status
    // -----------------------------------------------------------------------

    public Component getStatusText() {
        if (isLastStage())
            return ModTranslations.t(TranslationKeys.STATUS_COMPLETE);

        StageDefinition stage = getCurrentStage();
        MutableComponent t = Component.empty();
        t.append(ModTranslations.t(TranslationKeys.STATUS_HEADER, state.currentStage + 1, STAGES().size() - 1));
        t.append(Component.literal("\u00a7b").append(Component.literal(stage.title)).append("\n"));
        t.append(ModTranslations.t(TranslationKeys.STATUS_RADIUS, (int) stage.borderRadius));
        for (ItemReq req : resolvedRequirements) {
            int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
            boolean done = submitted >= req.count();
            String name = req.itemId().replace("minecraft:", "");
            t.append(ModTranslations.t(done ? TranslationKeys.STATUS_ITEM_OK : TranslationKeys.STATUS_ITEM_NOK,
                name, submitted, req.count()));
        }
        t.append(ModTranslations.t(TranslationKeys.STATUS_HINT));
        return t;
    }

    // -----------------------------------------------------------------------
    // Autel
    // -----------------------------------------------------------------------

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public boolean isAltar(BlockPos pos) {
        if (state.altarPositions == null) return false;
        return state.altarPositions.contains(posKey(pos));
    }

    public boolean addAltar(BlockPos pos) {
        return addAltar(pos, "");
    }

    public boolean addAltar(BlockPos pos, String name) {
        if (state.altarPositions == null) state.altarPositions = new java.util.ArrayList<>();
        if (state.altarNames == null)     state.altarNames     = new java.util.HashMap<>();
        String key = posKey(pos);
        if (state.altarPositions.contains(key)) return false;
        state.altarPositions.add(key);
        if (!name.isBlank()) state.altarNames.put(key, name);
        save();
        String displayName = name.isBlank() ? "Autel" : name;
        if (mapManager != null) mapManager.addAltarMarker(pos, displayName);
        return true;
    }

    public void setAltarName(BlockPos pos, String name) {
        if (state.altarNames == null) state.altarNames = new java.util.HashMap<>();
        String key = posKey(pos);
        state.altarNames.put(key, name);
        save();
        if (mapManager != null) mapManager.addAltarMarker(pos, name.isBlank() ? "Autel" : name);
    }

    public String getAltarName(BlockPos pos) {
        if (state.altarNames == null) return "";
        return state.altarNames.getOrDefault(posKey(pos), "");
    }

    public boolean removeAltar(BlockPos pos) {
        if (state.altarPositions == null) return false;
        String key = posKey(pos);
        boolean removed = state.altarPositions.remove(key);
        if (removed) {
            if (state.altarNames != null) state.altarNames.remove(key);
            save();
            if (mapManager != null) mapManager.removeAltarMarker(pos);
        }
        return removed;
    }

    /** Retourne le top N des donateurs triés par total décroissant. */
    public List<Map.Entry<String, Integer>> getTopDonors(int n) {
        return state.playerDonations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    public int getAltarCount() {
        return state.altarPositions == null ? 0 : state.altarPositions.size();
    }

    /**
     * Le joueur pose l'objet en main sur l'autel.
     * Seul l'objet tenu en main principale est pris, uniquement s'il est requis.
     * Retourne un message affiché dans l'action bar.
     */
    public Component donateFromHand(ServerPlayer player) {
        if (isLastStage())
            return ModTranslations.t(TranslationKeys.ALTAR_ALREADY_DOWN).withStyle(ChatFormatting.GOLD);

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty())
            return ModTranslations.t(TranslationKeys.ALTAR_HOLD_ITEM).withStyle(ChatFormatting.RED);

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

        // Chercher si cet item est requis
        ItemReq matching = null;
        for (ItemReq req : resolvedRequirements) {
            if (req.itemId().equals(itemId)) { matching = req; break; }
        }
        if (matching == null)
            return ModTranslations.t(TranslationKeys.ALTAR_NOT_REQUIRED).withStyle(ChatFormatting.RED);

        int alreadySubmitted = state.submittedItems.getOrDefault(itemId, 0);
        int remaining = matching.count() - alreadySubmitted;
        if (remaining <= 0)
            return ModTranslations.t(TranslationKeys.ALTAR_ALREADY_FULL, itemId.replace("minecraft:", "")).withStyle(ChatFormatting.GREEN);

        int toTake = Math.min(remaining, held.getCount());
        held.shrink(toTake);
        state.submittedItems.merge(itemId, toTake, Integer::sum);

        state.playerDonations.merge(player.getStringUUID(), toTake, Integer::sum);
        state.playerNames.put(player.getStringUUID(), player.getName().getString());

        String name = itemId.replace("minecraft:", "");

        // Annonce publique si le don dépasse le seuil configuré
        BorderQuestConfig cfgAlt = BorderQuestConfig.get();
        if (cfgAlt.donationAnnouncementsEnabled && toTake >= cfgAlt.donationAnnounceMinItems) {
            server.getPlayerList().broadcastSystemMessage(
                ModTranslations.t(TranslationKeys.BROADCAST_DONATE, player.getName().getString(), toTake, name),
                false
            );
        }

        save();
        updateSidebar();

        int newTotal = Math.min(state.submittedItems.get(itemId), matching.count());

        if (isStageComplete()) {
            advanceStage();
            return ModTranslations.t(TranslationKeys.ALTAR_COMPLETE, toTake, name, newTotal, matching.count())
                .withStyle(ChatFormatting.GREEN);
        }
        return ModTranslations.t(TranslationKeys.ALTAR_PROGRESS, toTake, name, newTotal, matching.count())
            .withStyle(ChatFormatting.GREEN);
    }

    // -----------------------------------------------------------------------
    // Utilitaires inventaire
    // -----------------------------------------------------------------------

    private int countInInventory(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private void removeFromInventory(ServerPlayer player, Item item, int amount) {
        int toRemove = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                int take = Math.min(stack.getCount(), toRemove);
                stack.shrink(take);
                toRemove -= take;
            }
        }
    }
}
