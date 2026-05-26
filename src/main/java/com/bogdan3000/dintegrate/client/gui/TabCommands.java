package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.Config.DonationRule;
import com.bogdan3000.dintegrate.DonateIntegrate;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TabCommands extends TabBase {

    private final Config config;
    private boolean inEditMode = false;

    // список правил
    private final List<Button> ruleButtons = new ArrayList<>();
    private Button addRuleButton;

    // редактор правила
    private DonationRule currentRule;
    private EditBox amountBox;
    private CycleButton<String> modeDropdown;
    private EditBox randomNBox;
    private MultilineEditBox commandsBox;
    private Button saveButton, deleteButton, backButton;

    private final Minecraft mc = Minecraft.getInstance();
    private static final List<String> MODES = List.of("all", "random", "random_all", "randomN");

    // скролл списка
    private double scrollOffset = 0d;
    private double maxScroll = 0d;

    public TabCommands(Config config) {
        super(I18n.get("dintegrate.gui.tab.commands"));
        this.config = config;
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        if (inEditMode) {
            initEditor(width, height);
            setListVisible(false);
        } else {
            initScrollableList(width, height);
        }
        if (Minecraft.getInstance().screen instanceof DonateIntegrateScreen s) {
            s.setTabButtonsVisible(!inEditMode);
            s.rebuildForCurrentTab();
        }
    }

    /* ======================= СПИСОК ПРАВИЛ ======================= */

    private void initScrollableList(int width, int height) {
        this.widgets.clear();
        ruleButtons.clear();

        int cx = width / 2;
        int baseY = Math.max(70, height / 6);

        // кнопка "добавить" — фикс сверху
        addRuleButton = Button.builder(Component.translatable("dintegrate.gui.commands.add_rule"), b -> createNewRule())
                .bounds(cx - 100, baseY - 30, 200, 20).build();
        addWidget(addRuleButton);

        int colCount = width > 720 ? 2 : 1;     // на широком экране 2 колонки
        int colWidth = width / colCount - 60;
        int spacing = 30;

        int i = 0;
        for (DonationRule rule : config.rules) {
            int col = i % colCount;
            int row = i / colCount;
            int x = 40 + col * (colWidth + 40);
            int yPos = baseY + row * spacing;

            String title = rule.amount + "₽ → " + rule.commands.size() + " cmds [" + rule.mode + "]";
            Button btn = Button.builder(Component.literal(title), b -> openEditor(rule))
                    .bounds(x, yPos, colWidth, 20).build();
            ruleButtons.add(btn);
            addWidget(btn);
            i++;
        }

        // вычисляем максимальную прокрутку (высота контента минус видимая)
        int totalRows = (int) Math.ceil((double) config.rules.size() / colCount);
        int totalHeight = totalRows * spacing;
        int viewportHeight = height - baseY - 80;
        maxScroll = Math.max(0, totalHeight - viewportHeight);

        // сразу применим scrollOffset к позициям
        updateScrollPositions();
        setListVisible(true);
    }

    private void setListVisible(boolean visible) {
        for (Button b : ruleButtons) {
            b.visible = visible;
            b.active = visible;
        }
        if (addRuleButton != null) {
            addRuleButton.visible = visible;
            addRuleButton.active = visible;
        }
    }

    /* ======================= ПРОКРУТКА МЫШЬЮ ======================= */

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inEditMode) {
            scrollOffset -= delta * 20;
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            updateScrollPositions();
            return true;
        }
        return false;
    }

    private void updateScrollPositions() {
        int baseY = Math.max(70, height / 6);
        int colCount = width > 720 ? 2 : 1;
        int colWidth = width / colCount - 60;
        int spacing = 30;

        for (int i = 0; i < ruleButtons.size(); i++) {
            Button btn = ruleButtons.get(i);
            int col = i % colCount;
            int row = i / colCount;
            int x = 40 + col * (colWidth + 40);
            int y = (int) (baseY + row * spacing - scrollOffset);
            btn.setX(x);
            btn.setY(y);

            // видимость в пределах экрана
            btn.visible = y + 20 > 50 && y < height - 50;
        }
    }

    /* ======================= РЕДАКТОР ПРАВИЛА ======================= */

    private void initEditor(int width, int height) {
        this.widgets.clear();
        Font font = mc.font;

        int cx = width / 2;
        int y = Math.max(25, height / 10);

        // Заголовок
        addWidget(new LabelWidget(cx, y, I18n.get("dintegrate.gui.commands.editing_rule", currentRule.amount)));
        y += 25;

        // Команды (большое поле, адаптивное)
        int boxWidth = (int) (width * 0.7);
        int boxHeight = (int) (height * 0.45);
        commandsBox = new MultilineEditBox(font, cx - boxWidth / 2, y, boxWidth, boxHeight, Component.translatable("dintegrate.gui.commands.commands"));
        commandsBox.setValue(String.join("\n", currentRule.commands));
        addWidget(commandsBox);

        // Сумма
        y += boxHeight + 15;
        amountBox = new EditBox(font, cx - 120, y, 240, 20, Component.translatable("dintegrate.gui.commands.amount"));
        amountBox.setValue(String.valueOf(currentRule.amount));
        addWidget(amountBox);

        // Тип
        y += 30;
        String initialMode = normalizeMode(currentRule.mode);
        modeDropdown = CycleButton.<String>builder(s -> Component.literal(s))
                .withValues(MODES)
                .withInitialValue(initialMode)
                .displayOnlyValue()
                .create(cx - 120, y, 240, 20, Component.translatable("dintegrate.gui.commands.mode"), (btn, val) -> onModeChanged(val));
        addWidget(modeDropdown);

        // Поле N для randomN
        y += 28;
        randomNBox = new EditBox(font, cx - 120, y, 240, 20, Component.translatable("dintegrate.gui.commands.random_n"));
        int initialN = parseRandomN(currentRule.mode);
        if (initialN > 0) randomNBox.setValue(String.valueOf(initialN));
        addWidget(randomNBox);

        // Кнопки
        y += 40;
        saveButton = Button.builder(Component.translatable("dintegrate.gui.save"), b -> saveCurrentRule())
                .bounds(cx - 120, y, 70, 20).build();
        deleteButton = Button.builder(Component.translatable("dintegrate.gui.delete"), b -> deleteCurrentRule())
                .bounds(cx - 35, y, 70, 20).build();
        backButton = Button.builder(Component.translatable("dintegrate.gui.back"), b -> exitEditor())
                .bounds(cx + 50, y, 70, 20).build();

        addWidget(saveButton);
        addWidget(deleteButton);
        addWidget(backButton);

        updateRandomNVisibility();
    }

    private void openEditor(DonationRule rule) {
        this.currentRule = rule;
        this.inEditMode = true;
        init(mc, width, height);
    }

    private void createNewRule() {
        DonationRule rule = new DonationRule();
        rule.amount = 0;
        rule.mode = "all";
        rule.commands = new ArrayList<>();
        config.rules.add(rule);
        openEditor(rule);
    }

    private void saveCurrentRule() {
        try {
            currentRule.amount = Double.parseDouble(amountBox.getValue().trim());
        } catch (Exception e) {
            DonateIntegrate.sendClientMessage(Component.translatable("dintegrate.message.invalid_amount"));
            return;
        }

        String selectedMode = modeDropdown.getValue();
        if ("randomN".equals(selectedMode)) {
            int n = parseIntSafe(randomNBox.getValue().trim(), 1);
            currentRule.mode = "random" + n;
        } else {
            currentRule.mode = selectedMode;
        }

        String[] lines = commandsBox.getValue().split("\\R+");
        currentRule.commands = new ArrayList<>();
        for (String line : lines)
            if (!line.isBlank()) currentRule.commands.add(line.trim());

        DonateIntegrate.sendClientMessage(Component.translatable("dintegrate.message.rule_saved", currentRule.amount));
        saveRulesToJson();
        exitEditor();
    }

    private void deleteCurrentRule() {
        config.rules.remove(currentRule);
        DonateIntegrate.sendClientMessage(Component.translatable("dintegrate.message.rule_deleted"));
        saveRulesToJson();
        exitEditor();
    }

    private void exitEditor() {
        this.inEditMode = false;
        this.currentRule = null;
        init(mc, width, height);
    }

    private void onModeChanged(String newVal) {
        updateRandomNVisibility();
        if (Minecraft.getInstance().screen instanceof DonateIntegrateScreen s)
            s.rebuildForCurrentTab();
    }

    private void updateRandomNVisibility() {
        boolean show = "randomN".equals(modeDropdown.getValue());
        randomNBox.visible = show;
        randomNBox.active = show;
    }

    private int parseRandomN(String mode) {
        if (mode == null) return 0;
        mode = mode.toLowerCase(Locale.ROOT).trim();
        if (mode.startsWith("random") && mode.length() > 6) {
            try { return Integer.parseInt(mode.substring(6)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private String normalizeMode(String mode) {
        if (mode == null) return "all";
        mode = mode.toLowerCase(Locale.ROOT).trim();
        if (mode.equals("all") || mode.equals("random") || mode.equals("random_all")) return mode;
        if (mode.startsWith("random") && mode.length() > 6) return "randomN";
        return "all";
    }

    private void saveRulesToJson() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("config", "dintegrate.json");
            com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
            java.io.FileReader reader = new java.io.FileReader(path.toFile());
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();

            json.add("rules", gson.toJsonTree(config.rules));

            java.io.FileWriter writer = new java.io.FileWriter(path.toFile());
            gson.toJson(json, writer);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ======================= РЕНДЕР ======================= */

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, width, height, 0xAA000000);
        super.render(gfx, mouseX, mouseY, partialTicks);

        // рисуем скроллбар в списке
        if (!inEditMode && maxScroll > 1) {
            int baseY = Math.max(70, height / 6);
            int top = baseY;
            int bottom = height - 50;
            int trackLeft = width - 14;
            int trackRight = width - 10;

            gfx.fill(trackLeft, top, trackRight, bottom, 0x33000000); // трек

            // размер и позиция "ползунка"
            int trackHeight = bottom - top;
            // отношение видимой области к общему контенту
            int colCount = width > 720 ? 2 : 1;
            int spacing = 30;
            int totalRows = (int) Math.ceil((double) config.rules.size() / colCount);
            int totalHeight = totalRows * spacing;

            int thumbHeight = Math.max(12, (int) ((double) (trackHeight) * (trackHeight) / Math.max(trackHeight, totalHeight)));
            int maxThumbTravel = trackHeight - thumbHeight;
            int thumbY = top + (int) ((maxThumbTravel) * (scrollOffset / maxScroll));

            gfx.fill(trackLeft + 1, thumbY, trackRight - 1, thumbY + thumbHeight, 0xFFAAAAAA); // ползунок
        }
    }
}
