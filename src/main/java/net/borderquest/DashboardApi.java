package net.borderquest;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.borderquest.StageDefinition.ItemReq;

public class DashboardApi {

    private static final Gson GSON = new Gson();

    public static String statusJson(BorderQuestManager mgr, MinecraftServer server) {
        return runOnServer(server, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            QuestState state = mgr.getState();

            data.put("stage", state.currentStage + 1);
            data.put("stageZeroBased", state.currentStage);
            data.put("totalStages", BorderQuestManager.STAGES().size() - 1);
            data.put("isLastStage", mgr.isLastStage());

            StageDefinition stage = mgr.getCurrentStage();
            data.put("title", stage.title);
            data.put("radius", (int) stage.borderRadius);

            if (!mgr.isLastStage()) {
                StageDefinition next = BorderQuestManager.STAGES().get(state.currentStage + 1);
                data.put("nextRadius", (int) next.borderRadius);
            }

            data.put("centerX", mgr.getBorderCenterX());
            data.put("centerZ", mgr.getBorderCenterZ());

            PlayerList players = server.getPlayerList();
            data.put("playersOnline", players.getPlayerCount());

            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemReq req : mgr.getResolvedRequirements()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", req.itemId());
                item.put("name", req.itemId().replace("minecraft:", ""));
                int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
                item.put("submitted", submitted);
                item.put("required", req.count());
                item.put("done", submitted >= req.count());
                items.add(item);
            }
            data.put("resources", items);
            return GSON.toJson(data);
        });
    }

    public static String leaderboardJson(BorderQuestManager mgr, MinecraftServer server) {
        return runOnServer(server, () -> {
            List<Map.Entry<String, Integer>> top = mgr.getTopDonors(10);
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> donors = new ArrayList<>();
            for (int i = 0; i < top.size(); i++) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("rank", i + 1);
                entry.put("name", mgr.getState().playerNames.getOrDefault(top.get(i).getKey(), "???"));
                entry.put("count", top.get(i).getValue());
                donors.add(entry);
            }
            data.put("donors", donors);
            return GSON.toJson(data);
        });
    }

    public static String altarsJson(BorderQuestManager mgr, MinecraftServer server) {
        return runOnServer(server, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            QuestState state = mgr.getState();
            List<Map<String, Object>> altars = new ArrayList<>();
            if (state.altarPositions != null) {
                for (String posKey : state.altarPositions) {
                    Map<String, Object> altar = new LinkedHashMap<>();
                    String[] parts = posKey.split(",");
                    if (parts.length == 3) {
                        altar.put("x", parts[0]);
                        altar.put("y", parts[1]);
                        altar.put("z", parts[2]);
                    }
                    altar.put("name", state.altarNames != null ? state.altarNames.getOrDefault(posKey, "") : "");
                    altars.add(altar);
                }
            }
            data.put("count", altars.size());
            data.put("altars", altars);
            return GSON.toJson(data);
        });
    }

    public static String stagesJson(BorderQuestManager mgr, MinecraftServer server) {
        return runOnServer(server, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            List<Map<String, Object>> stages = new ArrayList<>();
            int i = 0;
            for (StageDefinition stage : BorderQuestManager.STAGES()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("index", i + 1);
                s.put("title", stage.title);
                s.put("borderRadius", (int) stage.borderRadius);
                List<Map<String, Object>> reqs = new ArrayList<>();
                if (stage.requirements != null) {
                    for (ItemReq req : stage.requirements) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", req.itemId());
                        r.put("name", req.itemId().replace("minecraft:", ""));
                        r.put("count", req.count());
                        reqs.add(r);
                    }
                }
                s.put("requirements", reqs);
                s.put("rewards", stage.rewards != null ? stage.rewards : List.of());
                s.put("unlockRecipes", stage.unlockRecipes != null ? stage.unlockRecipes : List.of());
                stages.add(s);
                i++;
            }
            data.put("stages", stages);
            return GSON.toJson(data);
        });
    }

    public static String adminConfigJson(BorderQuestManager mgr, MinecraftServer server) {
        return runOnServer(server, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("config", BorderQuestConfig.get().getRawJson());
            return GSON.toJson(data);
        });
    }

    public static String executeCommand(BorderQuestManager mgr, MinecraftServer server, String action) {
        return runOnServer(server, () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            try {
                String fullCommand = switch (action) {
                    case "reload" -> {
                        BorderQuestConfig.load();
                        ModTranslations.load();
                        mgr.load();
                        mgr.applyBorder();
                        mgr.updateSidebar();
                        yield "bq reload";
                    }
                    case "skip" -> {
                        QuestState state = mgr.getState();
                        state.currentStage++;
                        state.submittedItems.clear();
                        mgr.save();
                        mgr.applyBorder();
                        mgr.updateSidebar();
                        yield "bq skip";
                    }
                    case "reset" -> {
                        mgr.getState().reset();
                        mgr.save();
                        mgr.applyBorder();
                        mgr.updateSidebar();
                        yield "bq reset";
                    }
                    default -> null;
                };
                if (fullCommand == null) {
                    result.put("success", false);
                    result.put("message", "Unknown action: " + action);
                } else {
                    result.put("success", true);
                    result.put("message", "Command executed: " + fullCommand);
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
            }
            return GSON.toJson(result);
        });
    }

    public static String updateConfig(BorderQuestManager mgr, MinecraftServer server, String newConfigJson) {
        return runOnServer(server, () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            try {
                var cfg = BorderQuestConfig.get();
                if (cfg.writeRawJson(newConfigJson)) {
                    BorderQuestConfig.load();
                    ModTranslations.load();
                    mgr.load();
                    mgr.applyBorder();
                    mgr.updateSidebar();
                    result.put("success", true);
                    result.put("message", "Config updated and reloaded");
                } else {
                    result.put("success", false);
                    result.put("message", "Invalid JSON config");
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
            }
            return GSON.toJson(result);
        });
    }

    private static String runOnServer(MinecraftServer server, java.util.function.Supplier<String> task) {
        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return "{\"success\":false,\"message\":\"Server thread busy\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
