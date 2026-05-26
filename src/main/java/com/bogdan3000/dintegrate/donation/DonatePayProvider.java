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
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DonatePay WebSocket клиент с защитой от дубликатов соединений.
 * Без автопереподключения. Управляется только вручную (Start/Stop).
 */
public class DonatePayProvider implements DonationProvider, WebSocket.Listener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String accessToken;
    private final int userId;
    private final String tokenUrl;
    private final String socketUrl;
    private final Consumer<DonationEvent> donationHandler;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("DonatePayScheduler");
                return t;
            });

    private WebSocket socket;
    private ScheduledFuture<?> pingTask;

    private String connectToken;
    private String clientId;
    private String subscriptionToken;
    private int msgCounter = 2;

    private volatile boolean connecting = false;
    private volatile boolean connected = false;
    private volatile boolean subscribed = false;
    private volatile String statusText = "Disconnected";

    private long lastConnectAttempt = 0L;
    private static final long COOLDOWN_MS = 8000;

    public DonatePayProvider(String accessToken, int userId, String tokenUrl, String socketUrl, Consumer<DonationEvent> handler) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.tokenUrl = tokenUrl;
        this.socketUrl = socketUrl;
        this.donationHandler = handler;
    }

    // ============================================================
    // Подключение
    // ============================================================

    @Override
    public synchronized void connect() {
        long now = System.currentTimeMillis();
        if (now - lastConnectAttempt < COOLDOWN_MS) {
            LOGGER.warn("[DIntegrate] Connection attempt blocked — cooldown active ({} ms left)", COOLDOWN_MS - (now - lastConnectAttempt));
            statusText = "Cooldown";
            return;
        }
        lastConnectAttempt = now;

        if (connected || connecting) {
            LOGGER.warn("[DIntegrate] Connection already active — skipping connect()");
            statusText = subscribed ? "Connected" : "Connecting";
            return;
        }

        if (accessToken == null || accessToken.isBlank() || userId <= 0) {
            LOGGER.error("[DIntegrate] Invalid token or user_id in config!");
            statusText = userId <= 0 ? "Missing user id" : "Missing token";
            return;
        }

        connecting = true;
        subscribed = false;
        statusText = "Requesting token";
        LOGGER.info("[DIntegrate] Requesting connection token from {}", tokenUrl);

        getConnectionToken().thenAccept(token -> {
            if (token == null || token.isEmpty()) {
                LOGGER.error("[DIntegrate] Failed to get connection token. (Maybe wrong token?)");
                connecting = false;
                statusText = "Token request failed";
                return;
            }

            this.connectToken = token;
            statusText = "Opening WebSocket";
            LOGGER.info("[DIntegrate] Got connection token, connecting to WebSocket...");

            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(socketUrl), this)
                    .thenAccept(ws -> {
                        this.socket = ws;
                        startPing(ws);
                        scheduler.schedule(() -> sendHandshake(ws), 500, TimeUnit.MILLISECONDS);
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("[DIntegrate] WebSocket connection failed: {}", ex.getMessage());
                        connecting = false;
                        statusText = "WebSocket failed";
                        return null;
                    });
        });
    }

    private void sendHandshake(WebSocket ws) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("id", 1);
            JsonObject params = new JsonObject();
            params.addProperty("token", connectToken);
            params.addProperty("name", "js");
            root.add("params", params);
            ws.sendText(root.toString(), true);
            statusText = "Handshake sent";
            LOGGER.info("[DIntegrate] Sent handshake (step 1)");
        } catch (Exception e) {
            statusText = "Handshake failed";
            LOGGER.error("[DIntegrate] Error sending handshake", e);
        }
    }

    private void sendSubscribe(WebSocket ws) {
        try {
            String channel = "$public:" + userId;
            JsonObject root = new JsonObject();
            root.addProperty("method", 1);
            root.addProperty("id", 2);
            JsonObject params = new JsonObject();
            params.addProperty("channel", channel);
            params.addProperty("token", subscriptionToken);
            root.add("params", params);
            ws.sendText(root.toString(), true);
            statusText = "Subscribing";
            LOGGER.info("[DIntegrate] Sent subscribe (step 2)");
        } catch (Exception e) {
            statusText = "Subscribe failed";
            LOGGER.error("[DIntegrate] Error sending subscribe", e);
        }
    }

    // ============================================================
    // HTTP-запросы
    // ============================================================

    private CompletableFuture<String> getConnectionToken() {
        try {
            String body = "{\"access_token\":\"" + accessToken + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            LOGGER.error("[DIntegrate] HTTP error {} from DonatePay token API", resp.statusCode());
                            statusText = "HTTP " + resp.statusCode() + " token";
                            return null;
                        }
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            return json.has("token") ? json.get("token").getAsString() : null;
                        } catch (Exception e) {
                            statusText = "Bad token response";
                            LOGGER.error("[DIntegrate] Token parse error", e);
                            return null;
                        }
                    });
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] HTTP error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<String> getSubscriptionToken(String clientId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client", clientId);
            body.add("channels", JsonParser.parseString("[\"$public:" + userId + "\"]"));

            String url = tokenUrl + "?access_token=" + accessToken;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            LOGGER.error("[DIntegrate] Subscription token HTTP error {}", resp.statusCode());
                            statusText = "HTTP " + resp.statusCode() + " subscribe";
                            return null;
                        }
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("channels")) {
                                JsonObject chan = json.getAsJsonArray("channels").get(0).getAsJsonObject();
                                return chan.get("token").getAsString();
                            }
                        } catch (Exception e) {
                            statusText = "Bad subscribe response";
                            LOGGER.error("[DIntegrate] Subscription token parse error", e);
                        }
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Subscription token HTTP error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    // ============================================================
    // WebSocket события
    // ============================================================

    @Override
    public void onOpen(WebSocket webSocket) {
        connected = true;
        connecting = false;
        statusText = "WebSocket opened";
        LOGGER.info("[DIntegrate] WebSocket opened.");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
        try {
            String msg = message.toString();
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();

            if (json.has("id") && json.get("id").getAsInt() == 1 && json.has("result")) {
                clientId = json.getAsJsonObject("result").get("client").getAsString();
                getSubscriptionToken(clientId).thenAccept(subToken -> {
                    if (subToken != null) {
                        this.subscriptionToken = subToken;
                        sendSubscribe(webSocket);
                    } else {
                        statusText = "Subscribe token failed";
                        LOGGER.error("[DIntegrate] Failed to get subscription token.");
                    }
                });
            } else if (json.has("id") && json.get("id").getAsInt() == 2 && json.has("result")) {
                subscribed = true;
                statusText = "Connected";
                LOGGER.info("[DIntegrate] Successfully subscribed to $public:{}.", userId);
            } else if (json.has("result") && json.getAsJsonObject("result").has("data")) {
                JsonObject vars = json.getAsJsonObject("result")
                        .getAsJsonObject("data")
                        .getAsJsonObject("data")
                        .getAsJsonObject("notification")
                        .getAsJsonObject("vars");
                handleDonation(vars);
            }

        } catch (Exception e) {
            LOGGER.error("[DIntegrate] WS parse error", e);
            statusText = "Bad WebSocket message";
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
        LOGGER.warn("[DIntegrate] WebSocket closed ({}): {}", code, reason);
        stopPing();
        socket = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.error("[DIntegrate] WebSocket error", error);
        connected = false;
        subscribed = false;
        connecting = false;
        statusText = "WebSocket error";
        stopPing();
    }

    // ============================================================
    // Ping
    // ============================================================

    private void startPing(WebSocket ws) {
        stopPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (ws != null && !ws.isOutputClosed()) {
                    msgCounter++;
                    ws.sendText("{\"method\":7,\"id\":" + msgCounter + "}", true);
                }
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Ping send error", e);
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }
    }

    // ============================================================
    // API
    // ============================================================

    @Override
    public boolean isConnected() {
        return connected && subscribed && socket != null && !socket.isOutputClosed();
    }

    public String getStatusText() {
        return statusText;
    }

    @Override
    public void onDonation(Consumer<DonationEvent> handler) { }

    @Override
    public synchronized void disconnect() {
        if (!connected && !connecting) {
            LOGGER.warn("[DIntegrate] Already disconnected — skipping.");
            return;
        }

        LOGGER.info("[DIntegrate] Disconnecting WebSocket...");
        connected = false;
        subscribed = false;
        connecting = false;
        statusText = "Disconnected";
        stopPing();

        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "Manual disconnect");
            } catch (Exception e) {
                LOGGER.warn("[DIntegrate] Socket close error: {}", e.getMessage());
            } finally {
                socket = null;
            }
        }
    }

    private void handleDonation(JsonObject vars) {
        try {
            String name = vars.has("name") ? vars.get("name").getAsString() : "Unknown";
            double sum = vars.has("sum") ? vars.get("sum").getAsDouble() : 0.0;
            String msg = vars.has("comment") ? vars.get("comment").getAsString() : "";
            donationHandler.accept(new DonationEvent(name, sum, msg, -1));
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Donation parse error", e);
        }
    }
}
