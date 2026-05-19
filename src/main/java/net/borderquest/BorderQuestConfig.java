package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static net.borderquest.StageDefinition.CategoryReq;
import static net.borderquest.StageDefinition.ItemReq;
import static net.borderquest.StageDefinition.XpReq;

/**
 * Configuration du mod chargée depuis config/borderquest.json.
 * Éditez ce fichier pour personnaliser les stades, les rayons et les récompenses.
 * Relancez le serveur ou tapez /bq reload pour appliquer les changements.
 */
public class BorderQuestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static BorderQuestConfig instance;

    // -----------------------------------------------------------------------
    // Paramètres généraux
    // -----------------------------------------------------------------------

    /** Langue du mod (fr_fr, en_us, etc.). */
    public String language = "en_us";

    /** Durée des feux d'artifice de célébration en ticks (20 ticks = 1 seconde). */
    public int celebrationDurationTicks = 200;

    /** Dégâts infligés par seconde lorsque le joueur est hors de la barrière. */
    public double borderDamagePerBlock = 0.2;

    /** Distance d'avertissement avant le mur (en blocs). */
    public int borderWarningBlocks = 5;

    /**
     * Diviseur de la barrière pour le Nether (coordonnées Nether = Overworld / 8).
     * Changez cette valeur si votre monde Nether utilise une échelle différente.
     */
    public double netherScale = 8.0;

    public int borderExpansionDurationSeconds = 10;

    // -----------------------------------------------------------------------
    // Particules autels
    // -----------------------------------------------------------------------

    /** Active les particules autour des blocs d'autel. */
    public boolean altarParticlesEnabled = true;

    /** Fréquence des particules (en ticks). 20 ticks = 1 seconde. */
    public int altarParticlePeriodTicks = 20;

    // -----------------------------------------------------------------------
    // Annonces de don dans le chat
    // -----------------------------------------------------------------------

    /** Active les annonces globales quand un joueur fait un don significatif. */
    public boolean donationAnnouncementsEnabled = true;

    /** Nombre minimum d'objets donnés pour déclencher une annonce publique. */
    public int donationAnnounceMinItems = 16;

    // -----------------------------------------------------------------------
    // Verrous de dimension
    // -----------------------------------------------------------------------

    /**
     * Liste des mondes verrouillés jusqu'à un certain stade.
     * worldId : identifiant du monde (ex: "minecraft:the_nether")
     * requiredStage : stade minimum (1-based) pour y accéder
     */
    public List<WorldLock> worldLocks = defaultWorldLocks();

    public static class WorldLock {
        public String worldId      = "minecraft:the_nether";
        public int    requiredStage = 5;
        public WorldLock() {}
        public WorldLock(String worldId, int requiredStage) {
            this.worldId       = worldId;
            this.requiredStage = requiredStage;
        }
    }

    // -----------------------------------------------------------------------
    // Webhook Discord
    // -----------------------------------------------------------------------

    /** Active l'envoi de messages Discord lors d'un changement de stade. */
    public boolean discordEnabled = false;

    /** URL du webhook Discord (format : https://discord.com/api/webhooks/...). */
    public String discordWebhookUrl = "";

    /** Nom affiché par le webhook dans Discord. */
    public String discordUsername = "Border Quest";

    /** URL de l'avatar du bot Discord (optionnel, laisser vide pour l'icône par défaut). */
    public String discordAvatarUrl = "";

    // -----------------------------------------------------------------------
    // Dashboard Web
    // -----------------------------------------------------------------------

    public DashboardConfig dashboard = new DashboardConfig();

    public static class DashboardConfig {
        public boolean enabled = true;
        public int port = 8123;
        public String bindAddress = "127.0.0.1";
        public String password = null;

        public static String generatePassword() {
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
            SecureRandom random = new SecureRandom();
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++)
                sb.append(chars.charAt(random.nextInt(chars.length())));
            return sb.toString();
        }
    }

    // -----------------------------------------------------------------------
    // Stades de progression
    // -----------------------------------------------------------------------

    /**
     * Liste des stades dans l'ordre. Le dernier stade représente la liberté totale.
     * Chaque stade comporte :
     *   - borderRadius   : rayon de la zone en blocs (diamètre = radius × 2)
     *   - title          : nom du stade affiché aux joueurs
     *   - requirements   : objets fixes à déposer (itemId + count)
     *   - categoryRequirements : catégories résolues selon le biome (ex. "logs")
     */
    public List<StageDefinition> stages = defaultStages();

    // -----------------------------------------------------------------------
    // Accès singleton
    // -----------------------------------------------------------------------

    public static BorderQuestConfig get() {
        if (instance == null) load();
        return instance;
    }

    // -----------------------------------------------------------------------
    // Chargement / sauvegarde
    // -----------------------------------------------------------------------

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                instance = GSON.fromJson(json, BorderQuestConfig.class);
                if (instance == null) instance = new BorderQuestConfig();
                boolean needsSave = instance.validate();
                if (needsSave) save();
                BorderQuest.LOGGER.info("[BorderQuest] Config chargee ({} stades)", instance.stages.size());
            } catch (IOException e) {
                BorderQuest.LOGGER.error("[BorderQuest] Impossible de charger la config, valeurs par defaut utilisees", e);
                instance = new BorderQuestConfig();
            }
        } else {
            instance = new BorderQuestConfig();
            save();
            BorderQuest.LOGGER.info("[BorderQuest] Config par defaut creee : {}", path);
        }
    }

    public static boolean hasField(String fieldName) {
        try {
            Path path = configPath();
            if (!Files.exists(path)) return false;
            return Files.readString(path).contains("\"" + fieldName + "\"");
        } catch (IOException e) {
            return false;
        }
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            BorderQuest.LOGGER.error("[BorderQuest] Impossible de sauvegarder la config", e);
        }
    }

    /** Applique les valeurs par défaut aux champs manquants ou invalides. Retourne true si un champ a été modifié. */
    private boolean validate() {
        boolean modified = false;
        if (language == null || language.isBlank()) { language = "en_us"; modified = true; }
        if (dashboard == null) { dashboard = new DashboardConfig(); modified = true; }
        if (dashboard.password == null || dashboard.password.isBlank()) { dashboard.password = DashboardConfig.generatePassword(); modified = true; }
        if (dashboard.port <= 0 || dashboard.port > 65535) { dashboard.port = 8123; modified = true; }
        if (dashboard.bindAddress == null || dashboard.bindAddress.isBlank()) { dashboard.bindAddress = "127.0.0.1"; modified = true; }
        if (stages == null || stages.isEmpty()) { stages = defaultStages(); modified = true; }
        if (celebrationDurationTicks <= 0) { celebrationDurationTicks = 200; modified = true; }
        if (borderDamagePerBlock < 0) { borderDamagePerBlock = 0.2; modified = true; }
        if (borderWarningBlocks < 0) { borderWarningBlocks = 5; modified = true; }
        if (netherScale <= 0) { netherScale = 8.0; modified = true; }
        if (altarParticlePeriodTicks <= 0) { altarParticlePeriodTicks = 20; modified = true; }
        if (donationAnnounceMinItems <= 0) { donationAnnounceMinItems = 1; modified = true; }
        if (worldLocks == null) { worldLocks = defaultWorldLocks(); modified = true; }
        if (discordWebhookUrl == null) { discordWebhookUrl = ""; modified = true; }
        if (discordUsername == null || discordUsername.isBlank()) { discordUsername = "Border Quest"; modified = true; }
        if (discordAvatarUrl == null) { discordAvatarUrl = ""; modified = true; }
        if (borderExpansionDurationSeconds <= 0) { borderExpansionDurationSeconds = 10; modified = true; }
        for (StageDefinition s : stages) {
            if (s.requirements == null) { s.requirements = List.of(); modified = true; }
            if (s.categoryRequirements == null) { s.categoryRequirements = List.of(); modified = true; }
            if (s.xpRequirements == null) { s.xpRequirements = List.of(); modified = true; }
            if (s.rewards == null) { s.rewards = new ArrayList<>(); modified = true; }
            if (s.unlockRecipes == null) { s.unlockRecipes = new ArrayList<>(); modified = true; }
        }
        return modified;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("borderquest.json");
    }

    public String getRawJson() {
        return GSON.toJson(this);
    }

    public boolean writeRawJson(String json) {
        try {
            GSON.fromJson(json, BorderQuestConfig.class);
            Files.writeString(configPath(), json);
            return true;
        } catch (Exception e) {
            BorderQuest.LOGGER.error("[BorderQuest] Invalid config JSON: {}", e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Stades par défaut
    // -----------------------------------------------------------------------

    private static List<WorldLock> defaultWorldLocks() {
        return new ArrayList<>(List.of(
            new WorldLock("minecraft:the_nether", 5),
            new WorldLock("minecraft:the_end",    7)
        ));
    }

    private static List<StageDefinition> defaultStages() {
        return List.of(
            new StageDefinition(10, "Defricher la zone",
                List.of(new ItemReq("minecraft:cobblestone", 64)),
                List.of(new CategoryReq("logs", 64)),
                List.of(new XpReq(100))),

            new StageDefinition(25, "Premiers pas vers la civilisation",
                List.of(
                    new ItemReq("minecraft:iron_ingot", 32),
                    new ItemReq("minecraft:bread", 32)
                )),

            new StageDefinition(50, "Expansion miniere",
                List.of(
                    new ItemReq("minecraft:iron_ingot", 64),
                    new ItemReq("minecraft:gold_ingot", 16)
                )),

            new StageDefinition(100, "Maitrise des metaux",
                List.of(
                    new ItemReq("minecraft:diamond", 8),
                    new ItemReq("minecraft:iron_ingot", 32)
                )),

            new StageDefinition(200, "Vers le Nether",
                List.of(
                    new ItemReq("minecraft:obsidian", 10),
                    new ItemReq("minecraft:diamond", 4)
                )),

            new StageDefinition(400, "Conquete du Nether",
                List.of(
                    new ItemReq("minecraft:blaze_rod", 16),
                    new ItemReq("minecraft:ender_pearl", 8)
                )),

            new StageDefinition(800, "Invocation du Wither",
                List.of(new ItemReq("minecraft:nether_star", 1))),

            new StageDefinition(29999984, "LIBERTE ! La barriere est tombee !",
                List.of())
        );
    }
}
