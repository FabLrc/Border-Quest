package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ModTranslations {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();
    private static Map<String, String> translations = new HashMap<>();
    private static String currentLanguage = "en_us";

    private ModTranslations() {}

    public static void load() {
        load(BorderQuestConfig.get().language);
    }

    private static void load(String language) {
        currentLanguage = language;
        translations = loadLanguage(language);
        BorderQuest.LOGGER.info("[BorderQuest] Langue chargee : {}", currentLanguage);
    }

    private static Map<String, String> loadLanguage(String language) {
        Map<String, String> map = loadFromResources(language);
        if (!map.isEmpty()) return map;
        if (!language.equals("en_us")) {
            BorderQuest.LOGGER.warn("[BorderQuest] Fichier de langue {} introuvable, fallback en_us", language);
            map = loadFromResources("en_us");
            if (!map.isEmpty()) return map;
        }
        BorderQuest.LOGGER.warn("[BorderQuest] Aucun fichier de langue trouve");
        return new HashMap<>();
    }

    private static Map<String, String> loadFromResources(String language) {
        String path = "assets/borderquest/lang/" + language + ".json";
        try (InputStream is = ModTranslations.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return new HashMap<>();
            return GSON.fromJson(new InputStreamReader(is), MAP_TYPE);
        } catch (Exception e) {
            BorderQuest.LOGGER.warn("[BorderQuest] Erreur chargement {}: {}", path, e.getMessage());
            return new HashMap<>();
        }
    }

    public static MutableComponent t(String key, Object... args) {
        String template = translations.getOrDefault(key, key);
        return Component.literal(String.format(template, args));
    }
}
