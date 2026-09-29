package com.eltena.addon.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

final class PlayerModelRenderer {
    private static final float DEFAULT_SIZE_FACTOR = 0.52F;
    private static final float DEFAULT_LOOK_STRENGTH = 0.16F;
    private static final int DEFAULT_BOTTOM_INSET = 16;

    private PlayerModelRenderer() {
    }

    static boolean render(GuiGraphics guiGraphics, int left, int top, int width, int height, int mouseX, int mouseY, LivingEntity entity) {
        return render(guiGraphics, left, top, width, height, mouseX, mouseY, entity, DEFAULT_SIZE_FACTOR, DEFAULT_LOOK_STRENGTH, DEFAULT_BOTTOM_INSET);
    }

    static boolean render(
        GuiGraphics guiGraphics,
        int left,
        int top,
        int width,
        int height,
        int mouseX,
        int mouseY,
        LivingEntity entity,
        float sizeFactor,
        float lookStrength,
        int bottomInset
    ) {
        if (entity == null || width < 48 || height < 72) {
            return false;
        }
        int available = Math.max(40, Math.min(width - 26, height - 24));
        int size = Math.max(28, Math.round(available * Math.max(0.35F, Math.min(0.95F, sizeFactor))));
        int centerX = left + width / 2;
        int bottomY = top + height - Math.max(6, bottomInset);
        int x1 = centerX - size / 2;
        int y1 = bottomY - size;
        int x2 = x1 + size;
        int y2 = bottomY;
        float lookCenterX = x1 + size / 2.0F;
        float lookCenterY = y1 + size * 0.36F;
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x1,
                y1,
                x2,
                y2,
                size,
                0.0F,
                (lookCenterX - mouseX) * lookStrength,
                (lookCenterY - mouseY) * lookStrength,
                entity
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
