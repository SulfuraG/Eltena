package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RadialIconRenderer {
    private static final int BASE_ICON_SIZE = 16;

    private RadialIconRenderer() {
    }

    public static void renderIcon(GuiGraphics guiGraphics, Font font, String iconPath, String fallbackText, int x, int y, int size) {
        if (tryRenderItem(guiGraphics, iconPath, x, y, size)) {
            return;
        }
        if (tryRenderTexture(guiGraphics, iconPath, x, y, size)) {
            return;
        }
        Component fallback = AddonUiFont.text(abbreviate(fallbackText));
        int textX = x + (size - font.width(fallback)) / 2;
        int textY = y + (size - font.lineHeight) / 2;
        guiGraphics.drawString(font, fallback, textX, textY, 0xFFEAF3FF, true);
    }

    public static void renderCooldownOverlay(GuiGraphics guiGraphics, Font font, int x, int y, int size, double cooldownRemaining, boolean selected) {
        if (cooldownRemaining <= 0.0D) {
            return;
        }
        int overlay = selected ? 0x9A08121C : 0xB4101620;
        String seconds = CooldownDisplayHelper.formatCompactSeconds(cooldownRemaining);
        int textY = y + Math.max(2, (size - font.lineHeight) / 2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.fill(x, y, x + size, y + size, overlay);
        guiGraphics.drawCenteredString(font, AddonUiFont.text(seconds), x + size / 2, textY, 0xFFFFFFFF);
        guiGraphics.pose().popPose();
    }

    private static boolean tryRenderTexture(GuiGraphics guiGraphics, String iconPath, int x, int y, int size) {
        if (!isTexturePath(iconPath)) {
            return false;
        }
        try {
            ResourceLocation resourceLocation = ResourceLocation.parse(iconPath);
            float scale = size / (float) BASE_ICON_SIZE;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.blit(resourceLocation, 0, 0, 0, 0, BASE_ICON_SIZE, BASE_ICON_SIZE, BASE_ICON_SIZE, BASE_ICON_SIZE);
            guiGraphics.pose().popPose();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryRenderItem(GuiGraphics guiGraphics, String iconPath, int x, int y, int size) {
        if (iconPath == null || iconPath.isBlank() || isTexturePath(iconPath)) {
            return false;
        }
        try {
            ResourceLocation resourceLocation = ResourceLocation.parse(iconPath);
            Item item = BuiltInRegistries.ITEM.get(resourceLocation);
            if (item == null || item == Items.AIR) {
                return false;
            }
            ItemStack stack = new ItemStack(item);
            float scale = size / (float) BASE_ICON_SIZE;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.renderItem(stack, 0, 0);
            guiGraphics.pose().popPose();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isTexturePath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return false;
        }
        String normalized = iconPath.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("/textures/") || normalized.endsWith(".png");
    }
    private static String abbreviate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "?";
        }
        String compact = raw.trim();
        return compact.length() <= 2 ? compact : compact.substring(0, 2).toUpperCase();
    }
}
