package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DashboardServer {

    private static HttpServer server;
    private static MinecraftServer minecraftServer;
    private static BorderQuestManager manager;
    private static final Gson GSON = new Gson();

    public static void start(MinecraftServer ms, BorderQuestManager mgr) {
        var cfg = BorderQuestConfig.get().dashboard;
        if (!cfg.enabled) {
            BorderQuest.LOGGER.info("[BorderQuest] Dashboard desactive");
            return;
        }

        try {
            minecraftServer = ms;
            manager = mgr;
            server = HttpServer.create(new InetSocketAddress(cfg.bindAddress, cfg.port), 0);

            server.createContext("/", DashboardServer::handleRoot);
            server.createContext("/api/status", DashboardServer::handleStatus);
            server.createContext("/api/leaderboard", DashboardServer::handleLeaderboard);
            server.createContext("/api/altars", DashboardServer::handleAltars);
            server.createContext("/api/stages", DashboardServer::handleStages);
            server.createContext("/api/admin/auth", DashboardServer::handleAuth);
            server.createContext("/api/admin/command", DashboardServer::handleCommand);
            server.createContext("/api/admin/config", DashboardServer::handleConfig);

            server.setExecutor(null);
            server.start();

            BorderQuest.LOGGER.info("[BorderQuest] Dashboard demarre sur http://{}:{}", cfg.bindAddress, cfg.port);
            BorderQuest.LOGGER.info("[BorderQuest] Dashboard admin password: {}", cfg.password);
        } catch (Exception e) {
            BorderQuest.LOGGER.error("[BorderQuest] Impossible de demarrer le dashboard: {}", e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            BorderQuest.LOGGER.info("[BorderQuest] Dashboard arrete");
        }
    }

    // -----------------------------------------------------------------------
    // Handlers
    // -----------------------------------------------------------------------

    private static void handleRoot(HttpExchange ex) {
        try (InputStream is = DashboardServer.class.getClassLoader()
                .getResourceAsStream("assets/borderquest/dashboard.html")) {
            if (is == null) {
                respond(ex, 500, "text/plain", "Dashboard HTML not found");
                return;
            }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            respond(ex, 200, "text/html; charset=utf-8", html);
        } catch (IOException e) {
            respond(ex, 500, "text/plain", "Internal error");
        }
    }

    private static void handleStatus(HttpExchange ex) {
        if (manager == null || minecraftServer == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }
        respond(ex, 200, "application/json", DashboardApi.statusJson(manager, minecraftServer));
    }

    private static void handleLeaderboard(HttpExchange ex) {
        if (manager == null || minecraftServer == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }
        respond(ex, 200, "application/json", DashboardApi.leaderboardJson(manager, minecraftServer));
    }

    private static void handleAltars(HttpExchange ex) {
        if (manager == null || minecraftServer == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }
        respond(ex, 200, "application/json", DashboardApi.altarsJson(manager, minecraftServer));
    }

    private static void handleStages(HttpExchange ex) {
        if (manager == null || minecraftServer == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }
        respond(ex, 200, "application/json", DashboardApi.stagesJson(manager, minecraftServer));
    }

    private static void handleAuth(HttpExchange ex) {
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, "text/plain", ""); return; }
        String json = readBody(ex);
        try {
            JsonObject body = JsonParser.parseString(json).getAsJsonObject();
            String password = body.get("password").getAsString();
            String cfgPwd = BorderQuestConfig.get().dashboard.password;
            boolean ok = cfgPwd != null && cfgPwd.equals(password);
            respond(ex, ok ? 200 : 401, "application/json",
                "{\"success\":" + ok + "}");
        } catch (Exception e) {
            respond(ex, 400, "application/json", "{\"success\":false}");
        }
    }

    private static void handleCommand(HttpExchange ex) {
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, "text/plain", ""); return; }
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "application/json", "{\"success\":false,\"message\":\"POST required\"}");
            return;
        }
        if (manager == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }
        String json = readBody(ex);
        try {
            JsonObject body = JsonParser.parseString(json).getAsJsonObject();
            if (!checkPassword(body)) {
                respond(ex, 401, "application/json", "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            String action = body.get("action").getAsString();
            respond(ex, 200, "application/json",
                DashboardApi.executeCommand(manager, minecraftServer, action));
        } catch (Exception e) {
            respond(ex, 400, "application/json", "{\"success\":false,\"message\":\"Invalid request\"}");
        }
    }

    private static void handleConfig(HttpExchange ex) {
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, "text/plain", ""); return; }
        if (manager == null) {
            respond(ex, 503, "application/json", "{\"success\":false,\"message\":\"Server not ready\"}");
            return;
        }

        if ("GET".equals(ex.getRequestMethod())) {
            Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
            if (!checkPasswordParam(params)) {
                respond(ex, 401, "application/json", "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }
            respond(ex, 200, "application/json",
                DashboardApi.adminConfigJson(manager, minecraftServer));
            return;
        }

        if ("POST".equals(ex.getRequestMethod())) {
            String json = readBody(ex);
            try {
                JsonObject body = JsonParser.parseString(json).getAsJsonObject();
                if (!checkPassword(body)) {
                    respond(ex, 401, "application/json", "{\"success\":false,\"message\":\"Unauthorized\"}");
                    return;
                }
                String newConfig = body.get("config").getAsString();
                respond(ex, 200, "application/json",
                    DashboardApi.updateConfig(manager, minecraftServer, newConfig));
            } catch (Exception e) {
                respond(ex, 400, "application/json", "{\"success\":false,\"message\":\"Invalid request\"}");
            }
            return;
        }

        respond(ex, 405, "application/json", "{\"success\":false,\"message\":\"GET or POST required\"}");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static boolean checkPassword(JsonObject body) {
        try {
            String password = body.get("password").getAsString();
            String cfgPwd = BorderQuestConfig.get().dashboard.password;
            return cfgPwd != null && cfgPwd.equals(password);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkPasswordParam(Map<String, String> params) {
        String pwd = params.get("password");
        if (pwd == null) return false;
        String cfgPwd = BorderQuestConfig.get().dashboard.password;
        return cfgPwd != null && cfgPwd.equals(pwd);
    }

    private static void respond(HttpExchange ex, int code, String contentType, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            ex.sendResponseHeaders(code, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        } catch (IOException ignored) {}
    }

    private static String readBody(HttpExchange ex) {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                try {
                    params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return params;
    }
}
