package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TabInfo extends TabBase {

    private final List<String> infoLines = List.of(
            "§e=== DonateIntegrate Mod — Справка ===",
            "",
            "§bGUI Config§7 — DonatePay: вставь token и нажми Get ID",
            "§bGUI Config§7 — DonationAlerts: нажми Authorize",
            "§b/dpi dp token <value>§7 — задать токен DonatePay",
            "§b/dpi dp getid§7 — получить User ID по токену",
            "§b/dpi dp user <id>§7 — задать User ID вручную",
            "§b/dpi dp reconnect|stop|status§7 — управление DonatePay",
            "§b/dpi da token <value>§7 — задать OAuth token DonationAlerts",
            "§b/dpi da user <id>§7 — задать DonationAlerts user ID",
            "§b/dpi da reset§7 — сбросить DonationAlerts логин",
            "§b/dpi da reconnect|stop|status§7 — управление DonationAlerts",
            "§b/dpi reload§7 — перезагрузить конфиг без перезапуска сокета",
            "",
            "§6=== Конфигурация донатов (config/dintegrate.json) ===",
            "",
            "§eamount§7 — сумма доната, на которую реагирует правило",
            "§emode§7 — режим выполнения команд:",
            "  §aall§7 — выполняет все команды по порядку",
            "  §brandom§7 — выбирает одну случайную команду",
            "  §drandom_all§7 — выполняет все команды, но в случайном порядке",
            "  §crandomN§7 — выполняет N случайных команд без повторов (пример: random3)",
            "",
            "§6=== Поддерживаемые команды ===",
            "  §f/delay <сек>§7 — задержка перед выполнением следующей команды",
            "  §f/randomdelay <A-B>§7 — случайная задержка между A и B сек",
            "  §f/любая Minecraft-команда§7 — например:",
            "     §7/give @p minecraft:diamond 1",
            "     §7/title @p actionbar {\"text\":\"Спасибо!\"}",
            "",
            "§6=== Плейсхолдеры ===",
            "  §a{name}§7 — имя донатера",
            "  §a{sum}§7 — сумма доната",
            "  §a{currency}§7 — валюта события",
            "  §a{message}§7 — сообщение донатера",
            "  §a{source}§7 — источник события",
            "  §a{event}§7 — тип события",
            "",
            "§6=== Пример правила ===",
            "§8{",
            "§8  \"amount\": 10,",
            "§8  \"mode\": \"all\",",
            "§8  \"commands\": [",
            "§8    \"/say Спасибо, {name}, за {sum}₽!\",",
            "§8    \"/delay 1.5\",",
            "§8    \"/say Сообщение: {message}\",",
            "§8    \"/give @p minecraft:diamond 1\"",
            "§8  ]",
            "§8}",
            "",
            "§7Файл создаётся автоматически при первом запуске мода."
    );

    public TabInfo() {
        super(I18n.get("dintegrate.gui.tab.info"));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, width, height, 0xAA000000);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // === динамический масштаб ===
        float scale = Math.max(0.75f, Math.min(1.0f, height / 900f)); // при маленьком окне уменьшается
        int lineHeight = Math.round(10 * scale);
        int y = Math.max(20, (int) (height * 0.05));

        gfx.pose().pushPose();
        gfx.pose().scale(scale, scale, 1.0f);

        int scaledWidth = (int) (width / scale);
        int startX = Math.max(15, (int) (scaledWidth * 0.05));

        for (String line : infoLines) {
            if (y > height / scale - 20) break; // не выходим за границы
            gfx.drawString(font, Component.literal(line), startX, y, 0xFFFFFF, false);
            y += lineHeight;
        }

        gfx.pose().popPose();
        super.render(gfx, mouseX, mouseY, partialTicks);
    }
}
