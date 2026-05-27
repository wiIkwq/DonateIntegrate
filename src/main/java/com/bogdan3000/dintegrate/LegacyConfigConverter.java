package com.bogdan3000.dintegrate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LegacyConfigConverter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path LEGACY_PATH = CONFIG_DIR.resolve("dintegrate.cfg");
    private static final Path JSON_PATH = CONFIG_DIR.resolve("dintegrate.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern RULE_KEY = Pattern.compile("rule_(\\d+)\\.(.+)");

    private LegacyConfigConverter() {}

    public static boolean legacyConfigExists() {
        return Files.exists(LEGACY_PATH) && Files.isRegularFile(LEGACY_PATH);
    }

    public static Path legacyPath() {
        return LEGACY_PATH;
    }

    public static int convertAndArchive() throws IOException {
        Files.createDirectories(CONFIG_DIR);
        Map<String, String> values = readLegacyValues();

        Config converted = readExistingJsonOrDefault();
        converted.donatepay_enabled = true;
        converted.token = values.getOrDefault("token", converted.token);
        converted.user_id = parseInt(values.get("user_id"), 0);
        converted.donatepay_user_name = "";
        converted.token_url = valueOrDefault(values.get("token_url"), converted.token_url);
        converted.socket_url = valueOrDefault(values.get("socket_url"), converted.socket_url);
        if (converted.donationalerts_channels == null) {
            converted.donationalerts_channels = new ArrayList<>(List.of("donation"));
        }
        converted.rules = parseRules(values);
        converted.legacy_values = collectUnknownLegacyValues(values);

        if (converted.rules.isEmpty()) {
            LOGGER.warn("[DIntegrate] Legacy config has no rules; default JSON rules will be kept empty.");
        }

        backupJsonIfNeeded();
        Files.writeString(JSON_PATH, GSON.toJson(converted), StandardCharsets.UTF_8);
        archiveLegacy();
        LOGGER.info("[DIntegrate] Legacy config converted: {} rules", converted.rules.size());
        return converted.rules.size();
    }

    private static Config readExistingJsonOrDefault() {
        if (!Files.exists(JSON_PATH)) {
            return new Config();
        }

        try (var fileReader = Files.newBufferedReader(JSON_PATH, StandardCharsets.UTF_8)) {
            JsonReader reader = new JsonReader(fileReader);
            reader.setLenient(true);
            Config loaded = GSON.fromJson(reader, Config.class);
            return loaded != null ? loaded : new Config();
        } catch (Exception e) {
            LOGGER.warn("[DIntegrate] Existing JSON config could not be read before migration; using defaults.", e);
            return new Config();
        }
    }

    private static Map<String, String> readLegacyValues() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : readLegacyText().split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            values.put(key, value);
        }
        return values;
    }

    private static String readLegacyText() throws IOException {
        byte[] bytes = Files.readAllBytes(LEGACY_PATH);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("windows-1251"));
    }

    private static List<Config.DonationRule> parseRules(Map<String, String> values) {
        Map<Integer, Map<String, String>> grouped = new TreeMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Matcher matcher = RULE_KEY.matcher(entry.getKey());
            if (!matcher.matches()) continue;
            int index = parseInt(matcher.group(1), -1);
            if (index < 0) continue;
            grouped.computeIfAbsent(index, ignored -> new LinkedHashMap<>()).put(matcher.group(2), entry.getValue());
        }

        List<Config.DonationRule> rules = new ArrayList<>();
        for (Map<String, String> ruleValues : grouped.values()) {
            double amount = parseDouble(ruleValues.get("amount"), Double.NaN);
            if (Double.isNaN(amount)) continue;

            Config.DonationRule rule = new Config.DonationRule();
            rule.amount = amount;
            rule.mode = valueOrDefault(ruleValues.get("mode"), "all");
            rule.commands = parseCommands(ruleValues);
            rules.add(rule);
        }
        return rules;
    }

    private static List<String> parseCommands(Map<String, String> ruleValues) {
        List<String> commands = new ArrayList<>();
        String startDelay = ruleValues.get("startdelay");
        if (startDelay != null && !startDelay.isBlank() && !startDelay.equals("0")) {
            commands.add("/delay " + startDelay.trim());
        }

        TreeMap<Integer, String> ordered = new TreeMap<>();
        for (Map.Entry<String, String> entry : ruleValues.entrySet()) {
            if (!entry.getKey().startsWith("cmd")) continue;
            int index = parseInt(entry.getKey().substring(3), -1);
            if (index > 0) ordered.put(index, entry.getValue());
        }

        for (String command : ordered.values()) {
            if (command == null || command.isBlank()) continue;
            commands.add(normalizeCommand(command));
        }
        return commands;
    }

    private static String normalizeCommand(String command) {
        String value = command.trim();
        if (value.startsWith("/")) return value;
        return "/" + value;
    }

    private static void backupJsonIfNeeded() throws IOException {
        if (!Files.exists(JSON_PATH)) return;
        Path backup = CONFIG_DIR.resolve("dintegrate.json.before_cfg_migration.bak");
        int i = 1;
        while (Files.exists(backup)) {
            backup = CONFIG_DIR.resolve("dintegrate.json.before_cfg_migration." + i + ".bak");
            i++;
        }
        Files.copy(JSON_PATH, backup);
    }

    private static void archiveLegacy() throws IOException {
        Path archive = CONFIG_DIR.resolve("dintegrate.cfg.zip");
        int i = 1;
        while (Files.exists(archive)) {
            archive = CONFIG_DIR.resolve("dintegrate.cfg." + i + ".zip");
            i++;
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("dintegrate.cfg"));
            Files.copy(LEGACY_PATH, zip);
            zip.closeEntry();
        }
        Files.delete(LEGACY_PATH);
    }

    private static Map<String, String> collectUnknownLegacyValues(Map<String, String> values) {
        Map<String, String> legacyValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (isKnownLegacyKey(entry.getKey()) || RULE_KEY.matcher(entry.getKey()).matches()) {
                continue;
            }
            legacyValues.put(entry.getKey(), entry.getValue());
        }
        return legacyValues;
    }

    private static boolean isKnownLegacyKey(String key) {
        return switch (key) {
            case "token", "user_id", "token_url", "socket_url" -> true;
            default -> false;
        };
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            if (value == null || value.isBlank()) return fallback;
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            if (value == null || value.isBlank()) return fallback;
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
