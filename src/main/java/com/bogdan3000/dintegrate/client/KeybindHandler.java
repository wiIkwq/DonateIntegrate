package com.bogdan3000.dintegrate.client;

import com.bogdan3000.dintegrate.client.gui.DonateIntegrateScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeybindHandler {

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.dintegrate.open_gui", // ID
            GLFW.GLFW_KEY_F8,          // клавиша (можешь заменить)
            "key.categories.dintegrate" // категория в настройках управления
    );

    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    // === Реакция на нажатие ===
    @EventBusSubscriber(modid = "dintegrate", value = Dist.CLIENT)
    public static class NeoForgeKeyHandler {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (OPEN_GUI_KEY.consumeClick()) {
                mc.setScreen(new DonateIntegrateScreen());
            }
        }
    }
}
