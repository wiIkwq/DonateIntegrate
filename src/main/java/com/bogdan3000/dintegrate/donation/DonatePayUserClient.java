package com.bogdan3000.dintegrate.donation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class DonatePayUserClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String USER_URL = "https://donatepay.ru/api/v1/user";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public CompletableFuture<Result> loadUser(String accessToken) {
        return CompletableFuture.supplyAsync(() -> loadUserBlocking(accessToken));
    }

    private Result loadUserBlocking(String accessToken) {
        try {
            String token = accessToken == null ? "" : accessToken.trim();
            if (token.isBlank() || token.startsWith("YOUR_")) throw new UserLoadException("DonatePay token is empty.", 0);

            String url = USER_URL + "?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "DonateIntegrate/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UserLoadException(messageForStatus(response.statusCode(), response.body()), response.statusCode());
            }

            JsonObject root = parseObject(response.body());
            String status = getString(root, "status", "");
            if (!status.isBlank() && !"success".equalsIgnoreCase(status)) {
                throw new UserLoadException("DonatePay user API failed: " + getString(root, "message", status), 200);
            }

            JsonObject data = root.getAsJsonObject("data");
            int userId = getInt(data, "id", 0);
            String name = getString(data, "name", "");
            if (userId <= 0) {
                throw new UserLoadException("DonatePay user API response does not include user id.", 200);
            }

            return new Result(token, userId, name);
        } catch (HttpTimeoutException e) {
            throw new UserLoadException("DonatePay user API timed out. Try again later.", 0, e);
        } catch (UserLoadException e) {
            LOGGER.warn("[DIntegrate] DonatePay user load failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.warn("[DIntegrate] DonatePay user load failed: {}", e.toString());
            throw new UserLoadException("DonatePay user API request failed.", 0, e);
        }
    }

    private static String messageForStatus(int statusCode, String body) {
        if (statusCode == 429) return "DonatePay rate limit. Wait a minute and try again.";
        if (body != null && body.trim().startsWith("{")) {
            try {
                JsonObject root = parseObject(body);
                String message = getString(root, "message", "");
                if (!message.isBlank()) return "DonatePay user API HTTP " + statusCode + ": " + message;
            } catch (Exception ignored) {
            }
        }
        return "DonatePay user API returned HTTP " + statusCode + ".";
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

    public record Result(String accessToken, int userId, String userName) {}

    public static class UserLoadException extends RuntimeException {
        private final int statusCode;

        public UserLoadException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public UserLoadException(String message, int statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
