package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.donation.DonatePayUserClient;
import com.bogdan3000.dintegrate.donation.DonationAlertsOAuthClient;
import com.bogdan3000.dintegrate.donation.DonationAlertsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TabConfig extends TabBase {
    private static final int PANEL_BG = 0xCC11151C;
    private static final int PANEL_BORDER = 0xFF2F3846;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF96A0AF;
    private static final int ACCENT = 0xFFFFD166;
    private static final int OK = 0xFF41D17D;
    private static final int WARN = 0xFFFFAA33;
    private static final int BAD = 0xFFFF5B6A;

    private final Config config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private MaskedTokenBox donatePayTokenBox;
    private EditBox donatePayUserIdBox;
    private Button donatePayGetIdButton;
    private Button donatePayReconnectButton;
    private Button donatePayStopButton;

    private Button donationAlertsAuthorizeButton;
    private Button donationAlertsResetButton;
    private Button donationAlertsReconnectButton;
    private Button donationAlertsStopButton;

    private boolean donatePayGetIdInProgress;
    private boolean donationAlertsOAuthInProgress;
    private long donatePayGetIdCooldownUntil;
    private String donatePayNote = "";
    private String donationAlertsNote = "";
    private CompletableFuture<DonationAlertsOAuthClient.Result> donationAlertsOAuthFuture;

    public TabConfig(Config config) {
        super(I18n.get("dintegrate.gui.tab.config"));
        this.config = config;
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        Font font = mc.font;
        Layout layout = layout(width, height);

        buildDonatePay(font, layout.leftX(), layout.top(), layout.panelW(), layout.panelH());
        buildDonationAlerts(layout.rightX(), layout.donationAlertsY(), layout.panelW(), layout.panelH());
    }

    private void buildDonatePay(Font font, int x, int y, int w, int h) {
        int pad = panelPad(w);
        int fieldX = x + pad;
        int fieldW = w - pad * 2;
        int labelY = y + 50;
        int gap = compact(w) ? 6 : 8;
        int getIdW = compact(w) ? 56 : 80;
        int userFieldW = Math.max(72, fieldW - getIdW - gap);

        donatePayTokenBox = new MaskedTokenBox(font, fieldX, labelY + 13, fieldW, 20, Component.translatable("dintegrate.gui.donatepay.token"));
        donatePayTokenBox.setMaxLength(2048);
        donatePayTokenBox.setActualValue(config.getToken());
        donatePayTokenBox.setActualResponder(this::onDonatePayTokenChanged);
        donatePayTokenBox.setRevealRequest(this::confirmDonatePayTokenReveal);
        addWidget(donatePayTokenBox);

        donatePayUserIdBox = new EditBox(font, fieldX, labelY + 58, userFieldW, 20, Component.translatable("dintegrate.gui.donatepay.user_id"));
        donatePayUserIdBox.setMaxLength(16);
        donatePayUserIdBox.setValue(String.valueOf(config.getUserId()));
        donatePayUserIdBox.setResponder(this::onDonatePayUserIdChanged);
        addWidget(donatePayUserIdBox);

        donatePayGetIdButton = Button.builder(Component.literal(donatePayGetIdLabel()), b -> loadDonatePayUserId())
                .bounds(fieldX + fieldW - getIdW, labelY + 58, getIdW, 20)
                .build();
        addWidget(donatePayGetIdButton);

        int actionY = y + h - 46;
        int reconnectW = Math.min(104, Math.max(82, (fieldW - gap) / 2));
        int stopW = Math.min(76, fieldW - reconnectW - gap);
        donatePayReconnectButton = Button.builder(Component.translatable("dintegrate.gui.reconnect"), b -> reconnectDonatePay())
                .bounds(fieldX, actionY, reconnectW, 22)
                .build();
        donatePayStopButton = Button.builder(Component.translatable("dintegrate.gui.stop"), b -> stopDonatePay())
                .bounds(fieldX + reconnectW + gap, actionY, stopW, 22)
                .build();
        addWidget(donatePayReconnectButton);
        addWidget(donatePayStopButton);
    }

    private void buildDonationAlerts(int x, int y, int w, int h) {
        int pad = panelPad(w);
        int fieldX = x + pad;
        int fieldW = w - pad * 2;
        int gap = compact(w) ? 6 : 10;
        int authW = Math.max(72, (fieldW - gap) / 2);
        int resetW = fieldW - authW - gap;
        donationAlertsAuthorizeButton = Button.builder(Component.literal(donationAlertsLoginButtonLabel()), b -> startDonationAlertsOAuth())
                .bounds(fieldX, y + 63, authW, 22)
                .build();
        donationAlertsResetButton = Button.builder(Component.translatable("dintegrate.gui.reset_login"), b -> resetDonationAlertsLogin())
                .bounds(fieldX + authW + gap, y + 63, resetW, 22)
                .build();
        addWidget(donationAlertsAuthorizeButton);
        addWidget(donationAlertsResetButton);

        int actionY = y + h - 46;
        int reconnectW = Math.min(104, Math.max(82, (fieldW - gap) / 2));
        int stopW = Math.min(76, fieldW - reconnectW - gap);
        donationAlertsReconnectButton = Button.builder(Component.translatable("dintegrate.gui.reconnect"), b -> reconnectDonationAlerts())
                .bounds(fieldX, actionY, reconnectW, 22)
                .build();
        donationAlertsStopButton = Button.builder(Component.translatable("dintegrate.gui.stop"), b -> stopDonationAlerts())
                .bounds(fieldX + reconnectW + gap, actionY, stopW, 22)
                .build();
        addWidget(donationAlertsReconnectButton);
        addWidget(donationAlertsStopButton);

        updateDonationAlertsOAuthButtons();
    }

    @Override
    public void tick() {
        super.tick();
        updateDonatePayGetIdButton();
        updateDonationAlertsOAuthButtons();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, width, height, 0xEE0B0E13);
        Font font = Minecraft.getInstance().font;
        Layout layout = layout(width, height);

        drawPanel(gfx, layout.leftX(), layout.top(), layout.panelW(), layout.panelH());
        drawPanel(gfx, layout.rightX(), layout.donationAlertsY(), layout.panelW(), layout.panelH());
        renderDonatePay(gfx, font, layout.leftX(), layout.top(), layout.panelW(), layout.panelH());
        renderDonationAlerts(gfx, font, layout.rightX(), layout.donationAlertsY(), layout.panelW(), layout.panelH());

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private void renderDonatePay(GuiGraphics gfx, Font font, int x, int y, int w, int h) {
        DonatePayProvider provider = DonateIntegrate.getDonateProvider();
        String status = provider == null ? tr("dintegrate.status.disconnected") : translateStatus(provider.getStatusText());
        int color = provider != null && provider.isConnected() ? OK : (provider == null ? BAD : WARN);
        int pad = panelPad(w);

        gfx.drawString(font, "DonatePay", x + pad, y + 16, TEXT, false);
        drawResponsiveStatus(gfx, font, x, y, w, status, color);
        gfx.drawString(font, tr("dintegrate.gui.donatepay.token"), x + pad, y + 50, MUTED, false);
        gfx.drawString(font, tr("dintegrate.gui.donatepay.user_id"), x + pad, y + 95, MUTED, false);
        drawClipped(gfx, font, donatePayAccountLine(), x + pad, y + 134, w - pad * 2, MUTED);
        if (!donatePayNote.isBlank()) {
            drawClipped(gfx, font, donatePayNote, x + pad, y + h - 68, w - pad * 2, ACCENT);
        }
    }

    private void renderDonationAlerts(GuiGraphics gfx, Font font, int x, int y, int w, int h) {
        DonationAlertsProvider provider = DonateIntegrate.getDonationAlertsProvider();
        String status = provider == null ? tr("dintegrate.status.disconnected") : translateStatus(provider.getStatusText());
        int color = provider != null && provider.isConnected() ? OK : (provider == null ? BAD : WARN);
        int pad = panelPad(w);

        gfx.drawString(font, "DonationAlerts", x + pad, y + 16, TEXT, false);
        drawResponsiveStatus(gfx, font, x, y, w, status, color);
        gfx.drawString(font, tr("dintegrate.gui.donationalerts.browser_login"), x + pad, y + 50, MUTED, false);
        drawClipped(gfx, font, donationAlertsAccountLine(), x + pad, y + 100, w - pad * 2, MUTED);
        gfx.drawString(font, tr("dintegrate.gui.donationalerts.channel_donations"), x + pad, y + 120, MUTED, false);
        if (!donationAlertsNote.isBlank()) {
            drawClipped(gfx, font, donationAlertsNote, x + pad, y + h - 68, w - pad * 2, ACCENT);
        }
    }

    private void drawPanel(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, PANEL_BG);
        gfx.hLine(x, x + w, y, PANEL_BORDER);
        gfx.hLine(x, x + w, y + h, PANEL_BORDER);
        gfx.vLine(x, y, y + h, PANEL_BORDER);
        gfx.vLine(x + w, y, y + h, PANEL_BORDER);
    }

    private void drawResponsiveStatus(GuiGraphics gfx, Font font, int x, int y, int w, String text, int color) {
        int pad = panelPad(w);
        String status = "● " + text;
        if (compact(w)) {
            drawClipped(gfx, font, status, x + pad, y + 30, w - pad * 2, color);
            return;
        }

        int maxW = Math.max(70, w - 150);
        status = clip(font, status, maxW);
        gfx.drawString(font, status, x + w - pad - font.width(status), y + 16, color, false);
    }

    private void drawClipped(GuiGraphics gfx, Font font, String text, int x, int y, int maxW, int color) {
        gfx.drawString(font, clip(font, text, maxW), x, y, color, false);
    }

    private String clip(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        if (maxW <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, maxW - font.width("...")) + "...";
    }

    private Layout layout(int width, int height) {
        int margin = width < 560 ? 10 : 24;
        int gap = width < 620 ? 10 : 24;
        int minSidePanelW = 190;
        boolean twoColumns = width >= margin * 2 + gap + minSidePanelW * 2;
        int panelW = twoColumns
                ? Math.min(330, (width - margin * 2 - gap) / 2)
                : Math.min(330, Math.max(180, width - margin * 2));
        int panelH = 220;
        int top = Math.max(36, Math.min(52, height / 10));
        int totalW = twoColumns ? panelW * 2 + gap : panelW;
        int leftX = Math.max(margin, (width - totalW) / 2);
        int rightX = twoColumns ? leftX + panelW + gap : leftX;
        int donationAlertsY = twoColumns ? top : top + panelH + 14;
        return new Layout(top, panelW, panelH, leftX, rightX, donationAlertsY);
    }

    private int panelPad(int w) {
        return compact(w) ? 12 : 18;
    }

    private boolean compact(int w) {
        return w < 260;
    }

    private record Layout(int top, int panelW, int panelH, int leftX, int rightX, int donationAlertsY) {}

    private void onDonatePayTokenChanged(String token) {
        String value = token == null ? "" : token.trim();
        if (value.equals(config.getToken())) return;

        config.token = value;
        config.user_id = 0;
        config.donatepay_user_name = "";
        if (donatePayUserIdBox != null) donatePayUserIdBox.setValue("0");
        donatePayNote = tr("dintegrate.gui.note.token_saved_get_id");

        DonateIntegrate.saveToJsonConfig("token", value);
        DonateIntegrate.saveToJsonConfig("user_id", 0);
        DonateIntegrate.saveToJsonConfig("donatepay_user_name", "");
    }

    private void confirmDonatePayTokenReveal() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            Minecraft.getInstance().setScreen(new DonateIntegrateScreen());
            if (confirmed) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof DonateIntegrateScreen screen) {
                        screen.revealDonatePayToken();
                    }
                });
            }
        },
                Component.translatable("dintegrate.gui.confirm.show_token.title"),
                Component.translatable("dintegrate.gui.confirm.show_token.body"),
                Component.translatable("dintegrate.gui.confirm.show_token.yes"),
                Component.translatable("dintegrate.gui.confirm.show_token.no")));
    }

    public void revealDonatePayToken() {
        if (donatePayTokenBox != null) {
            donatePayTokenBox.setRevealed(true);
            donatePayTokenBox.setFocused(true);
        }
    }

    private void onDonatePayUserIdChanged(String value) {
        int userId = parseUserId(value);
        if (userId == config.getUserId()) return;

        config.user_id = userId;
        if (userId == 0) config.donatepay_user_name = "";
        donatePayNote = userId > 0 ? tr("dintegrate.gui.note.user_id_saved") : tr("dintegrate.gui.note.user_id_cleared");
        DonateIntegrate.saveToJsonConfig("user_id", userId);
        if (userId == 0) DonateIntegrate.saveToJsonConfig("donatepay_user_name", "");
    }

    private void loadDonatePayUserId() {
        if (donatePayGetIdInProgress) return;
        long cooldownLeft = donatePayGetIdCooldownMs();
        if (cooldownLeft > 0) return;

        String token = donatePayTokenBox.getActualValue();
        if (token.isBlank() || token.startsWith("YOUR_")) {
            donatePayNote = tr("dintegrate.gui.note.paste_donatepay_token");
            return;
        }

        donatePayGetIdInProgress = true;
        donatePayNote = tr("dintegrate.gui.note.loading_user_id");
        updateDonatePayGetIdButton();

        new DonatePayUserClient().loadUser(token).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            donatePayGetIdInProgress = false;
            if (error != null) {
                donatePayNote = rootMessage(error);
                donatePayGetIdCooldownUntil = System.currentTimeMillis() + cooldownAfterDonatePayError(error);
                updateDonatePayGetIdButton();
                return;
            }
            applyDonatePayUser(result);
        }));
    }

    private void applyDonatePayUser(DonatePayUserClient.Result result) {
        config.donatepay_enabled = true;
        config.token = result.accessToken();
        config.user_id = result.userId();
        config.donatepay_user_name = result.userName();

        donatePayTokenBox.setActualValue(result.accessToken());
        donatePayUserIdBox.setValue(String.valueOf(result.userId()));
        donatePayNote = tr("dintegrate.gui.note.user_id_saved");
        donatePayGetIdCooldownUntil = 0L;

        DonateIntegrate.saveToJsonConfig("donatepay_enabled", true);
        DonateIntegrate.saveToJsonConfig("token", result.accessToken());
        DonateIntegrate.saveToJsonConfig("user_id", result.userId());
        DonateIntegrate.saveToJsonConfig("donatepay_user_name", result.userName());
    }

    private void reconnectDonatePay() {
        if (config.getToken().isBlank() || config.getToken().startsWith("YOUR_")) {
            donatePayNote = tr("dintegrate.gui.note.paste_token");
            return;
        }
        if (config.getUserId() <= 0) {
            donatePayNote = tr("dintegrate.gui.note.set_user_id_or_get");
            return;
        }
        config.donatepay_enabled = true;
        DonateIntegrate.saveToJsonConfig("donatepay_enabled", true);
        DonateIntegrate.restartDonatePayConnection();
        donatePayNote = tr("dintegrate.gui.note.reconnect_requested");
    }

    private void stopDonatePay() {
        DonateIntegrate.stopDonatePayConnection();
        donatePayNote = tr("dintegrate.gui.note.stopped");
    }

    private void startDonationAlertsOAuth() {
        if (donationAlertsOAuthInProgress || hasDonationAlertsLogin()) return;

        donationAlertsOAuthInProgress = true;
        donationAlertsNote = tr("dintegrate.gui.note.waiting_browser_login");
        updateDonationAlertsOAuthButtons();

        DonationAlertsOAuthClient client = new DonationAlertsOAuthClient(config.getDonationAlertsOAuthBaseUrl());
        donationAlertsOAuthFuture = client.authorize();
        donationAlertsOAuthFuture.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            donationAlertsOAuthInProgress = false;
            if (error != null) {
                donationAlertsNote = rootMessage(error);
                updateDonationAlertsOAuthButtons();
                return;
            }
            applyDonationAlertsOAuthResult(result);
        }));
    }

    private void applyDonationAlertsOAuthResult(DonationAlertsOAuthClient.Result result) {
        config.donationalerts_enabled = true;
        config.donationalerts_access_token = result.accessToken();
        config.donationalerts_refresh_token = result.refreshToken();
        config.donationalerts_user_id = result.userId();
        config.donationalerts_user_name = result.userName();
        config.donationalerts_channels = java.util.List.of("donation");

        DonateIntegrate.saveToJsonConfig("donationalerts_enabled", true);
        DonateIntegrate.saveToJsonConfig("donationalerts_access_token", result.accessToken());
        DonateIntegrate.saveToJsonConfig("donationalerts_refresh_token", result.refreshToken());
        DonateIntegrate.saveToJsonConfig("donationalerts_user_id", result.userId());
        DonateIntegrate.saveToJsonConfig("donationalerts_user_name", result.userName());
        DonateIntegrate.saveToJsonConfig("donationalerts_channels", channelsJson("donation"));

        donationAlertsNote = tr("dintegrate.gui.note.login_saved_reconnect");
        updateDonationAlertsOAuthButtons();
    }

    private void resetDonationAlertsLogin() {
        config.donationalerts_enabled = false;
        config.donationalerts_access_token = "YOUR_DONATIONALERTS_ACCESS_TOKEN";
        config.donationalerts_refresh_token = "";
        config.donationalerts_user_id = 0;
        config.donationalerts_user_name = "";

        DonateIntegrate.saveToJsonConfig("donationalerts_enabled", false);
        DonateIntegrate.saveToJsonConfig("donationalerts_access_token", "YOUR_DONATIONALERTS_ACCESS_TOKEN");
        DonateIntegrate.saveToJsonConfig("donationalerts_refresh_token", "");
        DonateIntegrate.saveToJsonConfig("donationalerts_user_id", 0);
        DonateIntegrate.saveToJsonConfig("donationalerts_user_name", "");

        DonateIntegrate.stopDonationAlertsConnection();
        donationAlertsNote = tr("dintegrate.gui.note.login_reset");
        updateDonationAlertsOAuthButtons();
    }

    private void reconnectDonationAlerts() {
        if (!hasDonationAlertsLogin()) {
            donationAlertsNote = tr("dintegrate.gui.note.authorize_first");
            return;
        }
        config.donationalerts_enabled = true;
        config.donationalerts_channels = java.util.List.of("donation");
        DonateIntegrate.saveToJsonConfig("donationalerts_enabled", true);
        DonateIntegrate.saveToJsonConfig("donationalerts_channels", channelsJson("donation"));
        DonateIntegrate.restartDonationAlertsConnection();
        donationAlertsNote = tr("dintegrate.gui.note.reconnect_requested");
    }

    private void stopDonationAlerts() {
        DonateIntegrate.stopDonationAlertsConnection();
        donationAlertsNote = tr("dintegrate.gui.note.stopped");
    }

    private void updateDonatePayGetIdButton() {
        if (donatePayGetIdButton == null) return;
        donatePayGetIdButton.setMessage(Component.literal(donatePayGetIdLabel()));
        donatePayGetIdButton.active = !donatePayGetIdInProgress && donatePayGetIdCooldownMs() <= 0;
    }

    private String donatePayGetIdLabel() {
        long cooldownLeft = donatePayGetIdCooldownMs();
        if (!donatePayGetIdInProgress && cooldownLeft > 0) {
            return tr("dintegrate.gui.wait_seconds", Math.max(1, cooldownLeft / 1000));
        }
        return donatePayGetIdInProgress ? tr("dintegrate.gui.loading") : tr("dintegrate.gui.get_id");
    }

    private void updateDonationAlertsOAuthButtons() {
        if (donationAlertsAuthorizeButton != null) {
            donationAlertsAuthorizeButton.setMessage(Component.literal(donationAlertsLoginButtonLabel()));
            donationAlertsAuthorizeButton.active = !donationAlertsOAuthInProgress && !hasDonationAlertsLogin();
        }
        if (donationAlertsResetButton != null) {
            donationAlertsResetButton.active = !donationAlertsOAuthInProgress && hasDonationAlertsLogin();
        }
    }

    private String donationAlertsLoginButtonLabel() {
        if (donationAlertsOAuthInProgress) return tr("dintegrate.gui.authorizing");
        return hasDonationAlertsLogin() ? tr("dintegrate.gui.authorized") : tr("dintegrate.gui.authorize");
    }

    private String donatePayAccountLine() {
        if (config.getUserId() <= 0) return tr("dintegrate.gui.no_user_id");
        String name = config.getDonatePayUserName();
        return (name == null || name.isBlank()) ? tr("dintegrate.gui.account_connected") : tr("dintegrate.gui.account_name", name);
    }

    private String donationAlertsAccountLine() {
        if (!hasDonationAlertsLogin()) return tr("dintegrate.gui.not_authorized");
        String name = config.getDonationAlertsUserName();
        return (name == null || name.isBlank())
                ? tr("dintegrate.gui.account_id", config.getDonationAlertsUserId())
                : tr("dintegrate.gui.account_name_id", name, config.getDonationAlertsUserId());
    }

    private String translateStatus(String status) {
        if (status == null || status.isBlank()) return tr("dintegrate.status.disconnected");
        return switch (status) {
            case "Disconnected" -> tr("dintegrate.status.disconnected");
            case "Connected", "Subscribed" -> tr("dintegrate.status.connected");
            case "Connecting" -> tr("dintegrate.status.connecting");
            case "Cooldown" -> tr("dintegrate.status.cooldown");
            case "Missing user id" -> tr("dintegrate.status.missing_user_id");
            case "Missing token" -> tr("dintegrate.status.missing_token");
            case "Requesting token" -> tr("dintegrate.status.requesting_token");
            case "Opening WebSocket" -> tr("dintegrate.status.opening_websocket");
            case "WebSocket opened" -> tr("dintegrate.status.websocket_opened");
            case "Handshake sent" -> tr("dintegrate.status.handshake_sent");
            case "Subscribing" -> tr("dintegrate.status.subscribing");
            case "Loading /user/oauth" -> tr("dintegrate.status.loading_user_oauth");
            case "Token check failed", "Token request failed" -> tr("dintegrate.status.token_failed");
            case "Unknown user id" -> tr("dintegrate.status.unknown_user_id");
            case "Subscribe failed", "Subscribe send failed", "Subscribe token failed" -> tr("dintegrate.status.subscribe_failed");
            case "WebSocket failed", "WebSocket error" -> tr("dintegrate.status.websocket_failed");
            default -> status;
        };
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    private boolean hasDonationAlertsLogin() {
        String token = config.getDonationAlertsAccessToken();
        return token != null && !token.isBlank() && !token.startsWith("YOUR_") && config.getDonationAlertsUserId() > 0;
    }

    private long donatePayGetIdCooldownMs() {
        return Math.max(0L, donatePayGetIdCooldownUntil - System.currentTimeMillis());
    }

    private long cooldownAfterDonatePayError(Throwable error) {
        Throwable current = rootCause(error);
        if (current instanceof DonatePayUserClient.UserLoadException userError && userError.statusCode() == 429) {
            return 60_000L;
        }
        return 15_000L;
    }

    private int parseUserId(String value) {
        try {
            if (value == null || value.isBlank()) return 0;
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return config.getUserId();
        }
    }

    private com.google.gson.JsonArray channelsJson(String value) {
        com.google.gson.JsonArray channels = new com.google.gson.JsonArray();
        for (String channel : value.split(" ")) {
            channels.add(channel);
        }
        return channels;
    }

    private String rootMessage(Throwable error) {
        Throwable current = rootCause(error);
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
