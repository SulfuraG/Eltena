package com.eltena.addon.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractPlaceholderScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 180;
    private final Component bodyText;

    protected AbstractPlaceholderScreen(Component title, Component bodyText) {
        super(title);
        this.bodyText = bodyText;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        guiGraphics.fill(0, 0, this.width, this.height, 0xB010141A);
        guiGraphics.fill(left, top, right, bottom, 0xF0181F2B);
        guiGraphics.fill(left, top, right, top + 28, 0xF0222D3D);
        drawBorder(guiGraphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF47607F);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.width / 2.0F, top + 24.0F, 0.0F);
        guiGraphics.pose().scale(1.35F, 1.35F, 1.0F);
        guiGraphics.drawCenteredString(this.font, this.title, 0, 0, 0xFFFFFF);
        guiGraphics.pose().popPose();

        guiGraphics.drawCenteredString(this.font, bodyText, this.width / 2, top + 78, 0xE2E8F0);
        guiGraphics.drawCenteredString(this.font, Component.literal("\u0045\u0053\u0043\u3067\u9589\u3058\u307e\u3059"), this.width / 2, bottom - 24, 0xF4D06F);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Disable the vanilla blurred background path for addon UI screens.
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
        // Disable the vanilla menu background path for addon UI screens.
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // Disable the vanilla menu background path for addon UI screens.
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
        // Disable the vanilla transparent background path for addon UI screens.
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
