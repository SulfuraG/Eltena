package com.eltena.addon.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class RadialOverlayLayout {
    private static final int BASE_ROOT_WIDTH = 900;
    private static final int BASE_ROOT_HEIGHT = 466;
    private static final int ROOT_MARGIN = 24;
    private static final int CENTER_Y_OFFSET = -8;

    private final float fitScale;
    private final int virtualScreenWidth;
    private final int virtualScreenHeight;
    private final int rootX;
    private final int rootY;

    private RadialOverlayLayout(float fitScale, int virtualScreenWidth, int virtualScreenHeight, int rootX, int rootY) {
        this.fitScale = fitScale;
        this.virtualScreenWidth = virtualScreenWidth;
        this.virtualScreenHeight = virtualScreenHeight;
        this.rootX = rootX;
        this.rootY = rootY;
    }

    static RadialOverlayLayout create(Minecraft minecraft, GuiGraphics guiGraphics) {
        float guiScale = minecraft != null ? (float) minecraft.getWindow().getGuiScale() : 2.0F;
        float preferredScale = 2.0F / Math.max(1.0F, guiScale);
        float fitWidth = guiGraphics.guiWidth() / (float) BASE_ROOT_WIDTH;
        float fitHeight = guiGraphics.guiHeight() / (float) BASE_ROOT_HEIGHT;
        float fitScale = Math.min(preferredScale, Math.min(fitWidth, fitHeight));
        int virtualScreenWidth = Math.round(guiGraphics.guiWidth() / fitScale);
        int virtualScreenHeight = Math.round(guiGraphics.guiHeight() / fitScale);
        int rootX = (virtualScreenWidth - BASE_ROOT_WIDTH) / 2;
        int rootY = Math.max(ROOT_MARGIN / 2, (virtualScreenHeight - BASE_ROOT_HEIGHT) / 2);
        return new RadialOverlayLayout(fitScale, virtualScreenWidth, virtualScreenHeight, rootX, rootY);
    }

    float fitScale() {
        return fitScale;
    }

    int virtualScreenWidth() {
        return virtualScreenWidth;
    }

    int virtualScreenHeight() {
        return virtualScreenHeight;
    }

    int rootX() {
        return rootX;
    }

    int rootY() {
        return rootY;
    }

    int centerX() {
        return rootX + (BASE_ROOT_WIDTH / 2);
    }

    int centerY() {
        return rootY + (BASE_ROOT_HEIGHT / 2) + CENTER_Y_OFFSET;
    }

    int toVirtualX(double value) {
        return (int) Math.round(value / fitScale);
    }

    int toVirtualY(double value) {
        return (int) Math.round(value / fitScale);
    }

    int mouseX(Minecraft minecraft) {
        if (minecraft == null) {
            return centerX();
        }
        double guiX = minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth()
            / minecraft.getWindow().getScreenWidth();
        return toVirtualX(guiX);
    }

    int mouseY(Minecraft minecraft) {
        if (minecraft == null) {
            return centerY();
        }
        double guiY = minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight()
            / minecraft.getWindow().getScreenHeight();
        return toVirtualY(guiY);
    }
}
