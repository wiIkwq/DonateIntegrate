package com.bogdan3000.dintegrate.donation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import org.slf4j.Logger;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DonationAlertsOAuthClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FIRST_PORT = 8787;
    private static final int LAST_PORT = 8797;
    private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);

    private final String callbackBaseUrl;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public DonationAlertsOAuthClient(String callbackBaseUrl) {
        this.callbackBaseUrl = trimTrailingSlash(callbackBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("DonationAlertsOAuth");
            return t;
        });
    }

    public CompletableFuture<Result> authorize() {
        return CompletableFuture
                .supplyAsync(this::authorizeBlocking, executor)
                .whenComplete((result, error) -> executor.shutdown());
    }

    private Result authorizeBlocking() {
        try (ServerSocket server = bindLocalServer()) {
            int port = server.getLocalPort();
            server.setSoTimeout((int) CALLBACK_TIMEOUT.toMillis());

            String returnUrl = "http://127.0.0.1:" + port + "/integro/donationalerts/callback";
            String authUrl = callbackBaseUrl + "/integro/donationalerts/start?return_url="
                    + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);

            LOGGER.info("[DIntegrate] Opening DonationAlerts OAuth in browser. Local callback port: {}", port);
            Util.getPlatform().openUri(URI.create(authUrl));

            Callback callback = waitForCallback(server);
            if (!callback.error().isBlank()) {
                throw new IOException(callback.error());
            }

            return exchangeTicket(callback.ticket());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ServerSocket bindLocalServer() throws IOException {
        IOException last = null;
        for (int port = FIRST_PORT; port <= LAST_PORT; port++) {
            try {
                ServerSocket server = new ServerSocket();
                server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
                return server;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("No free local callback port from " + FIRST_PORT + " to " + LAST_PORT, last);
    }

    private Callback waitForCallback(ServerSocket server) throws IOException {
        long deadline = System.currentTimeMillis() + CALLBACK_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = server.accept()) {
                Callback callback = handleRequest(socket);
                if (callback != null) {
                    return callback;
                }
            }
        }
        throw new SocketTimeoutException("DonationAlerts OAuth callback timed out.");
    }

    private Callback handleRequest(Socket socket) throws IOException {
        socket.setSoTimeout(5000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return null;
        }

        String line;
        while ((line = reader.readLine()) != null && !line.isBlank()) {
            // Consume headers.
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 2 || !"GET".equals(parts[0])) {
            writeHtml(socket, 405, "DonationAlerts OAuth", "Unsupported local callback request.");
            return null;
        }

        URI uri = URI.create(parts[1]);
        if (!"/integro/donationalerts/callback".equals(uri.getPath())) {
            writeHtml(socket, 404, "DonationAlerts OAuth", "This local endpoint is only for Integro authorization.");
            return null;
        }

        Map<String, String> query = parseQuery(uri.getRawQuery());
        String ticket = query.getOrDefault("ticket", "");
        String status = query.getOrDefault("status", "");
        String error = query.getOrDefault("error", "");

        if (!error.isBlank() || !status.equals("ok") || ticket.isBlank()) {
            writeHtml(socket, 400, "DonationAlerts OAuth failed", "Return to Minecraft and try again.");
            return new Callback("", error.isBlank() ? "DonationAlerts OAuth did not return a ticket." : error);
        }

        writeHtml(socket, 200, "DonationAlerts connected", "You can return to Minecraft.");
        return new Callback(ticket, "");
    }

    private Result exchangeTicket(String ticket) throws IOException, InterruptedException {
        String url = callbackBaseUrl + "/integro/donationalerts/token?ticket="
                + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ticket exchange failed: HTTP " + response.statusCode() + " " + response.body());
        }

        JsonObject root = parseObject(response.body());
        JsonObject token = root.getAsJsonObject("token");
        JsonObject user = root.getAsJsonObject("user");
        if (token == null || user == null) {
            throw new IOException("Ticket exchange response is missing token or user.");
        }

        String accessToken = getString(token, "access_token", "");
        String refreshToken = getString(token, "refresh_token", "");
        int userId = getInt(user, "id", 0);
        String userName = getString(user, "name", getString(user, "code", ""));

        if (accessToken.isBlank() || userId <= 0) {
            throw new IOException("DonationAlerts OAuth response did not include access_token or user id.");
        }

        return new Result(accessToken, refreshToken, userId, userName);
    }

    private static void writeHtml(Socket socket, int status, String title, String message) throws IOException {
        String body = "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escapeHtml(title)
                + "</title><style>body{font-family:system-ui,sans-serif;background:#101112;color:#f2f4f7;display:grid;place-items:center;min-height:100vh;margin:0}"
                + "main{max-width:560px;padding:24px}h1{margin:0 0 12px;font-size:34px}p{color:#b8bec8;font-size:18px}</style></head>"
                + "<body><main><h1>" + escapeHtml(title) + "</h1><p>" + escapeHtml(message) + "</p></main></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        OutputStream out = socket.getOutputStream();
        String headers = "HTTP/1.1 " + status + " " + statusText(status) + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private static String statusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "OK";
        };
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;

        for (String part : raw.split("&")) {
            int idx = part.indexOf('=');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String value = idx >= 0 ? part.substring(idx + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static JsonObject parseObject(String body) {
        JsonReader reader = new JsonReader(new StringReader(body));
        reader.setLenient(true);
        return JsonParser.parseReader(reader).getAsJsonObject();
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

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://callback.bohdan.lol";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    private record Callback(String ticket, String error) {}

    public record Result(String accessToken, String refreshToken, int userId, String userName) {}
}
