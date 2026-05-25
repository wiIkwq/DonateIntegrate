package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.donation.DonationAlertsProvider;
import com.bogdan3000.dintegrate.donation.DonationProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;

@Mod("dintegrate")
@Mod.EventBusSubscriber(modid = "dintegrate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DonateIntegrate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Config config;
    private static DonatePayProvider donatePayProvider;
    private static DonationAlertsProvider donationAlertsProvider;

    public DonateIntegrate() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // === ИНИЦИАЛИЗАЦИЯ ===
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[DIntegrate] Client initialized");
        try {
            config = new Config();
            config.load();
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Failed to load config", e);
        }
    }

    // === РЕГИСТРАЦИЯ КОМАНД ===
    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        var d = event.getDispatcher();

        d.register(Commands.literal("dpi")

                // === TOKEN ===
                .then(Commands.literal("token")
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(ctx -> {
                                    String v = StringArgumentType.getString(ctx, "value");
                                    saveToJsonConfig("token", v);
                                    reloadConfig(true);
                                    sendClientMessage("§aToken updated and reconnected!");
                                    return 1;
                                })))

                // === USER ===
                .then(Commands.literal("user")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "value");
                                    saveToJsonConfig("user_id", id);
                                    reloadConfig(true);
                                    sendClientMessage("§aUser ID updated and reconnected!");
                                    return 1;
                                })))

                // === DONATIONALERTS ===
                .then(Commands.literal("da")
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "value");
                                            saveToJsonConfig("donationalerts_enabled", enabled);
                                            reloadConfig(true);
                                            sendClientMessage(enabled
                                                    ? "§aDonationAlerts enabled and connection restarted!"
                                                    : "§cDonationAlerts disabled and connection restarted.");
                                            return 1;
                                        })))
                        .then(Commands.literal("token")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String v = StringArgumentType.getString(ctx, "value");
                                            saveToJsonConfig("donationalerts_access_token", v);
                                            reloadConfig(true);
                                            sendClientMessage("§aDonationAlerts token updated and reconnected!");
                                            return 1;
                                        })))
                        .then(Commands.literal("user")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "value");
                                            saveToJsonConfig("donationalerts_user_id", id);
                                            reloadConfig(true);
                                            sendClientMessage("§aDonationAlerts user ID updated and reconnected!");
                                            return 1;
                                        })))
                        .then(Commands.literal("channels")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "value");
                                            JsonArray channels = parseDonationAlertsChannels(raw);
                                            saveToJsonConfig("donationalerts_channels", channels);
                                            reloadConfig(true);
                                            sendClientMessage("§aDonationAlerts channels updated: " + channels);
                                            return 1;
                                        }))))

                // === RELOAD ===
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            reloadConfig(false);
                            sendClientMessage("§bConfig reloaded (no reconnect).");
                            return 1;
                        }))

                // === CONNECTION ===
                .then(Commands.literal("start").executes(ctx -> {
                    startConnection();
                    sendClientMessage("§aConnection started!");
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    stopConnection();
                    sendClientMessage("§cConnection stopped.");
                    return 1;
                }))
                .then(Commands.literal("restart").executes(ctx -> {
                    restartConnection();
                    sendClientMessage("§bConnection restarted!");
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    sendClientMessage("§bDonatePay: " + providerStatus(donatePayProvider));
                    sendClientMessage("§bDonationAlerts: " + donationAlertsStatus());
                    return 1;
                }))

                // === TEST ===
                .then(Commands.literal("test")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("sum", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    double sum = DoubleArgumentType.getDouble(ctx, "sum");
                                                    String msg = StringArgumentType.getString(ctx, "message");

                                                    var rule = config.getRules().entrySet().stream()
                                                            .filter(e -> Math.abs(e.getKey() - sum) < 0.0001)
                                                            .map(e -> e.getValue())
                                                            .findFirst()
                                                            .orElse(null);

                                                    if (rule == null) {
                                                        sendClientMessage("§c[DIntegrate] No rule found for this amount (" + sum + ")");
                                                        return 0;
                                                    }

                                                    sendClientMessage("§d[DIntegrate] Simulating donation " + sum + "₽ (" + rule.mode + ")");
                                                    new ActionHandler(config).execute(sum, name, msg);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    // === ПЕРЕЗАГРУЗКА КОНФИГА ===
    private static void reloadConfig(boolean restart) {
        try {
            config.load();
            int count = config.getRules().size();

            if (restart) {
                restartConnection();
                sendClientMessage("§b[DIntegrate] Config reloaded (" + count + " rules) and reconnected.");
            } else {
                sendClientMessage("§b[DIntegrate] Config reloaded (" + count + " rules).");
            }

            LOGGER.info("[DIntegrate] Config reloaded successfully ({} rules). Restart = {}", count, restart);
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Reload failed", e);
            sendClientMessage("§c[DIntegrate] Failed to reload config. Check logs.");
        }
    }

    // === СОХРАНЕНИЕ JSON ===
    public static void saveToJsonConfig(String key, String value) {
        saveToJsonConfig(key, new JsonPrimitive(value));
    }

    public static void saveToJsonConfig(String key, Number value) {
        saveToJsonConfig(key, new JsonPrimitive(value));
    }

    public static void saveToJsonConfig(String key, Boolean value) {
        saveToJsonConfig(key, new JsonPrimitive(value));
    }

    public static void saveToJsonConfig(String key, JsonElement value) {
        try {
            Path path = Paths.get("config", "dintegrate.json");
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{}");
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject json;

            try (FileReader fileReader = new FileReader(path.toFile())) {
                JsonReader reader = new JsonReader(fileReader);
                reader.setLenient(true);
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            json.add(key, value);

            try (FileWriter writer = new FileWriter(path.toFile())) {
                gson.toJson(json, writer);
            }

            LOGGER.info("[DIntegrate] Updated {} in JSON config: {}", key, logValue(key, value));
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Failed to update JSON config", e);
        }
    }

    // === ДОСТУП ДЛЯ GUI ===
    public static DonatePayProvider getDonateProvider() {
        return donatePayProvider;
    }

    public static DonationAlertsProvider getDonationAlertsProvider() {
        return donationAlertsProvider;
    }

    public static Config getConfig() {
        return config;
    }

    public static void startConnection() {
        if (config == null) {
            LOGGER.error("[DIntegrate] Cannot start providers before config is loaded.");
            return;
        }

        if (config.isDonatePayEnabled() && (donatePayProvider == null || !donatePayProvider.isConnected())) {
            if (donatePayProvider != null) {
                donatePayProvider.disconnect();
            }
            donatePayProvider = new DonatePayProvider(
                    config.getToken(),
                    config.getUserId(),
                    config.getTokenUrl(),
                    config.getSocketUrl(),
                    DonateIntegrate::handleProviderEvent
            );
            donatePayProvider.connect();
        }

        if (config.isDonationAlertsEnabled() && (donationAlertsProvider == null || !donationAlertsProvider.isConnected())) {
            if (donationAlertsProvider != null) {
                donationAlertsProvider.disconnect();
            }
            donationAlertsProvider = new DonationAlertsProvider(
                    config.getDonationAlertsAccessToken(),
                    config.getDonationAlertsUserId(),
                    config.getDonationAlertsApiUrl(),
                    config.getDonationAlertsSocketUrl(),
                    config.getDonationAlertsChannels(),
                    DonateIntegrate::handleProviderEvent
            );
            donationAlertsProvider.connect();
        }
    }

    public static void stopConnection() {
        if (donatePayProvider != null) {
            donatePayProvider.disconnect();
            donatePayProvider = null;
        }
        if (donationAlertsProvider != null) {
            donationAlertsProvider.disconnect();
            donationAlertsProvider = null;
        }
    }

    public static void restartConnection() {
        stopConnection();
        startConnection();
    }

    public static void sendClientMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(text));
    }

    private static void handleProviderEvent(DonationProvider.DonationEvent event) {
        if (!"donation".equals(event.getEventType()) && !"merchandise-sale".equals(event.getEventType())) {
            LOGGER.info("[DIntegrate] Received unsupported {} event from {}.",
                    event.getEventType(), event.getSource());
            return;
        }

        LOGGER.info("[DIntegrate] Event from {}: {}", event.getSource(), event);
        sendClientMessage("§d[DIntegrate] " + event.getSource() + ": " + event.getUsername()
                + " donated " + formatAmount(event.getAmount())
                + (event.getCurrency().isBlank() ? "" : " " + event.getCurrency()));
        new ActionHandler(config).execute(event);
    }

    private static String providerStatus(DonationProvider provider) {
        return provider != null && provider.isConnected() ? "§aConnected" : "§cDisconnected";
    }

    private static String donationAlertsStatus() {
        if (donationAlertsProvider == null) {
            return "§cDisconnected";
        }
        String base = donationAlertsProvider.isConnected() ? "§aConnected" : "§e" + donationAlertsProvider.getStatusText();
        int userId = donationAlertsProvider.getResolvedUserId();
        return base + (userId > 0 ? " §7(user " + userId + ")" : "");
    }

    private static String formatAmount(double amount) {
        String value = Double.toString(amount);
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    private static String logValue(String key, JsonElement value) {
        String normalized = key == null ? "" : key.toLowerCase();
        if (normalized.contains("token") || normalized.contains("secret")) {
            return "\"***\"";
        }
        return String.valueOf(value);
    }

    private static JsonArray parseDonationAlertsChannels(String raw) {
        JsonArray channels = new JsonArray();
        for (String part : raw.split("[,\\s]+")) {
            String value = part.trim().toLowerCase();
            if ((value.equals("donation") || value.equals("goal") || value.equals("poll")) && !contains(channels, value)) {
                channels.add(value);
            }
        }
        if (channels.isEmpty()) channels.add("donation");
        return channels;
    }

    private static boolean contains(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.getAsString().equals(value)) return true;
        }
        return false;
    }
}
