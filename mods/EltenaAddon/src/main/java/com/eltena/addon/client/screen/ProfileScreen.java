package com.eltena.addon.client.screen;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.ModSyncClient;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class ProfileScreen extends Screen {
    private static final int PANEL_GAP = 14;
    private static final int SIDE_PADDING = 16;
    private static final int PROFILE_PANEL_WIDTH = 320;
    private static final float TITLE_SCALE = 1.16F;
    private static final float LABEL_SCALE = 0.94F;
    private static final float VALUE_SCALE = 1.00F;

    public ProfileScreen() {
        super(AddonUiFont.translatable("screen.eltenaaddon.profile"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(
            Button.builder(AddonUiFont.translatable("screen.eltenaaddon.back_to_menu"), button -> openMenu())
                .bounds(rootLeft() + 8, rootTop() + 8, 104, 20)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float fitScale = AddonScreenLayout.fitScale(this);
        int virtualMouseX = AddonScreenLayout.toVirtualX(this, mouseX);
        int virtualMouseY = AddonScreenLayout.toVirtualY(this, mouseY);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(fitScale, fitScale, 1.0F);
        renderFrame(guiGraphics);
        renderHeader(guiGraphics);
        renderProfilePanel(guiGraphics);
        renderStatPanel(guiGraphics);
        renderFooter(guiGraphics);
        super.render(guiGraphics, virtualMouseX, virtualMouseY, partialTick);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !hasShiftDown()) {
            openMenu();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
    }

    private void renderFrame(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, AddonScreenLayout.virtualScreenWidth(this), AddonScreenLayout.virtualScreenHeight(this), 0xA4060C14);
        guiGraphics.fill(rootLeft(), rootTop(), rootRight(), rootBottom(), 0xE10A1522);
        guiGraphics.fill(rootLeft() + 2, rootTop() + 2, rootRight() - 2, rootBottom() - 2, 0x7A112237);
        guiGraphics.fill(rootLeft(), rootTop(), rootRight(), rootTop() + AddonScreenLayout.headerHeight(), 0xE7132235);
        guiGraphics.fill(rootLeft(), rootBottom() - AddonScreenLayout.footerHeight(), rootRight(), rootBottom(), 0xD80D1723);
        drawBorder(guiGraphics, rootLeft(), rootTop(), AddonScreenLayout.ROOT_WIDTH, AddonScreenLayout.ROOT_HEIGHT, 0xFF2F5D87);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        drawScaledString(guiGraphics, AddonUiFont.apply(this.title), rootLeft() + 126, rootTop() + 14, 0xFFFFFFFF, TITLE_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.summary.player"), rootLeft() + 126, rootTop() + 36, 0xFFA9C8E8, VALUE_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.text(ModSyncClient.getPlayerName()), rootLeft() + 198, rootTop() + 36, 0xFFEAF3FF, VALUE_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.sync.label"), rootRight() - 214, rootTop() + 36, 0xFFA9C8E8, VALUE_SCALE);
        drawScaledString(guiGraphics, AddonUiFont.text(currentSyncLabel()), rootRight() - 140, rootTop() + 36, 0xFFC7D2DF, VALUE_SCALE);
    }

    private void renderProfilePanel(GuiGraphics guiGraphics) {
        int left = rootLeft() + SIDE_PADDING;
        int top = AddonScreenLayout.contentTop(this) + 16;
        int width = PROFILE_PANEL_WIDTH;
        int height = AddonScreenLayout.contentBottom(this) - top - 8;
        drawPanel(guiGraphics, left, top, width, height);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.summary.profile"), left + 14, top + 12, 0xFFF4D06F, 1.04F);

        int columnWidth = width - 28;
        int y = top + 38;
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.level", String.valueOf(ModSyncClient.getLevel()));
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.job", ModSyncClient.getCurrentJobName());
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.current_title", profileValue(ModSyncClient.getCurrentTitleName()));
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.title_count", String.valueOf(ModSyncClient.getUnlockedTitleCount()));
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.world_title", profileValue(ModSyncClient.getWorldTitleName()));
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.world_title_exp", String.valueOf(ModSyncClient.getWorldTitleExp()));
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.title_effect", AddonUiFont.translatable("screen.eltenaaddon.profile.value.none").getString());
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.hp", ModSyncClient.getHp() + " / " + ModSyncClient.getMaxHp());
        y = drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.mp", ModSyncClient.getMp() + " / " + ModSyncClient.getMaxMp());
        drawStat(guiGraphics, left + 14, y, columnWidth, "screen.eltenaaddon.profile.summary.exp", ModSyncClient.getExp() + " / " + ModSyncClient.getMaxExp());
    }

    private void renderStatPanel(GuiGraphics guiGraphics) {
        int left = rootLeft() + SIDE_PADDING + PROFILE_PANEL_WIDTH + PANEL_GAP;
        int top = AddonScreenLayout.contentTop(this) + 16;
        int width = AddonScreenLayout.ROOT_WIDTH - (left - rootLeft()) - SIDE_PADDING;
        int height = AddonScreenLayout.contentBottom(this) - top - 8;
        drawPanel(guiGraphics, left, top, width, height);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.summary.stats"), left + 14, top + 12, 0xFFF4D06F, 1.04F);

        int columnWidth = (width - 44) / 2;
        int leftColumnX = left + 14;
        int rightColumnX = left + 24 + columnWidth;

        int leftY = top + 38;
        leftY = drawStat(guiGraphics, leftColumnX, leftY, columnWidth, "screen.eltenaaddon.profile.summary.attack", String.valueOf(ModSyncClient.getBaseAttack()));
        leftY = drawStat(guiGraphics, leftColumnX, leftY, columnWidth, "screen.eltenaaddon.profile.summary.weapon_attack", formatDecimal(ModSyncClient.getWeaponAttack()));
        leftY = drawStat(guiGraphics, leftColumnX, leftY, columnWidth, "screen.eltenaaddon.profile.summary.attack_power", formatDecimal(ModSyncClient.getAttackPower()));
        leftY = drawStat(guiGraphics, leftColumnX, leftY, columnWidth, "screen.eltenaaddon.profile.summary.defense", formatDecimal(ModSyncClient.getDefense()));
        leftY = drawStat(guiGraphics, leftColumnX, leftY, columnWidth, "screen.eltenaaddon.profile.summary.hp", ModSyncClient.getHp() + " / " + ModSyncClient.getMaxHp());

        int rightY = top + 38;
        rightY = drawStat(guiGraphics, rightColumnX, rightY, columnWidth, "screen.eltenaaddon.profile.summary.mp", ModSyncClient.getMp() + " / " + ModSyncClient.getMaxMp());
        rightY = drawStat(guiGraphics, rightColumnX, rightY, columnWidth, "screen.eltenaaddon.profile.summary.job", profileValue(ModSyncClient.getCurrentJobName()));
        rightY = drawStat(guiGraphics, rightColumnX, rightY, columnWidth, "screen.eltenaaddon.profile.summary.weapon_type", profileValue(ModSyncClient.getWeaponType()));
        rightY = drawStat(guiGraphics, rightColumnX, rightY, columnWidth, "screen.eltenaaddon.profile.summary.weapon_category", profileValue(ModSyncClient.getWeaponCategory()));
        drawStat(guiGraphics, rightColumnX, rightY, columnWidth, "screen.eltenaaddon.profile.summary.exp", ModSyncClient.getExp() + " / " + ModSyncClient.getMaxExp());
    }

    private int drawStat(GuiGraphics guiGraphics, int x, int y, int width, String labelKey, String value) {
        drawScaledString(guiGraphics, AddonUiFont.translatable(labelKey), x, y, 0xFFA9C8E8, LABEL_SCALE);
        int drawY = y + 12;
        for (FormattedCharSequence line : splitScaledText(AddonUiFont.text(value), VALUE_SCALE, width)) {
            drawScaledFormattedString(guiGraphics, line, x, drawY, 0xFFEAF3FF, VALUE_SCALE);
            drawY += 11;
        }
        return drawY + 6;
    }

    private void renderFooter(GuiGraphics guiGraphics) {
        int left = rootLeft() + 14;
        int top = rootBottom() - AddonScreenLayout.footerHeight() + 9;
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.footer"), left, top, 0xFFB6C7D8, 0.96F);
    }

    private String currentSyncLabel() {
        return ModSyncClient.lastPlayerStateSyncSource().isBlank()
            ? AddonUiFont.translatable("screen.eltenaaddon.sync.mock").getString()
            : AddonUiFont.translatable("screen.eltenaaddon.sync.live", ModSyncClient.lastPlayerStateSyncSource()).getString();
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String profileValue(String value) {
        return value == null || value.isBlank()
            ? AddonUiFont.translatable("screen.eltenaaddon.profile.value.unset").getString()
            : value;
    }

    private List<FormattedCharSequence> splitScaledText(Component text, float scale, int maxWidth) {
        int scaledWidth = Math.max(20, Math.round(maxWidth / Math.max(0.01F, scale)));
        return this.font.split(text, scaledWidth);
    }

    private void openMenu() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EltenaMenuScreen());
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x66313E4A);
        drawBorder(guiGraphics, x, y, width, height, 0xAA1F384C);
    }

    private void drawScaledString(GuiGraphics guiGraphics, Component text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawScaledFormattedString(GuiGraphics guiGraphics, FormattedCharSequence text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private int rootLeft() {
        return AddonScreenLayout.rootLeft(this);
    }

    private int rootTop() {
        return AddonScreenLayout.rootTop(this);
    }

    private int rootRight() {
        return rootLeft() + AddonScreenLayout.ROOT_WIDTH;
    }

    private int rootBottom() {
        return rootTop() + AddonScreenLayout.ROOT_HEIGHT;
    }
}
