package com.bogdan3000.dintegrate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Paths.get("config", "dintegrate.json");

    public boolean donatepay_enabled = true;
    public String token = "YOUR_DONATEPAY_TOKEN";
    public int user_id = 0;
    public String donatepay_user_name = "";
    public String token_url = "https://donatepay.ru/api/v2/socket/token";
    public String socket_url = "wss://centrifugo.donatepay.ru/connection/websocket?format=json";
    public boolean donationalerts_enabled = false;
    public String donationalerts_access_token = "YOUR_DONATIONALERTS_ACCESS_TOKEN";
    public String donationalerts_refresh_token = "";
    public int donationalerts_user_id = 0;
    public String donationalerts_user_name = "";
    public String donationalerts_oauth_base_url = "https://callback.bohdan.lol";
    public String donationalerts_api_url = "https://www.donationalerts.com/api/v1";
    public String donationalerts_socket_url = "wss://centrifugo.donationalerts.com/connection/websocket";
    public List<String> donationalerts_channels = new ArrayList<>(List.of("donation"));
    public List<DonationRule> rules = new ArrayList<>();
    public Map<String, String> legacy_values = new LinkedHashMap<>();

    public static class DonationRule {
        public double amount;
        public String mode;
        public List<String> commands = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public void load() throws IOException {
        if (!Files.exists(CONFIG_PATH)) {
            LOGGER.warn("[DIntegrate] JSON config not found — creating default one");
            saveDefault();
        }

        try (FileReader fileReader = new FileReader(CONFIG_PATH.toFile())) {
            JsonReader reader = new JsonReader(fileReader);
            reader.setLenient(true);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Config loaded = GSON.fromJson(root, Config.class);
            if (loaded != null) {
                this.token = loaded.token != null ? loaded.token : this.token;
                this.user_id = loaded.user_id;
                this.donatepay_user_name = loaded.donatepay_user_name != null
                        ? loaded.donatepay_user_name
                        : this.donatepay_user_name;
                this.token_url = loaded.token_url != null ? loaded.token_url : this.token_url;
                this.socket_url = loaded.socket_url != null ? loaded.socket_url : this.socket_url;
                this.donatepay_enabled = !root.has("donatepay_enabled") || loaded.donatepay_enabled;
                this.donationalerts_enabled = loaded.donationalerts_enabled;
                this.donationalerts_access_token = loaded.donationalerts_access_token != null
                        ? loaded.donationalerts_access_token
                        : this.donationalerts_access_token;
                this.donationalerts_refresh_token = loaded.donationalerts_refresh_token != null
                        ? loaded.donationalerts_refresh_token
                        : this.donationalerts_refresh_token;
                this.donationalerts_user_id = loaded.donationalerts_user_id;
                this.donationalerts_user_name = loaded.donationalerts_user_name != null
                        ? loaded.donationalerts_user_name
                        : this.donationalerts_user_name;
                this.donationalerts_oauth_base_url = loaded.donationalerts_oauth_base_url != null
                        ? loaded.donationalerts_oauth_base_url
                        : this.donationalerts_oauth_base_url;
                this.donationalerts_api_url = loaded.donationalerts_api_url != null
                        ? loaded.donationalerts_api_url
                        : this.donationalerts_api_url;
                this.donationalerts_socket_url = loaded.donationalerts_socket_url != null
                        ? loaded.donationalerts_socket_url
                        : this.donationalerts_socket_url;
                this.donationalerts_channels = loaded.donationalerts_channels != null
                        ? loaded.donationalerts_channels
                        : new ArrayList<>(List.of("donation"));
                this.rules = loaded.rules != null ? loaded.rules : new ArrayList<>();
                this.legacy_values = loaded.legacy_values != null ? loaded.legacy_values : new LinkedHashMap<>();
            }
            LOGGER.info("[DIntegrate] JSON config loaded — rules: {}", rules.size());
            for (DonationRule r : rules) {
                LOGGER.info("[DIntegrate] Rule {}₽ → {} commands (mode={})",
                        r.amount, r.commands.size(), r.mode);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("[DIntegrate] Failed to parse JSON config: {}", e.toString());
            throw new IOException("Invalid JSON syntax in dintegrate.json", e);
        }
    }

    private void saveDefault() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());

        Config def = new Config();

        DonationRule rule = new DonationRule();
        rule.amount = 10;
        rule.mode = "all";
        rule.commands = List.of(
                "/say {name} пожертвовал {sum}₽!",
                "/delay 1",
                "/say Сообщение: {message}",
                "/randomdelay 1-10",
                "/give @p minecraft:diamond 1"
        );

        def.rules = List.of(rule);

        // Комментарии и JSON объединяем в один текст
        String header = """
            /*
             * === DonateIntegrate Configuration ===
             *
             *  donatepay_enabled — включить/выключить DonatePay
             *  token — API ключ DonatePay; user_id / donatepay_user_name сохраняются автоматически через /api/v1/user
             *  token_url и socket_url — не трогай, если не знаешь зачем
             *
             *  donationalerts_enabled — включить/выключить DonationAlerts
             *  donationalerts_access_token / refresh_token — OAuth токены DonationAlerts
             *  donationalerts_user_id / user_name — сохраняются после авторизации через GUI
             *  donationalerts_oauth_base_url — callback backend для OAuth
             *  donationalerts_channels — каналы DonationAlerts: donation, goal, poll
             *
             * === Donation Rules ===
             *  Поле "rules" — список правил. Каждое правило имеет:
             *    amount — сумма доната
             *    mode — режим выполнения команд:
             *        "all"         — выполняет все команды по порядку
             *        "random"      — выбирает одну случайную команду
             *        "random_all"  — выполняет все команды, но в случайном порядке
             *        "randomN"     — выполняет N случайных команд (пример: "random3")
             *
             * === Дополнительные команды ===
             *    delay X           — задержка X секунд (пример: "delay 1")
             *    randomdelay A-B   — случайная задержка от A до B секунд (пример: "randomdelay 1-3")
             *
             * === Плейсхолдеры ===
             *    {name}      — имя донатера
             *    {sum}       — сумма доната
             *    {currency}  — валюта события
             *    {message}   — сообщение донатера
             *    {source}    — источник события: donatepay/donationalerts
             *    {event}     — тип события
             */
            """;

        String json = GSON.toJson(def);

        Files.writeString(CONFIG_PATH, header + "\n" + json);
        LOGGER.info("[DIntegrate] Default JSON config with documentation created at {}", CONFIG_PATH.toAbsolutePath());
    }

    public Map<Double, DonationRule> getRules() {
        Map<Double, DonationRule> map = new HashMap<>();
        for (DonationRule r : rules) {
            map.put(r.amount, r);
        }
        return map;
    }

    public String getToken() { return token; }
    public int getUserId() { return user_id; }
    public String getDonatePayUserName() { return donatepay_user_name; }
    public String getTokenUrl() { return token_url; }
    public String getSocketUrl() { return socket_url; }
    public boolean isDonatePayEnabled() { return donatepay_enabled; }
    public boolean isDonationAlertsEnabled() { return donationalerts_enabled; }
    public String getDonationAlertsAccessToken() { return donationalerts_access_token; }
    public String getDonationAlertsRefreshToken() { return donationalerts_refresh_token; }
    public int getDonationAlertsUserId() { return donationalerts_user_id; }
    public String getDonationAlertsUserName() { return donationalerts_user_name; }
    public String getDonationAlertsOAuthBaseUrl() { return donationalerts_oauth_base_url; }
    public String getDonationAlertsApiUrl() { return donationalerts_api_url; }
    public String getDonationAlertsSocketUrl() { return donationalerts_socket_url; }
    public List<String> getDonationAlertsChannels() { return donationalerts_channels; }
}
