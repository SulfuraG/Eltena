package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.ModSyncClient;
import com.eltena.addon.network.model.PlayerStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class EltenaStatusHudOverlay {
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 8;
    private static final int TEXT_OFFSET_Y = 9;

    private EltenaStatusHudOverlay() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(EltenaStatusHudOverlay::onRenderGuiLayerPre);
    }

    private static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ModSyncClient.hasLivePlayerState()) {
            return;
        }

        PlayerStatePayload payload = ModSyncClient.getPlayerState();
        if (payload == null || payload.maxMp() <= 0) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int x = guiGraphics.guiWidth() / 2 + 10;
        int y = guiGraphics.guiHeight() - 39;

        renderMpBar(guiGraphics, font, x, y, payload.mp(), payload.maxMp());
        event.setCanceled(true);
    }

    private static void renderMpBar(GuiGraphics guiGraphics, Font font, int x, int y, int value, int maxValue) {
        int safeMax = Math.max(1, maxValue);
        int safeValue = Mth.clamp(value, 0, safeMax);
        int fillWidth = Mth.clamp(Math.round(BAR_WIDTH * ((float) safeValue / (float) safeMax)), 0, BAR_WIDTH);
        TextLine label = translated(font, "hud.eltenaaddon.mp");
        TextLine valueComponent = literal(font, safeValue + " / " + safeMax);

        guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xC7050A11);
        guiGraphics.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + BAR_HEIGHT - 1, 0xB3183C7A);
        if (fillWidth > 0) {
            guiGraphics.fill(x + 1, y + 1, x + fillWidth - 1, y + BAR_HEIGHT - 1, 0xFF4BA6FF);
        }
        guiGraphics.drawString(font, label.component(), x, y - TEXT_OFFSET_Y, 0xFFB7D8FF, false);
        guiGraphics.drawString(font, valueComponent.component(), x + BAR_WIDTH - valueComponent.width(), y - TEXT_OFFSET_Y, 0xFFD7E2EE, false);
    }

    private static TextLine translated(Font font, String key, Object... args) {
        MutableComponent component = AddonUiFont.translatable(key, args);
        return new TextLine(component, font.width(component));
    }

    private static TextLine literal(Font font, String value) {
        MutableComponent component = AddonUiFont.text(value);
        return new TextLine(component, font.width(component));
    }

    private record TextLine(MutableComponent component, int width) {
    }
}
