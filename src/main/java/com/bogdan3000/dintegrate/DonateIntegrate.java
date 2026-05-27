package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.donation.DonatePayUserClient;
import com.bogdan3000.dintegrate.donation.DonationAlertsProvider;
import com.bogdan3000.dintegrate.donation.DonationProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Mod("dintegrate")
@Mod.EventBusSubscriber(modid = "dintegrate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DonateIntegrate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Config config;
    private static DonatePayProvider donatePayProvider;
    private static DonationAlertsProvider donationAlertsProvider;
    private static boolean legacyConfigPromptShown = false;

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

                .then(Commands.literal("dp")
                        .then(Commands.literal("token")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String v = StringArgumentType.getString(ctx, "value").trim();
                                            saveToJsonConfig("token", v);
                                            saveToJsonConfig("user_id", 0);
                                            saveToJsonConfig("donatepay_user_name", "");
                                            reloadConfig(false);
                                            sendClientMessage(Component.translatable("dintegrate.message.dp_token_saved"));
                                            return 1;
                                        })))
                        .then(Commands.literal("user")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "value");
                                            saveToJsonConfig("user_id", id);
                                            if (id == 0) saveToJsonConfig("donatepay_user_name", "");
                                            reloadConfig(false);
                                            sendClientMessage(Component.translatable("dintegrate.message.dp_user_saved"));
                                            return 1;
                                        })))
                        .then(Commands.literal("getid").executes(ctx -> {
                            if (config == null || config.getToken() == null || config.getToken().isBlank() || config.getToken().startsWith("YOUR_")) {
                                sendClientMessage(Component.translatable("dintegrate.message.dp_set_token_first"));
                                return 0;
                            }
                            sendClientMessage(Component.translatable("dintegrate.message.dp_loading_user_id"));
                            new DonatePayUserClient().loadUser(config.getToken()).whenComplete((result, error) ->
                                    Minecraft.getInstance().execute(() -> {
                                        if (error != null) {
                                            sendClientMessage(Component.translatable("dintegrate.message.dp_user_id_failed", rootMessage(error)));
                                            return;
                                        }
                                        saveToJsonConfig("donatepay_enabled", true);
                                        saveToJsonConfig("token", result.accessToken());
                                        saveToJsonConfig("user_id", result.userId());
                                        saveToJsonConfig("donatepay_user_name", result.userName());
                                        reloadConfig(false);
                                        sendClientMessage(Component.translatable("dintegrate.message.dp_user_id_saved_value",
                                                result.userId(),
                                                result.userName().isBlank() ? "" : " (" + result.userName() + ")"));
                                    }));
                            return 1;
                        }))
                        .then(Commands.literal("reconnect").executes(ctx -> {
                            saveToJsonConfig("donatepay_enabled", true);
                            reloadConfig(false);
                            restartDonatePayConnection();
                            sendClientMessage(Component.translatable("dintegrate.message.dp_reconnect_requested"));
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            stopDonatePayConnection();
                            sendClientMessage(Component.translatable("dintegrate.message.dp_stopped"));
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            sendClientMessage(Component.translatable("dintegrate.message.dp_status", donatePayStatus()));
                            return 1;
                        })))

                .then(Commands.literal("da")
                        .then(Commands.literal("token")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String v = StringArgumentType.getString(ctx, "value").trim();
                                            saveToJsonConfig("donationalerts_access_token", v);
                                            reloadConfig(false);
                                            sendClientMessage(Component.translatable("dintegrate.message.da_token_saved"));
                                            return 1;
                                        })))
                        .then(Commands.literal("user")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "value");
                                            saveToJsonConfig("donationalerts_user_id", id);
                                            reloadConfig(false);
                                            sendClientMessage(Component.translatable("dintegrate.message.da_user_saved"));
                                            return 1;
                                        })))
                        .then(Commands.literal("reset").executes(ctx -> {
                            saveToJsonConfig("donationalerts_enabled", false);
                            saveToJsonConfig("donationalerts_access_token", "YOUR_DONATIONALERTS_ACCESS_TOKEN");
                            saveToJsonConfig("donationalerts_refresh_token", "");
                            saveToJsonConfig("donationalerts_user_id", 0);
                            saveToJsonConfig("donationalerts_user_name", "");
                            reloadConfig(false);
                            stopDonationAlertsConnection();
                            sendClientMessage(Component.translatable("dintegrate.message.da_login_reset"));
                            return 1;
                        }))
                        .then(Commands.literal("channels")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String raw = StringArgumentType.getString(ctx, "value");
                                            JsonArray channels = parseDonationAlertsChannels(raw);
                                            saveToJsonConfig("donationalerts_channels", channels);
                                            reloadConfig(false);
                                            sendClientMessage(Component.translatable("dintegrate.message.da_channels_saved", channels.toString()));
                                            return 1;
                                        })))
                        .then(Commands.literal("reconnect").executes(ctx -> {
                            saveToJsonConfig("donationalerts_enabled", true);
                            reloadConfig(false);
                            restartDonationAlertsConnection();
                            sendClientMessage(Component.translatable("dintegrate.message.da_reconnect_requested"));
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            stopDonationAlertsConnection();
                            sendClientMessage(Component.translatable("dintegrate.message.da_stopped"));
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            sendClientMessage(Component.translatable("dintegrate.message.da_status", donationAlertsStatus()));
                            return 1;
                        })))

                // === RELOAD ===
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            reloadConfig(false);
                            sendClientMessage(Component.translatable("dintegrate.message.config_reloaded_no_reconnect"));
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
                                                        sendClientMessage(Component.translatable("dintegrate.message.no_rule_amount", sum));
                                                        return 0;
                                                    }

                                                    sendClientMessage(Component.translatable("dintegrate.message.simulating_donation", sum, rule.mode));
                                                    new ActionHandler(config).execute(sum, name, msg);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (legacyConfigPromptShown || !(event.getNewScreen() instanceof TitleScreen) || !LegacyConfigConverter.legacyConfigExists()) {
            return;
        }
        legacyConfigPromptShown = true;

        event.setNewScreen(new ConfirmScreen(confirmed -> {
            Minecraft mc = Minecraft.getInstance();
            if (confirmed) {
                try {
                    int rules = LegacyConfigConverter.convertAndArchive();
                    reloadConfig(false);
                    sendClientMessage(Component.translatable("dintegrate.message.legacy_converted", rules));
                } catch (Exception e) {
                    LOGGER.error("[DIntegrate] Legacy config conversion failed", e);
                    sendClientMessage(Component.translatable("dintegrate.message.legacy_failed"));
                }
            }
            mc.setScreen(new TitleScreen());
        },
                Component.translatable("dintegrate.legacy.title"),
                Component.translatable("dintegrate.legacy.body"),
                Component.translatable("dintegrate.legacy.convert"),
                Component.translatable("dintegrate.legacy.not_now")));
    }

    // === ПЕРЕЗАГРУЗКА КОНФИГА ===
    private static void reloadConfig(boolean restart) {
        try {
            config.load();
            int count = config.getRules().size();

            if (restart) {
                restartConnection();
                sendClientMessage(Component.translatable("dintegrate.message.config_reloaded_reconnected", count));
            } else {
                sendClientMessage(Component.translatable("dintegrate.message.config_reloaded", count));
            }

            LOGGER.info("[DIntegrate] Config reloaded successfully ({} rules). Restart = {}", count, restart);
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Reload failed", e);
            sendClientMessage(Component.translatable("dintegrate.message.config_reload_failed"));
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
                Files.writeString(path, "{}", StandardCharsets.UTF_8);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject json;

            try (var fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonReader reader = new JsonReader(fileReader);
                reader.setLenient(true);
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            json.add(key, value);

            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
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
        startDonatePayConnection();
        startDonationAlertsConnection();
    }

    public static void startDonatePayConnection() {
        if (config == null) {
            LOGGER.error("[DIntegrate] Cannot start DonatePay before config is loaded.");
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
    }

    public static void startDonationAlertsConnection() {
        if (config == null) {
            LOGGER.error("[DIntegrate] Cannot start DonationAlerts before config is loaded.");
            return;
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
        stopDonatePayConnection();
        stopDonationAlertsConnection();
    }

    public static void stopDonatePayConnection() {
        if (donatePayProvider != null) {
            donatePayProvider.disconnect();
            donatePayProvider = null;
        }
    }

    public static void stopDonationAlertsConnection() {
        if (donationAlertsProvider != null) {
            donationAlertsProvider.disconnect();
            donationAlertsProvider = null;
        }
    }

    public static void restartConnection() {
        stopConnection();
        startConnection();
    }

    public static void restartDonatePayConnection() {
        stopDonatePayConnection();
        startDonatePayConnection();
    }

    public static void restartDonationAlertsConnection() {
        stopDonationAlertsConnection();
        startDonationAlertsConnection();
    }

    public static void sendClientMessage(String text) {
        sendClientMessage(Component.literal(text));
    }

    public static void sendClientMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(message);
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

    private static String donatePayStatus() {
        if (donatePayProvider == null) {
            return "§cDisconnected";
        }
        String base = donatePayProvider.isConnected() ? "§aConnected" : "§e" + donatePayProvider.getStatusText();
        return base + (config != null && config.getUserId() > 0 ? " §7(user " + config.getUserId() + ")" : "");
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

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
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
