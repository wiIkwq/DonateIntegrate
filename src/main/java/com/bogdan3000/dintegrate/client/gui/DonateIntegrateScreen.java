package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Главный экран DonateIntegrate с вкладками.
 * Открывается клавишей (см. KeybindHandler, по умолчанию G).
 */
public class DonateIntegrateScreen extends Screen {

    private TabBase currentTab;

    private Button btnConfig;
    private Button btnCommands;
    private Button btnInfo;
    private Button btnMisc;

    public DonateIntegrateScreen() {
        super(Component.literal("DonateIntegrate"));
    }

    @Override
    protected void init() {
        super.init();

        this.renderables.clear();
        this.children().clear();

        int y = 8;
        int x = this.width / 2 - 170;

        // === CONFIG ===
        btnConfig = Button.builder(Component.translatable("dintegrate.gui.tab.config"), b ->
                        switchTab(new TabConfig(DonateIntegrate.getConfig())))
                .bounds(x, y, 80, 20)
                .build();
        addRenderableWidget(btnConfig);

        // === COMMANDS ===
        btnCommands = Button.builder(Component.translatable("dintegrate.gui.tab.commands"), b ->
                        switchTab(new TabCommands(DonateIntegrate.getConfig())))
                .bounds(x + 90, y, 80, 20)
                .build();
        addRenderableWidget(btnCommands);

        // === INFO ===
        btnInfo = Button.builder(Component.translatable("dintegrate.gui.tab.info"), b ->
                        switchTab(new TabInfo()))
                .bounds(x + 180, y, 80, 20)
                .build();
        addRenderableWidget(btnInfo);

        // === TEST ===
        btnMisc = Button.builder(Component.translatable("dintegrate.gui.tab.test"), b ->
                        switchTab(new TabMisc()))
                .bounds(x + 270, y, 80, 20)
                .build();
        addRenderableWidget(btnMisc);

        // вкладка по умолчанию
        if (currentTab == null) {
            currentTab = new TabConfig(DonateIntegrate.getConfig());
        }
        currentTab.init(Minecraft.getInstance(), this.width, this.height);

        for (var w : currentTab.widgets) {
            this.addRenderableWidget(w);
        }
    }

    /** Переключение вкладки */
    private void switchTab(TabBase next) {
        if (currentTab != null) {
            this.renderables.removeAll(currentTab.widgets);
            this.children().removeAll(currentTab.widgets);
        }

        currentTab = next;
        currentTab.init(Minecraft.getInstance(), this.width, this.height);

        for (var w : currentTab.widgets) {
            this.addRenderableWidget(w);
        }
    }

    /** Управление видимостью кнопок вкладок */
    public void setTabButtonsVisible(boolean visible) {
        if (btnConfig != null) btnConfig.visible = visible;
        if (btnCommands != null) btnCommands.visible = visible;
        if (btnInfo != null) btnInfo.visible = visible;
        if (btnMisc != null) btnMisc.visible = visible;
    }

    /** Полная пересборка GUI при смене режима вкладки */
    public void rebuildForCurrentTab() {
        boolean tabsVisible = btnConfig != null && btnConfig.visible;

        this.renderables.clear();
        this.children().clear();

        if (btnConfig != null) { this.addRenderableWidget(btnConfig); btnConfig.visible = tabsVisible; }
        if (btnCommands != null) { this.addRenderableWidget(btnCommands); btnCommands.visible = tabsVisible; }
        if (btnInfo != null) { this.addRenderableWidget(btnInfo); btnInfo.visible = tabsVisible; }
        if (btnMisc != null) { this.addRenderableWidget(btnMisc); btnMisc.visible = tabsVisible; }

        if (currentTab != null) {
            for (var w : currentTab.widgets) {
                this.addRenderableWidget(w);
            }
        }
    }

    public void revealDonatePayToken() {
        if (currentTab instanceof TabConfig configTab) {
            configTab.revealDonatePayToken();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (currentTab != null) currentTab.tick();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, this.width, this.height, 0xAA000000);

        if (currentTab != null) {
            currentTab.render(gfx, mouseX, mouseY, partialTicks);
        }

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    // === добавляем поддержку скролла ===
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab != null && currentTab.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (currentTab != null) currentTab.onClose();
    }
}
