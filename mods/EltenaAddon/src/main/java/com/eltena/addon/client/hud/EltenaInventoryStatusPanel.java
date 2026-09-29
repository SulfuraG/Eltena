package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.ModSyncClient;
import com.eltena.addon.network.model.PlayerStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public final class EltenaInventoryStatusPanel {
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;
    private static final int PANEL_MAX_WIDTH = 142;
    private static final int PANEL_MIN_WIDTH = 104;
    private static final int PANEL_MARGIN = 10;
    private static final int SCREEN_MARGIN = 6;
    private static final int PANEL_DESIRED_HEIGHT = 212;
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private EltenaInventoryStatusPanel() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(EltenaInventoryStatusPanel::onScreenRenderPost);
    }

    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ModSyncClient.hasLivePlayerState()) {
            return;
        }
        PlayerStatePayload payload = ModSyncClient.getPlayerState();
        if (payload == null) {
            return;
        }
        render(event.getGuiGraphics(), minecraft.font, payload);
    }

    private static void render(GuiGraphics guiGraphics, Font font, PlayerStatePayload payload) {
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        int inventoryLeft = (guiWidth - INVENTORY_WIDTH) / 2;
        int inventoryTop = (guiHeight - INVENTORY_HEIGHT) / 2;
        int availableWidth = inventoryLeft - PANEL_MARGIN - SCREEN_MARGIN;
        if (availableWidth < PANEL_MIN_WIDTH) {
            return;
        }

        int panelWidth = Math.min(PANEL_MAX_WIDTH, availableWidth);
        int panelLeft = Math.max(SCREEN_MARGIN, inventoryLeft - PANEL_MARGIN - panelWidth);
        int maxPanelHeight = Math.max(INVENTORY_HEIGHT, guiHeight - SCREEN_MARGIN * 2);
        PanelStyle style = chooseStyle(font, panelWidth, maxPanelHeight);
        int measuredContentHeight = measureContentHeight(font, style);
        int panelHeight = Math.min(maxPanelHeight, Math.max(Math.max(INVENTORY_HEIGHT, PANEL_DESIRED_HEIGHT), measuredContentHeight));
        int centeredTop = inventoryTop - Math.max(0, panelHeight - INVENTORY_HEIGHT) / 2;
        int panelTop = clamp(centeredTop, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, guiHeight - panelHeight - SCREEN_MARGIN));
        int textLeft = panelLeft + style.innerPadding();
        int contentRight = panelLeft + panelWidth - style.innerPadding();
        int y = panelTop + style.innerPadding();

        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xD0101622);
        drawBorder(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, 0xFF42607F);

        y = drawScaledString(guiGraphics, font, translated("hud.eltenaaddon.inventory.title"), textLeft, y, 0xFFE6F2FF, style.titleScale());
        String levelJobValue = "Lv " + payload.level() + " / " + safeJobName(payload);
        y = drawScaledString(guiGraphics, font, literal(levelJobValue), textLeft, y + style.sectionGap(), 0xFFBFD5EC, style.bodyScale()) + style.sectionGap();

        y = drawScaledString(guiGraphics, font, translated("hud.eltenaaddon.inventory.basic"), textLeft, y, 0xFFF6D97D, style.bodyScale()) + style.lineGap();
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.hp", payload.hp() + " / " + payload.maxHp(), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.mp", payload.mp() + " / " + payload.maxMp(), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.job", safeJobName(payload), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.level", Integer.toString(payload.level()), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.exp", payload.exp() + " / " + payload.maxExp(), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());

        y += style.sectionGap();
        y = drawScaledString(guiGraphics, font, translated("hud.eltenaaddon.inventory.combat"), textLeft, y, 0xFFF6D97D, style.bodyScale()) + style.lineGap();
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.attack", Integer.toString(resolveBaseAttack(payload)), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.attack_weapon", formatValue(Math.max(0.0D, payload.weaponAttack())), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.attack_power", formatValue(Math.max(0.0D, payload.attackPower())), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.defense", formatValue(Math.max(0.0D, ModSyncClient.getDefense())), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        y = drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.weapon_type", safeWeaponType(payload), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
        drawStatLine(guiGraphics, font, "hud.eltenaaddon.inventory.weapon_category", safeWeaponCategory(payload), textLeft, y, contentRight, style.bodyScale(), style.lineAdvance());
    }

    private static int drawAttributeBlock(
        GuiGraphics guiGraphics,
        Font font,
        MutableComponent label,
        String value,
        List<DetailLine> details,
        int left,
        int top,
        int right,
        float scale,
        int lineAdvance,
        int blockGap
    ) {
        int y = drawValueLine(guiGraphics, font, label, value, left, top, right, 0xFFE6F2FF, 0xFFFFFFFF, scale) + 1;
        for (DetailLine detail : details) {
            y = drawStatLine(guiGraphics, font, detail.labelKey(), detail.value(), left + 6, y, right, scale, lineAdvance);
        }
        return y + blockGap;
    }

    private static int drawStatLine(
        GuiGraphics guiGraphics,
        Font font,
        String labelKey,
        String value,
        int left,
        int top,
        int right,
        float scale,
        int lineAdvance
    ) {
        return drawValueLine(guiGraphics, font, translated(labelKey), value, left, top, right, 0xFFBFD5EC, 0xFFF5FAFF, scale)
            + Math.max(1, lineAdvance - 7);
    }

    private static int drawValueLine(
        GuiGraphics guiGraphics,
        Font font,
        MutableComponent label,
        String value,
        int left,
        int top,
        int right,
        int labelColor,
        int valueColor,
        float scale
    ) {
        MutableComponent valueComponent = literal(value);
        drawScaledStringAt(guiGraphics, font, label, left, top, labelColor, scale);
        int scaledWidth = scaledWidth(font, valueComponent, scale);
        drawScaledStringAt(guiGraphics, font, valueComponent, Math.max(left, right - scaledWidth), top, valueColor, scale);
        return top + Math.max(7, Math.round(font.lineHeight * scale));
    }

    private static int drawScaledString(
        GuiGraphics guiGraphics,
        Font font,
        MutableComponent text,
        int x,
        int y,
        int color,
        float scale
    ) {
        drawScaledStringAt(guiGraphics, font, text, x, y, color, scale);
        return y + Math.max(7, Math.round(font.lineHeight * scale));
    }

    private static void drawScaledStringAt(
        GuiGraphics guiGraphics,
        Font font,
        MutableComponent text,
        int x,
        int y,
        int color,
        float scale
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private static int scaledWidth(Font font, Component component, float scale) {
        return Math.round(font.width(component) * scale);
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static MutableComponent translated(String key, Object... args) {
        return AddonUiFont.translatable(key, args);
    }

    private static MutableComponent literal(String value) {
        return AddonUiFont.text(value);
    }

    private static String safeJobName(PlayerStatePayload payload) {
        if (payload.currentJobName() == null || payload.currentJobName().isBlank()) {
            return translated("hud.eltenaaddon.inventory.job_unknown").getString();
        }
        return payload.currentJobName();
    }

    private static String safeWeaponType(PlayerStatePayload payload) {
        if (payload.weaponType() == null || payload.weaponType().isBlank()) {
            return translated("hud.eltenaaddon.inventory.unset").getString();
        }
        return payload.weaponType();
    }

    private static String safeWeaponCategory(PlayerStatePayload payload) {
        if (payload.weaponCategory() == null || payload.weaponCategory().isBlank()) {
            return translated("hud.eltenaaddon.inventory.unset").getString();
        }
        return payload.weaponCategory();
    }

    private static String formatWhole(double value) {
        return Integer.toString((int) Math.round(value));
    }

    private static int resolveBaseAttack(PlayerStatePayload payload) {
        return payload.baseAttack() > 0 ? payload.baseAttack() : payload.attack();
    }

    private static String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return Integer.toString((int) Math.round(value));
        }
        return VALUE_FORMAT.format(value);
    }

    private static String plusPercent(double value) {
        return "+" + PERCENT_FORMAT.format(Math.max(0.0D, value) * 100.0D) + "%";
    }

    private static String plusPercentFromMultiplier(double multiplier) {
        return plusPercent(Math.max(0.0D, multiplier - 1.0D));
    }

    private static String percent(double value) {
        return PERCENT_FORMAT.format(Math.max(0.0D, value) * 100.0D) + "%";
    }

    private static DetailLine detail(String labelKey, String value) {
        return new DetailLine(labelKey, value);
    }

    private static PanelStyle chooseStyle(Font font, int panelWidth, int maxPanelHeight) {
        PanelStyle[] candidates = panelWidth < 118
            ? new PanelStyle[] {
                new PanelStyle(0.72F, 0.84F, 6, 7, 2, 1),
                new PanelStyle(0.68F, 0.80F, 6, 6, 1, 1),
                new PanelStyle(0.64F, 0.76F, 5, 6, 1, 0)
            }
            : new PanelStyle[] {
                new PanelStyle(0.78F, 0.92F, 8, 8, 3, 2),
                new PanelStyle(0.74F, 0.88F, 7, 7, 2, 1),
                new PanelStyle(0.70F, 0.84F, 6, 7, 2, 1),
                new PanelStyle(0.66F, 0.80F, 5, 6, 1, 0)
            };
        PanelStyle fallback = candidates[candidates.length - 1];
        for (PanelStyle candidate : candidates) {
            if (Math.max(INVENTORY_HEIGHT, Math.max(PANEL_DESIRED_HEIGHT, measureContentHeight(font, candidate))) <= maxPanelHeight) {
                return candidate;
            }
            fallback = candidate;
        }
        return fallback;
    }

    private static int measureContentHeight(Font font, PanelStyle style) {
        int y = style.innerPadding();
        y += scaledLineHeight(font, style.titleScale());
        y += style.sectionGap();
        y += scaledLineHeight(font, style.bodyScale());
        y += style.sectionGap();
        y += scaledLineHeight(font, style.bodyScale());
        y += style.lineGap();
        y += 7 * style.lineAdvance();
        y += style.sectionGap();
        y += scaledLineHeight(font, style.bodyScale());
        y += style.lineGap();
        for (int index = 0; index < 3; index++) {
            y += scaledLineHeight(font, style.bodyScale()) + 1;
            y += 3 * style.lineAdvance();
            y += style.blockGap();
        }
        return y + style.innerPadding();
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(6, Math.round(font.lineHeight * scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record DetailLine(String labelKey, String value) {
    }

    private record PanelStyle(
        float bodyScale,
        float titleScale,
        int innerPadding,
        int lineAdvance,
        int sectionGap,
        int blockGap
    ) {
        private int lineGap() {
            return Math.max(1, lineAdvance - 7);
        }
    }
}
