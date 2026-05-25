package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TabConfig extends TabBase {

    private EditBox tokenBox, userIdBox;
    private EditBox donationAlertsTokenBox, donationAlertsUserIdBox, donationAlertsChannelsBox;
    private Button saveButton, startButton, stopButton, restartButton, toggleTokenButton, toggleDonationAlertsTokenButton;
    private Button donatePayEnabledButton, donationAlertsEnabledButton;

    private boolean tokenVisible = false;
    private boolean donationAlertsTokenVisible = false;
    private String connectionStatus = "Unknown";
    private String donationAlertsStatus = "Unknown";
    private int connectionColor = 0xFFFFAA00;
    private int donationAlertsColor = 0xFFFFAA00;
    private int connectionY;
    private int labelX;
    private int donatePayTitleY;
    private int donationAlertsTitleY;
    private int donatePayTokenY;
    private int donatePayUserY;
    private int donationAlertsTokenY;
    private int donationAlertsUserY;
    private int donationAlertsChannelsY;

    private final Config config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public TabConfig(Config config) {
        super("Config");
        this.config = config;
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        Font font = mc.font;

        int cx = width / 2;
        int y = Math.max(34, height / 12);
        int fieldWidth = 240;
        int editButtonWidth = 58;
        int row = 27;

        boolean twoColumns = width >= 760;
        int donatePayX = twoColumns ? cx - 330 : cx - 120;
        int donationAlertsX = twoColumns ? cx + 20 : cx - 120;
        int donationAlertsY = twoColumns ? y : y + 150;
        labelX = twoColumns ? donatePayX - 88 : cx - 220;

        // === DonatePay ===
        donatePayTitleY = y;
        donatePayEnabledButton = Button.builder(Component.literal(enabledLabel(config.donatepay_enabled)), b -> toggleDonatePayEnabled())
                .bounds(donatePayX, y + 18, fieldWidth, 20)
                .build();
        addWidget(donatePayEnabledButton);

        donatePayTokenY = y + 18 + row;
        tokenBox = new EditBox(font, donatePayX, donatePayTokenY, fieldWidth - editButtonWidth - 6, 20, Component.literal("DonatePay token"));
        tokenBox.setMaxLength(2048);
        tokenBox.setValue(obfuscateToken(config.getToken()));
        tokenBox.setEditable(false);
        addWidget(tokenBox);

        toggleTokenButton = Button.builder(Component.literal("Edit"), b -> toggleTokenVisibility())
                .bounds(donatePayX + fieldWidth - editButtonWidth, donatePayTokenY, editButtonWidth, 20).build();
        addWidget(toggleTokenButton);

        donatePayUserY = donatePayTokenY + row;
        userIdBox = new EditBox(font, donatePayX, donatePayUserY, fieldWidth, 20, Component.literal("DonatePay User ID"));
        userIdBox.setMaxLength(16);
        userIdBox.setValue(String.valueOf(config.getUserId()));
        addWidget(userIdBox);

        // === DonationAlerts ===
        donationAlertsTitleY = donationAlertsY;
        donationAlertsEnabledButton = Button.builder(Component.literal(enabledLabel(config.donationalerts_enabled)), b -> toggleDonationAlertsEnabled())
                .bounds(donationAlertsX, donationAlertsY + 18, fieldWidth, 20)
                .build();
        addWidget(donationAlertsEnabledButton);

        donationAlertsTokenY = donationAlertsY + 18 + row;
        donationAlertsTokenBox = new EditBox(font, donationAlertsX, donationAlertsTokenY, fieldWidth - editButtonWidth - 6, 20, Component.literal("DonationAlerts token"));
        donationAlertsTokenBox.setMaxLength(2048);
        donationAlertsTokenBox.setValue(obfuscateToken(config.getDonationAlertsAccessToken()));
        donationAlertsTokenBox.setEditable(false);
        addWidget(donationAlertsTokenBox);

        toggleDonationAlertsTokenButton = Button.builder(Component.literal("Edit"), b -> toggleDonationAlertsTokenVisibility())
                .bounds(donationAlertsX + fieldWidth - editButtonWidth, donationAlertsTokenY, editButtonWidth, 20)
                .build();
        addWidget(toggleDonationAlertsTokenButton);

        donationAlertsUserY = donationAlertsTokenY + row;
        donationAlertsUserIdBox = new EditBox(font, donationAlertsX, donationAlertsUserY, fieldWidth, 20, Component.literal("DonationAlerts User ID"));
        donationAlertsUserIdBox.setMaxLength(16);
        donationAlertsUserIdBox.setValue(config.getDonationAlertsUserId() == 19114 ? "0" : String.valueOf(config.getDonationAlertsUserId()));
        addWidget(donationAlertsUserIdBox);

        donationAlertsChannelsY = donationAlertsUserY + row;
        donationAlertsChannelsBox = new EditBox(font, donationAlertsX, donationAlertsChannelsY, fieldWidth, 20, Component.literal("DonationAlerts channels"));
        donationAlertsChannelsBox.setMaxLength(64);
        donationAlertsChannelsBox.setValue(String.join(" ", config.getDonationAlertsChannels()));
        addWidget(donationAlertsChannelsBox);

        // === Connection status (под полями) ===
        int formBottom = twoColumns ? Math.max(donatePayUserY, donationAlertsChannelsY) + 34 : donationAlertsChannelsY + 34;
        saveButton = Button.builder(Component.literal("Save and Restart"), b -> saveConfig())
                .bounds(cx - 90, formBottom, 180, 20).build();
        addWidget(saveButton);

        connectionY = formBottom + 28;

        // === Start/Stop/Restart ===
        int controlsY = connectionY + 46;
        int bw = 90;
        startButton = Button.builder(Component.literal("Start"), b -> handleStart())
                .bounds(cx - bw - 60, controlsY, bw, 20).build();
        stopButton = Button.builder(Component.literal("Stop"), b -> handleStop())
                .bounds(cx - bw / 2, controlsY, bw, 20).build();
        restartButton = Button.builder(Component.literal("Restart"), b -> handleRestart())
                .bounds(cx + bw / 2 + 20, controlsY, bw, 20).build();
        addWidget(startButton);
        addWidget(stopButton);
        addWidget(restartButton);

        updateConnectionStatus();
    }

    @Override
    public void tick() {
        super.tick();
        updateConnectionStatus();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, width, height, 0xAA000000);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        gfx.drawCenteredString(font, "DonatePay", tokenBox.getX() + 120, donatePayTitleY, 0xFFFFD166);
        gfx.drawCenteredString(font, "DonationAlerts", donationAlertsTokenBox.getX() + 120, donationAlertsTitleY, 0xFFFFD166);
        drawFieldLabel(gfx, font, "Token", labelX, donatePayTokenY + 6);
        drawFieldLabel(gfx, font, "User ID", labelX, donatePayUserY + 6);

        int daLabelX = width >= 760 ? donationAlertsTokenBox.getX() - 88 : labelX;
        drawFieldLabel(gfx, font, "Token", daLabelX, donationAlertsTokenY + 6);
        drawFieldLabel(gfx, font, "User ID (0=auto)", daLabelX, donationAlertsUserY + 6);
        drawFieldLabel(gfx, font, "Channels", daLabelX, donationAlertsChannelsY + 6);

        gfx.drawCenteredString(font, "DonatePay: " + connectionStatus, centerX(), connectionY, connectionColor);
        gfx.drawCenteredString(font, "DonationAlerts: " + donationAlertsStatus + donationAlertsDetail(), centerX(), connectionY + 12, donationAlertsColor);
        gfx.drawCenteredString(font, donationAlertsLastEvent(), centerX(), connectionY + 24, 0xFFBFC7D5);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private void updateConnectionStatus() {
        var provider = DonateIntegrate.getDonateProvider();

        if (provider == null) {
            connectionStatus = "Disconnected";
            connectionColor = 0xFFFF5555;
        } else if (provider.isConnected()) {
            connectionStatus = "Connected";
            connectionColor = 0xFF00FF00;
        } else {
            connectionStatus = "Disconnected";
            connectionColor = 0xFFFF5555;
        }

        var donationAlertsProvider = DonateIntegrate.getDonationAlertsProvider();
        if (donationAlertsProvider == null) {
            donationAlertsStatus = "Disconnected";
            donationAlertsColor = 0xFFFF5555;
        } else if (donationAlertsProvider.isConnected()) {
            donationAlertsStatus = "Connected";
            donationAlertsColor = 0xFF00FF00;
        } else {
            donationAlertsStatus = donationAlertsProvider.getStatusText();
            donationAlertsColor = 0xFFFFAA00;
        }
    }

    private void handleStart() {
        connectionStatus = "Connecting...";
        connectionColor = 0xFFFFAA00; // жёлтый
        donationAlertsStatus = "Connecting...";
        donationAlertsColor = 0xFFFFAA00;
        DonateIntegrate.sendClientMessage("§eConnecting...");

        DonateIntegrate.startConnection();

        // через 1 секунду проверяем подключение ещё раз
        scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                1, TimeUnit.SECONDS);
    }

    private void handleStop() {
        DonateIntegrate.stopConnection();
        DonateIntegrate.sendClientMessage("§cConnection stopped.");
        updateConnectionStatus();
    }

    private void handleRestart() {
        connectionStatus = "Connecting...";
        connectionColor = 0xFFFFAA00;
        donationAlertsStatus = "Connecting...";
        donationAlertsColor = 0xFFFFAA00;
        DonateIntegrate.sendClientMessage("§eReconnecting...");

        DonateIntegrate.restartConnection();

        scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                1, TimeUnit.SECONDS);
    }

    private void toggleTokenVisibility() {
        tokenVisible = !tokenVisible;
        if (tokenVisible) {
            tokenBox.setEditable(true);
            tokenBox.setValue(config.getToken());
            tokenBox.setCursorPosition(tokenBox.getValue().length());
            toggleTokenButton.setMessage(Component.literal("Hide"));
        } else {
            tokenBox.setEditable(false);
            tokenBox.setValue(obfuscateToken(config.getToken()));
            toggleTokenButton.setMessage(Component.literal("Edit"));
        }
    }

    private void toggleDonationAlertsTokenVisibility() {
        donationAlertsTokenVisible = !donationAlertsTokenVisible;
        if (donationAlertsTokenVisible) {
            donationAlertsTokenBox.setEditable(true);
            donationAlertsTokenBox.setValue(config.getDonationAlertsAccessToken());
            donationAlertsTokenBox.setCursorPosition(donationAlertsTokenBox.getValue().length());
            toggleDonationAlertsTokenButton.setMessage(Component.literal("Hide"));
        } else {
            donationAlertsTokenBox.setEditable(false);
            donationAlertsTokenBox.setValue(obfuscateToken(config.getDonationAlertsAccessToken()));
            toggleDonationAlertsTokenButton.setMessage(Component.literal("Edit"));
        }
    }

    private void toggleDonatePayEnabled() {
        config.donatepay_enabled = !config.donatepay_enabled;
        donatePayEnabledButton.setMessage(Component.literal(enabledLabel(config.donatepay_enabled)));
    }

    private void toggleDonationAlertsEnabled() {
        config.donationalerts_enabled = !config.donationalerts_enabled;
        donationAlertsEnabledButton.setMessage(Component.literal(enabledLabel(config.donationalerts_enabled)));
    }

    private void saveConfig() {
        String tokenInput = tokenVisible ? tokenBox.getValue().trim() : config.getToken();
        String userStr = userIdBox.getValue().trim();
        String daTokenInput = donationAlertsTokenVisible ? donationAlertsTokenBox.getValue().trim() : config.getDonationAlertsAccessToken();
        String daUserStr = donationAlertsUserIdBox.getValue().trim();
        String daChannels = normalizeChannels(donationAlertsChannelsBox.getValue());

        if (config.donatepay_enabled && (tokenInput == null || tokenInput.isEmpty())) {
            DonateIntegrate.sendClientMessage("§cDonatePay token cannot be empty while DonatePay is enabled!");
            return;
        }

        if (config.donationalerts_enabled && (daTokenInput == null || daTokenInput.isEmpty() || daTokenInput.startsWith("YOUR_"))) {
            DonateIntegrate.sendClientMessage("§cDonationAlerts token cannot be empty while DonationAlerts is enabled!");
            return;
        }

        if (config.donationalerts_enabled && !looksLikeOAuthAccessToken(daTokenInput)) {
            DonateIntegrate.sendClientMessage("§cDonationAlerts needs OAuth access_token, not API key/client secret.");
            return;
        }

        try {
            int userId = Integer.parseInt(userStr);
            int daUserId = Integer.parseInt(daUserStr);
            if (config.donationalerts_enabled && daUserId == 19114) {
                DonateIntegrate.sendClientMessage("§eDonationAlerts User ID looks like app ID. Use 0 for auto-detect unless you know the real DA user id.");
                return;
            }

            DonateIntegrate.saveToJsonConfig("donatepay_enabled", config.donatepay_enabled);
            DonateIntegrate.saveToJsonConfig("token", tokenInput);
            DonateIntegrate.saveToJsonConfig("user_id", userId);
            DonateIntegrate.saveToJsonConfig("donationalerts_enabled", config.donationalerts_enabled);
            DonateIntegrate.saveToJsonConfig("donationalerts_access_token", daTokenInput);
            DonateIntegrate.saveToJsonConfig("donationalerts_user_id", daUserId);
            DonateIntegrate.saveToJsonConfig("donationalerts_channels", channelsJson(daChannels));

            config.token = tokenInput;
            config.user_id = userId;
            config.donationalerts_access_token = daTokenInput;
            config.donationalerts_user_id = daUserId;
            config.donationalerts_channels = Arrays.asList(daChannels.split(" "));

            if (tokenVisible) toggleTokenVisibility();
            if (donationAlertsTokenVisible) toggleDonationAlertsTokenVisibility();

            DonateIntegrate.sendClientMessage("§aConfig saved and connection restarted!");
            connectionStatus = "Connecting...";
            connectionColor = 0xFFFFAA00;
            donationAlertsStatus = "Connecting...";
            donationAlertsColor = 0xFFFFAA00;
            DonateIntegrate.restartConnection();

            scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                    1, TimeUnit.SECONDS);

        } catch (NumberFormatException e) {
            DonateIntegrate.sendClientMessage("§cInvalid User ID format! Use numbers only. DonationAlerts user ID can be 0.");
        }
    }

    private String enabledLabel(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private void drawFieldLabel(GuiGraphics gfx, Font font, String text, int x, int y) {
        gfx.drawString(font, text, x, y, 0xFFBFC7D5, false);
    }

    private String donationAlertsDetail() {
        var provider = DonateIntegrate.getDonationAlertsProvider();
        if (provider == null) return "";
        String status = provider.getStatusText();
        if (status != null && status.equals(donationAlertsStatus)) return "";
        return status == null || status.isBlank() ? "" : " (" + status + ")";
    }

    private String donationAlertsLastEvent() {
        var provider = DonateIntegrate.getDonationAlertsProvider();
        if (provider == null) return "DonationAlerts last event: -";
        return "DonationAlerts last event: " + provider.getLastEventText();
    }

    private String obfuscateToken(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.startsWith("YOUR_")) return token;
        if (token.length() <= 4) return "*".repeat(token.length());
        return token.substring(0, 2) + "*".repeat(token.length() - 4) + token.substring(token.length() - 2);
    }

    private boolean looksLikeOAuthAccessToken(String token) {
        if (token == null) return false;
        String value = token.trim();
        return value.startsWith("eyJ") && value.chars().filter(ch -> ch == '.').count() == 2;
    }

    private String normalizeChannels(String value) {
        StringBuilder result = new StringBuilder();
        for (String raw : value.split("[,\\s]+")) {
            String channel = raw.trim().toLowerCase(Locale.ROOT);
            if (channel.equals("donation") || channel.equals("goal") || channel.equals("poll")) {
                if (result.indexOf(channel) < 0) {
                    if (!result.isEmpty()) result.append(' ');
                    result.append(channel);
                }
            }
        }
        return result.isEmpty() ? "donation" : result.toString();
    }

    private com.google.gson.JsonArray channelsJson(String value) {
        com.google.gson.JsonArray channels = new com.google.gson.JsonArray();
        for (String channel : value.split(" ")) {
            channels.add(channel);
        }
        return channels;
    }
}
