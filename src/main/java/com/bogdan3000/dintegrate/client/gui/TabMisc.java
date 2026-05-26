package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Test — тест донатов из GUI.
 * Список правил выводится сеткой (колонки) и поддерживает прокрутку.
 */
public class TabMisc extends TabBase {

    private final Config config;

    // кнопки правил
    private final List<Button> ruleButtons = new ArrayList<>();

    // layout/scroll
    private int baseY = 40;
    private int rowHeight = 24;
    private int cols = 2;           // пересчитывается в init()
    private int colWidth = 220;     // пересчитывается в init()
    private int colPad = 10;
    private double scrollOffset = 0;
    private int contentHeight = 0;

    public TabMisc() {
        super(I18n.get("dintegrate.gui.tab.test"));
        this.config = DonateIntegrate.getConfig();
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);

        // адаптивные колонки под ширину экрана
        int usable = Math.max(1, width - 40); // поля слева/справа
        cols = Math.max(1, Math.min(usable / 240, 3)); // 1..3 колонок
        if (cols == 0) cols = 1;
        colWidth = (usable - (colPad * (cols - 1))) / cols;
        if (colWidth < 160) { // fallback — лучше одна колонка, чем крошечные кнопки
            cols = 1;
            colWidth = usable;
        }

        buildButtons();
        updateLayout();
    }

    private void buildButtons() {
        // удалить старые кнопки из набора виджетов
        for (var b : ruleButtons) {
            this.widgets.remove(b);
        }
        ruleButtons.clear();

        // для каждого правила — кнопка
        int i = 0;
        for (var rule : config.rules) {
            String label = rule.amount + "₽ — " + (rule.mode != null ? rule.mode.toUpperCase() : "ALL")
                    + " (" + (rule.commands != null ? rule.commands.size() : 0) + " cmds)";
            final double amt = rule.amount;
            Button btn = Button.builder(Component.literal(label), b -> testDonation(amt))
                    .bounds(0, 0, colWidth, 20) // координаты выставим в updateLayout
                    .build();
            ruleButtons.add(btn);
            addWidget(btn);
            i++;
        }

        if (ruleButtons.isEmpty()) {
            Button noRules = Button.builder(Component.translatable("dintegrate.gui.test.no_rules"), b -> {})
                    .bounds(centerX() - 100, baseY + 10, 200, 20)
                    .build();
            ruleButtons.add(noRules);
            addWidget(noRules);
        }
    }

    private void updateLayout() {
        // сколько строк занимает список
        int rows = (int) Math.ceil(ruleButtons.size() / (double) cols);
        contentHeight = rows * rowHeight;

        int left = 20; // левый отступ от края экрана
        int visibleTop = baseY;
        int visibleBottom = height - 12;

        for (int i = 0; i < ruleButtons.size(); i++) {
            Button btn = ruleButtons.get(i);
            int row = i / cols;
            int col = i % cols;

            int x = left + col * (colWidth + colPad);
            int y = baseY + row * rowHeight - (int) scrollOffset;

            btn.setX(x);
            btn.setY(y);
            btn.setWidth(colWidth);

            // видимость по вертикальному окну
            boolean visible = (y + 20 >= visibleTop) && (y <= visibleBottom);
            btn.visible = visible;
        }

        // ограничиваем прокрутку
        int maxScroll = Math.max(0, contentHeight - (visibleBottom - visibleTop));
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void testDonation(double amount) {
        var rules = config.getRules();
        var rule = rules.get(amount);
        if (rule == null) {
            DonateIntegrate.sendClientMessage(Component.translatable("dintegrate.message.no_rule_amount", amount));
            return;
        }
        String name = "TestUser";
        String msg = "Test Message";
        DonateIntegrate.sendClientMessage(Component.translatable("dintegrate.message.simulating_donation", amount, rule.mode));
        new com.bogdan3000.dintegrate.logic.ActionHandler(config).execute(amount, name, msg);
    }

    @Override
    public void tick() {
        super.tick();
        // при изменении размера окна в рантайме — убедимся, что раскладка актуальна
        updateLayout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // delta > 0 — прокрутка вверх, < 0 — вниз
        // немного ускорим скролл
        scrollOffset -= delta * 20;  // delta положительное — сдвигаем вверх
        updateLayout();
        return true; // событие съедаем
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        // фон уже рисует экран; при желании можно подложить карту/рамки
        super.render(gfx, mouseX, mouseY, partialTicks);

        // индикатор прокрутки (тонкая полоса справа)
        int visibleTop = baseY;
        int visibleBottom = height - 12;
        int viewportH = Math.max(1, visibleBottom - visibleTop);
        if (contentHeight > viewportH) {
            int barH = Math.max(20, (int) (viewportH * (viewportH / (float) contentHeight)));
            int maxScroll = contentHeight - viewportH;
            int barY = visibleTop + (int) ((scrollOffset / (float) maxScroll) * (viewportH - barH));
            int barX = width - 8;
            gfx.fill(barX, visibleTop, barX + 3, visibleBottom, 0x33000000);
            gfx.fill(barX, barY, barX + 3, barY + barH, 0x99FFFFFF);
        }
    }
}
