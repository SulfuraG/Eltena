package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class SelectedActionHudRenderer {
    private static final int HUD_WIDTH = 136;
    private static final int HUD_PADDING_X = 7;
    private static final int HUD_PADDING_Y = 6;
    private static final int HUD_RIGHT_MARGIN = 10;
    private static final int HUD_BOTTOM_MARGIN = 18;
    private static final int HUD_GAP = 8;
    private static final int HUD_ICON_SIZE = 20;
    private static final int TEXT_X_OFFSET = 31;
    private static final int NAME_Y_OFFSET = 16;
    private static final int MP_Y_OFFSET = 27;
    private static final int COOLDOWN_Y_OFFSET = 38;
    private static final int HINT_Y_OFFSET = 49;

    private SelectedActionHudRenderer() {
    }

    static void render(
        GuiGraphics guiGraphics,
        Font font,
        int stackIndexFromBottom,
        String iconPath,
        String iconFallback,
        Component title,
        Component name,
        Component mp,
        Component cooldown,
        Component hint,
        int backgroundColor,
        int borderColor,
        int titleColor,
        int nameColor,
        int mpColor,
        int cooldownColor,
        double cooldownRemaining
    ) {
        int height = hudHeight(font);
        int x = Math.max(6, guiGraphics.guiWidth() - HUD_RIGHT_MARGIN - HUD_WIDTH);
        int y = Math.max(6, guiGraphics.guiHeight() - HUD_BOTTOM_MARGIN - height - (stackIndexFromBottom * (height + HUD_GAP)));

        guiGraphics.fill(x, y, x + HUD_WIDTH, y + height, backgroundColor);
        drawBorder(guiGraphics, x, y, HUD_WIDTH, height, borderColor);

        int iconX = x + HUD_PADDING_X;
        int iconY = y + (height - HUD_ICON_SIZE) / 2;
        RadialIconRenderer.renderIcon(guiGraphics, font, iconPath, iconFallback, iconX, iconY, HUD_ICON_SIZE);
        if (cooldownRemaining > 0.0D) {
            RadialIconRenderer.renderCooldownOverlay(guiGraphics, font, iconX, iconY, HUD_ICON_SIZE, cooldownRemaining, true);
        }

        int textX = x + TEXT_X_OFFSET;
        int maxTextWidth = HUD_WIDTH - TEXT_X_OFFSET - HUD_PADDING_X;
        guiGraphics.drawString(font, fitLine(font, title, maxTextWidth), textX, y + HUD_PADDING_Y, titleColor, false);
        guiGraphics.drawString(font, fitLine(font, name, maxTextWidth), textX, y + NAME_Y_OFFSET, nameColor, false);
        guiGraphics.drawString(font, fitLine(font, mp, maxTextWidth), textX, y + MP_Y_OFFSET, mpColor, false);
        guiGraphics.drawString(font, fitLine(font, cooldown, maxTextWidth), textX, y + COOLDOWN_Y_OFFSET, cooldownColor, false);
        guiGraphics.drawString(font, fitLine(font, hint, maxTextWidth), textX, y + HINT_Y_OFFSET, 0xFFD8E7F2, false);
    }

    static int hudHeight(Font font) {
        return Math.max(HINT_Y_OFFSET + font.lineHeight + HUD_PADDING_Y - 1, HUD_ICON_SIZE + (HUD_PADDING_Y * 2));
    }

    private static Component fitLine(Font font, Component component, int maxWidth) {
        String raw = component == null ? "" : component.getString();
        if (raw.isBlank()) {
            return AddonUiFont.text("");
        }
        if (font.width(raw) <= maxWidth) {
            return AddonUiFont.text(raw);
        }
        String shortened = font.plainSubstrByWidth(raw, Math.max(0, maxWidth - font.width("...")));
        if (shortened.length() >= raw.length()) {
            return AddonUiFont.text(shortened);
        }
        return AddonUiFont.text(shortened + "...");
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }
}
