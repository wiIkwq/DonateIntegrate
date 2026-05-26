package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class MaskedTokenBox extends EditBox {
    private String actualValue = "";
    private Consumer<String> actualResponder;
    private Runnable revealRequest;
    private boolean revealed = false;

    public MaskedTokenBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    public void setActualValue(String value) {
        this.actualValue = value == null ? "" : value.trim();
        super.setValue(revealed ? actualValue : mask(actualValue));
        setCursorPosition(getValue().length());
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualResponder(Consumer<String> responder) {
        this.actualResponder = responder;
    }

    public void setRevealRequest(Runnable revealRequest) {
        this.revealRequest = revealRequest;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        if (!revealed && this.revealed) {
            actualValue = getValue();
        }
        this.revealed = revealed;
        super.setValue(revealed ? actualValue : mask(actualValue));
        setCursorPosition(getValue().length());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!revealed && isMouseOver(mouseX, mouseY)) {
            if (revealRequest != null) revealRequest.run();
            setFocused(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!revealed) return false;
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) syncActualFromVisible();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!revealed) return false;
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) syncActualFromVisible();
        return handled;
    }

    private void syncActualFromVisible() {
        actualValue = getValue().trim();
        notifyActualResponder();
    }

    private void notifyActualResponder() {
        if (actualResponder != null) {
            actualResponder.accept(actualValue);
        }
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.startsWith("YOUR_")) return value;
        if (value.length() <= 4) return "*".repeat(value.length());
        return value.substring(0, 2) + "*".repeat(Math.min(24, value.length() - 4)) + value.substring(value.length() - 2);
    }
}
