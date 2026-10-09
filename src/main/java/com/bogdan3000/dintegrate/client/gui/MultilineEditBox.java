package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class MultilineEditBox extends EditBox {
    private final Font localFont;

    private int scrollOffset = 0; // индекс верхней строки в отображении
    private long lastBlink = 0L;
    private boolean caretVisible = true; // мигание курсора

    public MultilineEditBox(Font font, int x, int y, int width, int height, Component title) {
        super(font, x, y, width, height, title);
        this.localFont = font;
        this.setMaxLength(10000);
    }

    /* ======================= ВВОД/КЛАВИАТУРА ======================= */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter / Numpad Enter -> новая строка
        if (keyCode == 257 || keyCode == 335) {
            int cursor = this.getCursorPosition();
            String current = this.getValue();
            String newValue = current.substring(0, cursor) + "\n" + current.substring(cursor);
            this.setValue(newValue);
            this.setCursorPosition(cursor + 1);
            ensureCursorVisible();
            return true;
        }

        // Стрелки для вертикального скролла (опционально)
        if (keyCode == 265) { // ↑
            setScrollOffset(scrollOffset - 1);
            return true;
        } else if (keyCode == 264) { // ↓
            setScrollOffset(scrollOffset + 1);
            return true;
        }

        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) {
            ensureCursorVisible();
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == '\n' || codePoint == '\r') {
            int cursor = this.getCursorPosition();
            String current = this.getValue();
            String newValue = current.substring(0, cursor) + "\n" + current.substring(cursor);
            this.setValue(newValue);
            this.setCursorPosition(cursor + 1);
            ensureCursorVisible();
            return true;
        }
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) {
            ensureCursorVisible();
        }
        return handled;
    }

    /* ======================= МЫШЬ / СКРОЛЛ ======================= */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInside(mouseX, mouseY)) return false;
        // scrollY > 0 — вверх
        setScrollOffset(scrollOffset - (scrollY > 0 ? 1 : -1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = isInside(mouseX, mouseY);
        this.setFocused(inside);
        if (!inside) return false;

        // вычисляем линию и столбец под курсором
        String[] lines = getLines();
        int lineH = localFont.lineHeight + 2;
        int relY = (int)mouseY - (this.getY() + 4);
        int visIdx = Math.max(0, Math.min(relY / lineH, Math.max(0, getVisibleLines() - 1)));
        int lineIdx = Math.max(0, Math.min(scrollOffset + visIdx, lines.length - 1));

        // ищем колонку по пиксельной ширине
        String line = lines[lineIdx];
        int relX = (int)mouseX - (this.getX() + 4);
        int col = 0;
        int px = 0;
        for (int i = 0; i < line.length(); i++) {
            int w = localFont.width(String.valueOf(line.charAt(i)));
            if (px + w / 2 >= relX) { col = i; break; }
            px += w;
            col = i + 1;
        }

        // пересчитываем в позицию курсора по всему тексту
        int cursor = 0;
        for (int i = 0; i < lineIdx; i++) cursor += lines[i].length() + 1; // +\n
        cursor += Math.max(0, Math.min(col, line.length()));

        this.setCursorPosition(cursor);
        ensureCursorVisible();
        return true;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    /* ======================= ОТРисовка ======================= */

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        // рамка
        gfx.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, 0xFF808080);
        // фон
        gfx.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);

        // рисуем только видимую часть
        String[] lines = getLines();
        int lineH = localFont.lineHeight + 2;
        int visibleLines = getVisibleLines();

        // корректный диапазон
        int maxStart = Math.max(0, lines.length - visibleLines);
        int start = Math.min(scrollOffset, maxStart);
        int end = Math.min(lines.length, start + visibleLines);

        int lineY = this.getY() + 4;
        for (int i = start; i < end; i++) {
            gfx.drawString(localFont, lines[i], this.getX() + 4, lineY, 0xFFFFFF, false);
            lineY += lineH;
        }

        // карет (мигающий курсор)
        drawCaret(gfx, lines, start, end, lineH);

        // простая полоса прокрутки справа
        if (lines.length > visibleLines) {
            int barHeight = Math.max(10, (int)((float)visibleLines / lines.length * (this.height - 4)));
            int available = this.height - 4 - barHeight;
            int barY = this.getY() + 2 + (available <= 0 ? 0 : (int)((float)start / (lines.length - visibleLines) * available));
            gfx.fill(this.getX() + this.width - 3, barY, this.getX() + this.width - 1, barY + barHeight, 0xFFAAAAAA);
        }
    }

    private void drawCaret(GuiGraphics gfx, String[] lines, int start, int end, int lineH) {
        if (!this.isFocused()) return;

        // мигание курсора
        long now = System.currentTimeMillis();
        if (now - lastBlink > 500) { caretVisible = !caretVisible; lastBlink = now; }
        if (!caretVisible) return;

        // позиция курсора -> (lineIdx, col)
        int cursor = this.getCursorPosition();
        int charCount = 0;
        int lineIdx = 0;
        int col = 0;

        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length();
            if (cursor <= charCount + len) {
                lineIdx = i;
                col = cursor - charCount;
                break;
            }
            charCount += len + 1; // +\n
            if (i == lines.length - 1) {
                lineIdx = i;
                col = len;
            }
        }

        // если курсор вне видимого окна — не рисуем (ensureCursorVisible обычно это предотвращает)
        if (lineIdx < start || lineIdx >= end) return;

        int x = this.getX() + 4 + localFont.width(lines[lineIdx].substring(0, Math.max(0, Math.min(col, lines[lineIdx].length()))));
        int y = this.getY() + 4 + (lineIdx - start) * lineH;

        // тонкая вертикальная линия-курсор
        gfx.fill(x, y - 1, x + 1, y + localFont.lineHeight + 1, 0xFFFFFFFF);
    }

    /* ======================= УТИЛИТЫ ======================= */

    private String[] getLines() {
        return this.getValue().split("\n", -1);
    }

    private int getVisibleLines() {
        int lineH = localFont.lineHeight + 2;
        return Math.max(1, this.height / lineH);
    }

    private void setScrollOffset(int newOffset) {
        int maxOffset = Math.max(0, getLines().length - getVisibleLines());
        scrollOffset = Math.max(0, Math.min(newOffset, maxOffset));
    }

    private void ensureCursorVisible() {
        String[] lines = getLines();
        int cursorIndex = this.getCursorPosition();

        // перевод курсора в (lineIdx, col)
        int lineIdx = 0;
        int charCount = 0;
        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length();
            if (cursorIndex <= charCount + len) { lineIdx = i; break; }
            charCount += len + 1; // +\n
            lineIdx = i + 1;
        }

        int visible = getVisibleLines();
        if (lineIdx < scrollOffset) {
            scrollOffset = lineIdx;
        } else if (lineIdx >= scrollOffset + visible) {
            scrollOffset = lineIdx - visible + 1;
        }

        setScrollOffset(scrollOffset); // clamp
    }
}
