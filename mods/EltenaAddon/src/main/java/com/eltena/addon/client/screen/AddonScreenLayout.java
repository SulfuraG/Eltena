package com.eltena.addon.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class AddonScreenLayout {
    static final int ROOT_WIDTH = 900;
    static final int ROOT_HEIGHT = 466;
    private static final int ROOT_MARGIN = 24;

    private AddonScreenLayout() {
    }

    static float fitScale(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        float guiScale = minecraft != null ? (float) minecraft.getWindow().getGuiScale() : 2.0F;
        float preferredScale = 2.0F / Math.max(1.0F, guiScale);
        float fitWidth = screen.width / (float) ROOT_WIDTH;
        float fitHeight = screen.height / (float) ROOT_HEIGHT;
        return Math.min(preferredScale, Math.min(fitWidth, fitHeight));
    }

    static int virtualScreenWidth(Screen screen) {
        return Math.round(screen.width / fitScale(screen));
    }

    static int virtualScreenHeight(Screen screen) {
        return Math.round(screen.height / fitScale(screen));
    }

    static int rootLeft(Screen screen) {
        return (virtualScreenWidth(screen) - ROOT_WIDTH) / 2;
    }

    static int rootTop(Screen screen) {
        return Math.max(ROOT_MARGIN / 2, (virtualScreenHeight(screen) - ROOT_HEIGHT) / 2);
    }

    static int headerHeight() {
        return Math.max(44, Math.min(60, Math.round(ROOT_HEIGHT * 0.11F)));
    }

    static int footerHeight() {
        return Math.max(58, Math.min(76, Math.round(ROOT_HEIGHT * 0.11F)));
    }

    static int footerTop(Screen screen) {
        return rootTop(screen) + ROOT_HEIGHT - footerHeight() - 8;
    }

    static int contentTop(Screen screen) {
        return rootTop(screen) + headerHeight() - 20;
    }

    static int contentBottom(Screen screen) {
        return footerTop(screen) - 10;
    }

    static int toVirtualX(Screen screen, double mouseX) {
        return (int) Math.round(mouseX / fitScale(screen));
    }

    static int toVirtualY(Screen screen, double mouseY) {
        return (int) Math.round(mouseY / fitScale(screen));
    }

    static ScissorRect toScissor(Screen screen, int x, int y, int width, int height) {
        float scale = fitScale(screen);
        int left = Math.round(x * scale);
        int top = Math.round(y * scale);
        int right = Math.round((x + width) * scale);
        int bottom = Math.round((y + height) * scale);
        return new ScissorRect(left, top, right, bottom);
    }

    record ScissorRect(int left, int top, int right, int bottom) {
    }
}
