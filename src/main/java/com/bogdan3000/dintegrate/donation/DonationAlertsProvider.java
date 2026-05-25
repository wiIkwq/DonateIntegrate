package com.bogdan3000.dintegrate.donation;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DonationAlerts Centrifugo client.
 * Requires an OAuth access token with subscribe scopes for selected channels.
 */
public class DonationAlertsProvider implements DonationProvider, WebSocket.Listener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SOURCE = "donationalerts";

    private final String accessToken;
    private final int configuredUserId;
    private final String apiUrl;
    private final String socketUrl;
    private final List<String> channelTypes;
    private final Consumer<DonationEvent> eventHandler;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("DonationAlertsScheduler");
                return t;
            });

    private WebSocket socket;
    private ScheduledFuture<?> pingTask;
    private String socketConnectionToken;
    private String clientId;
    private int userId;
    private int msgCounter = 1;
    private int expectedSubscriptions = 0;
    private int confirmedSubscriptions = 0;

    private volatile boolean connecting = false;
    private volatile boolean connected = false;
    private volatile boolean subscribed = false;
    private volatile String statusText = "Disconnected";
    private volatile String lastEventText = "No events yet";

    private long lastConnectAttempt = 0L;
    private static final long COOLDOWN_MS = 8000;

    public DonationAlertsProvider(
            String accessToken,
            int userId,
            String apiUrl,
            String socketUrl,
            List<String> channels,
            Consumer<DonationEvent> eventHandler
    ) {
        this.accessToken = accessToken;
        this.configuredUserId = userId;
        this.apiUrl = trimTrailingSlash(apiUrl);
        this.socketUrl = socketUrl;
        this.channelTypes = normalizeChannels(channels);
        this.eventHandler = eventHandler;
    }

    @Override
    public synchronized void connect() {
        long now = System.currentTimeMillis();
        if (now - lastConnectAttempt < COOLDOWN_MS) {
            LOGGER.warn("[DIntegrate] DonationAlerts connect blocked by cooldown ({} ms left)", COOLDOWN_MS - (now - lastConnectAttempt));
            statusText = "Cooldown";
            return;
        }
        lastConnectAttempt = now;

        if (connected || connecting) {
            LOGGER.warn("[DIntegrate] DonationAlerts connection already active.");
            statusText = subscribed ? "Subscribed" : "Connecting";
            return;
        }

        if (accessToken == null || accessToken.isBlank() || accessToken.startsWith("YOUR_")) {
            LOGGER.error("[DIntegrate] DonationAlerts token is missing.");
            statusText = "Missing token";
            return;
        }

        connecting = true;
        subscribed = false;
        confirmedSubscriptions = 0;
        statusText = "Loading /user/oauth";

        loadUserInfo().thenAccept(info -> {
            if (info == null || info.socketConnectionToken == null || info.socketConnectionToken.isBlank()) {
                LOGGER.error("[DIntegrate] DonationAlerts failed to load user/oauth. Check token and scopes.");
                connecting = false;
                statusText = "Token check failed";
                return;
            }

            this.userId = configuredUserId > 0 ? configuredUserId : info.userId;
            this.socketConnectionToken = info.socketConnectionToken;

            if (this.userId <= 0) {
                LOGGER.error("[DIntegrate] DonationAlerts user_id is unknown.");
                connecting = false;
                statusText = "Unknown user id";
                return;
            }

            statusText = "Opening WebSocket";
            LOGGER.info("[DIntegrate] DonationAlerts connecting to WebSocket for user {}", this.userId);
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(socketUrl), this)
                    .thenAccept(ws -> {
                        this.socket = ws;
                        startPing(ws);
                        scheduler.schedule(() -> sendHandshake(ws), 500, TimeUnit.MILLISECONDS);
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("[DIntegrate] DonationAlerts WebSocket failed: {}", ex.getMessage());
                        connecting = false;
                        statusText = "WebSocket failed";
                        return null;
                    });
        });
    }

    private CompletableFuture<UserInfo> loadUserInfo() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/user/oauth"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        LOGGER.error("[DIntegrate] DonationAlerts user/oauth HTTP {}: {}", resp.statusCode(), truncate(resp.body()));
                        statusText = "HTTP " + resp.statusCode() + " /user/oauth";
                        return null;
                    }

                    try {
                        JsonObject data = parseObject(resp.body()).getAsJsonObject("data");
                        int id = getInt(data, "id", configuredUserId);
                        String token = getString(data, "socket_connection_token", "");
                        return new UserInfo(id, token);
                    } catch (Exception e) {
                        LOGGER.error("[DIntegrate] DonationAlerts user/oauth parse error. Body: {}", truncate(resp.body()), e);
                        statusText = "Bad /user/oauth response";
                        return null;
                    }
                });
    }

    private CompletableFuture<Map<String, String>> getSubscriptionTokens(String clientId) {
        JsonArray channels = new JsonArray();
        for (String type : channelTypes) {
            channels.add(channelName(type));
        }

        JsonObject body = new JsonObject();
        body.add("channels", channels);
        body.addProperty("client", clientId);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/centrifuge/subscribe"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        LOGGER.error("[DIntegrate] DonationAlerts subscribe HTTP {}: {}", resp.statusCode(), truncate(resp.body()));
                        statusText = "HTTP " + resp.statusCode() + " subscribe";
                        return Map.of();
                    }

                    try {
                        Map<String, String> tokens = parseSubscriptionTokens(resp.body());
                        if (tokens.isEmpty()) {
                            LOGGER.error("[DIntegrate] DonationAlerts subscribe response has no channel tokens: {}", truncate(resp.body()));
                            statusText = "No subscribe tokens";
                        }
                        return tokens;
                    } catch (Exception e) {
                        LOGGER.error("[DIntegrate] DonationAlerts subscribe parse error. Body: {}", truncate(resp.body()), e);
                        statusText = "Bad subscribe response";
                        return Map.of();
                    }
                });
    }

    private void sendHandshake(WebSocket ws) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("id", 1);
            JsonObject params = new JsonObject();
            params.addProperty("token", socketConnectionToken);
            root.add("params", params);
            ws.sendText(root.toString(), true);
            statusText = "Handshake sent";
            LOGGER.info("[DIntegrate] DonationAlerts sent handshake.");
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] DonationAlerts handshake error", e);
            statusText = "Handshake failed";
        }
    }

    private void sendSubscribe(WebSocket ws, String channel, String token) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("method", 1);
            root.addProperty("id", ++msgCounter);
            JsonObject params = new JsonObject();
            params.addProperty("channel", channel);
            params.addProperty("token", token);
            root.add("params", params);
            ws.sendText(root.toString(), true);
            statusText = "Subscribing";
            LOGGER.info("[DIntegrate] DonationAlerts subscribing to {}", channel);
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] DonationAlerts subscribe send error", e);
            statusText = "Subscribe send failed";
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        connected = true;
        connecting = false;
        statusText = "WebSocket opened";
        LOGGER.info("[DIntegrate] DonationAlerts WebSocket opened.");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
        try {
            JsonObject json = JsonParser.parseString(message.toString()).getAsJsonObject();

            if (json.has("id") && json.get("id").getAsInt() == 1 && json.has("result")) {
                clientId = json.getAsJsonObject("result").get("client").getAsString();
                getSubscriptionTokens(clientId).thenAccept(tokens -> {
                    expectedSubscriptions = tokens.size();
                    if (expectedSubscriptions == 0) {
                        LOGGER.error("[DIntegrate] DonationAlerts returned no subscription tokens.");
                        connected = false;
                        subscribed = false;
                        connecting = false;
                        statusText = "Subscribe failed";
                        return;
                    }
                    tokens.forEach((channel, token) -> sendSubscribe(webSocket, channel, token));
                });
            } else if (json.has("result")) {
                JsonObject result = json.getAsJsonObject("result");
                if (isSubscriptionConfirmation(result)) {
                    confirmedSubscriptions++;
                    subscribed = confirmedSubscriptions >= expectedSubscriptions;
                    statusText = subscribed ? "Subscribed" : "Subscribed " + confirmedSubscriptions + "/" + expectedSubscriptions;
                    LOGGER.info("[DIntegrate] DonationAlerts subscription confirmed ({}/{}).", confirmedSubscriptions, expectedSubscriptions);
                } else if (result.has("data")) {
                    handlePublication(result);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] DonationAlerts WS parse error", e);
        }

        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
        connected = false;
        subscribed = false;
        connecting = false;
        statusText = "Closed " + code;
        stopPing();
        socket = null;
        LOGGER.warn("[DIntegrate] DonationAlerts WebSocket closed ({}): {}", code, reason);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        subscribed = false;
        connecting = false;
        statusText = "WebSocket error";
        stopPing();
        LOGGER.error("[DIntegrate] DonationAlerts WebSocket error", error);
    }

    @Override
    public boolean isConnected() {
        return connected && subscribed && socket != null && !socket.isOutputClosed();
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastEventText() {
        return lastEventText;
    }

    public int getResolvedUserId() {
        return userId;
    }

    @Override
    public void onDonation(Consumer<DonationEvent> handler) { }

    @Override
    public synchronized void disconnect() {
        if (!connected && !connecting) {
            LOGGER.warn("[DIntegrate] DonationAlerts already disconnected.");
            return;
        }

        connected = false;
        subscribed = false;
        connecting = false;
        statusText = "Disconnected";
        stopPing();

        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "Manual disconnect");
            } catch (Exception e) {
                LOGGER.warn("[DIntegrate] DonationAlerts socket close error: {}", e.getMessage());
            } finally {
                socket = null;
            }
        }
    }

    private void handlePublication(JsonObject result) {
        JsonObject payload = result.getAsJsonObject("data");
        String channel = getString(result, "channel", "");
        JsonObject resource = unwrapResource(payload);

        if (channel.startsWith("$alerts:")) {
            String eventType = getString(resource, "name", "donation");
            if (eventType.isBlank() || "alert".equalsIgnoreCase(eventType) || eventType.equalsIgnoreCase("donations")) {
                eventType = "donation";
            }
            handleDonation(resource, eventType);
            return;
        }

        if (channel.startsWith("$goals:")) {
            handleGoal(resource);
        } else if (channel.startsWith("$polls:")) {
            handlePoll(resource);
        } else {
            LOGGER.info("[DIntegrate] DonationAlerts ignored event from channel {}", channel);
        }
    }

    private JsonObject unwrapResource(JsonObject payload) {
        JsonObject current = payload;
        for (int i = 0; i < 3; i++) {
            if (current.has("data") && current.get("data").isJsonObject()) {
                current = current.getAsJsonObject("data");
            } else if (current.has("notification") && current.get("notification").isJsonObject()) {
                current = current.getAsJsonObject("notification");
            } else if (current.has("vars") && current.get("vars").isJsonObject()) {
                current = current.getAsJsonObject("vars");
            } else {
                break;
            }
        }
        return current;
    }

    private boolean isSubscriptionConfirmation(JsonObject result) {
        return result.has("type")
                && getInt(result, "type", 0) == 1
                && result.has("channel")
                && result.has("data")
                && result.getAsJsonObject("data").has("info");
    }

    private void handleDonation(JsonObject data, String eventType) {
        String username = getString(data, "username", "Unknown");
        String message = getString(data, "message", "");
        String currency = getString(data, "currency", "");
        double amount = getDouble(data, "amount", 0.0);
        int id = getInt(data, "id", -1);
        lastEventText = username + " | " + formatAmount(amount) + (currency.isBlank() ? "" : " " + currency);
        eventHandler.accept(new DonationEvent(SOURCE, eventType, username, amount, currency, message, id));
    }

    private void handleGoal(JsonObject data) {
        String title = getString(data, "title", "Goal");
        String currency = getString(data, "currency", "");
        double raised = getDouble(data, "raised_amount", 0.0);
        int id = getInt(data, "id", -1);
        lastEventText = "Goal: " + title + " | " + formatAmount(raised) + (currency.isBlank() ? "" : " " + currency);
        eventHandler.accept(new DonationEvent(SOURCE, "goal", title, raised, currency, title, id));
    }

    private void handlePoll(JsonObject data) {
        String title = getString(data, "title", "Poll");
        int id = getInt(data, "id", -1);
        lastEventText = "Poll: " + title;
        eventHandler.accept(new DonationEvent(SOURCE, "poll", title, 0.0, "", title, id));
    }

    private void startPing(WebSocket ws) {
        stopPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (ws != null && !ws.isOutputClosed()) {
                    JsonObject ping = new JsonObject();
                    ping.addProperty("method", 7);
                    ping.addProperty("id", ++msgCounter);
                    ws.sendText(ping.toString(), true);
                }
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] DonationAlerts ping error", e);
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }
    }

    private String channelName(String type) {
        return switch (type) {
            case "goal" -> "$goals:goal_" + userId;
            case "poll" -> "$polls:poll_" + userId;
            default -> "$alerts:donation_" + userId;
        };
    }

    private String eventTypeForChannel(String channel, String fallback) {
        if (channel.startsWith("$goals:")) return "goal";
        if (channel.startsWith("$polls:")) return "poll";
        if (channel.startsWith("$alerts:")) return fallback == null || fallback.isBlank() ? "donation" : fallback;
        return fallback == null || fallback.isBlank() ? "unknown" : fallback;
    }

    private static List<String> normalizeChannels(List<String> channels) {
        List<String> result = new ArrayList<>();
        if (channels != null) {
            for (String raw : channels) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if ((value.equals("donation") || value.equals("goal") || value.equals("poll")) && !result.contains(value)) {
                    result.add(value);
                }
            }
        }
        if (result.isEmpty()) result.add("donation");
        return result;
    }

    private static JsonObject parseObject(String body) {
        JsonReader reader = new JsonReader(new StringReader(body));
        reader.setLenient(true);
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    private static Map<String, String> parseSubscriptionTokens(String body) {
        JsonReader reader = new JsonReader(new StringReader(body));
        reader.setLenient(true);
        JsonElement root = JsonParser.parseReader(reader);
        JsonArray data = null;

        if (root.isJsonArray()) {
            data = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("data") && object.get("data").isJsonArray()) {
                data = object.getAsJsonArray("data");
            } else if (object.has("channels") && object.get("channels").isJsonArray()) {
                data = object.getAsJsonArray("channels");
            }
        }

        Map<String, String> tokens = new LinkedHashMap<>();
        if (data != null) {
            for (JsonElement element : data) {
                JsonObject item = element.getAsJsonObject();
                String channel = getString(item, "channel", "");
                String token = getString(item, "token", "");
                if (!channel.isBlank() && !token.isBlank()) {
                    tokens.put(channel, token);
                }
            }
        }
        return tokens;
    }

    private static String getString(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://www.donationalerts.com/api/v1";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static String formatAmount(double amount) {
        String value = Double.toString(amount);
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    private record UserInfo(int userId, String socketConnectionToken) {}
}
