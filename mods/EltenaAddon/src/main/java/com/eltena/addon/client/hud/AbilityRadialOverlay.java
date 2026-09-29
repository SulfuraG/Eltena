package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.EltenaAddonNetwork;
import com.eltena.addon.network.ModSyncClient;
import com.eltena.addon.network.model.AbilityRadialSlotPayload;
import com.eltena.addon.network.model.AbilityStatePayload;
import com.eltena.addon.network.model.AbilitySummaryPayload;
import com.eltena.addon.network.model.NotificationPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class AbilityRadialOverlay {
    private static final String DEBUG_PROPERTY = "eltena.syncDebug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";

    private static boolean open;
    private static int selectedSlot = 0;
    private static String selectedAbilityId = "";
    private static boolean hasSelectedAbilitySlot;

    private AbilityRadialOverlay() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(AbilityRadialOverlay::onRenderGuiLayerPost);
    }

    public static void setOpen(boolean value) {
        open = value;
    }

    public static boolean isOpen() {
        return open;
    }

    public static String selectedAbilityId() {
        return selectedAbilityId;
    }

    public static int selectedSlotIndex() {
        return selectedSlot;
    }

    public static void castSelectedAbility() {
        AbilityStatePayload payload = ModSyncClient.getAbilityState();
        if (payload == null || payload.radialSlots() == null || payload.radialSlots().isEmpty()) {
            notifyLocal("hud.eltenaaddon.ability_selection.none", "warning");
            debug("Ability cast blocked: no radial state available.");
            return;
        }
        if (selectedAbilityId == null || selectedAbilityId.isBlank()) {
            AbilityRadialSlotPayload slot = selectedSlotPayload();
            if (hasSelectedAbilitySlot && slot != null && normalizedAbilityId(slot).isBlank()) {
                notifyLocal("hud.eltenaaddon.ability_selection.unassigned", "warning");
                debug("Ability cast blocked: selected slot is unassigned.");
                return;
            }
            notifyLocal("hud.eltenaaddon.ability_selection.none", "warning");
            debug("Ability cast blocked: no selected ability.");
            return;
        }
        AbilitySummaryPayload summary = selectedSummary();
        if (summary == null) {
            notifyLocal("hud.eltenaaddon.ability_selection.none", "warning");
            debug("Ability cast blocked: selected ability missing from unlocked list.");
            return;
        }
        debug("Ability cast request send abilityId=" + selectedAbilityId + " slot=" + selectedSlot);
        EltenaAddonNetwork.sendAbilityCastRequest(summary.id());
    }

    private static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        renderOnScreen(event.getGuiGraphics());
    }

    public static void renderOnScreen(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        if (!open) {
            renderSelectionHud(guiGraphics, minecraft);
            return;
        }
        List<AbilityRadialSlotPayload> visible = visibleSlots();
        if (visible.isEmpty()) {
            return;
        }
        RadialOverlayLayout layout = RadialOverlayLayout.create(minecraft, guiGraphics);
        int centerX = layout.centerX();
        int centerY = layout.centerY();
        int mouseX = layout.mouseX(minecraft);
        int mouseY = layout.mouseY(minecraft);
        AbilityRadialSlotPayload selected = resolveSelectedSlot(visible, mouseX, mouseY, centerX, centerY);
        if (selected == null) {
            return;
        }

        Font font = minecraft.font;
        AbilitySummaryPayload summary = summaryFor(selected.abilityId());
        double cooldownRemaining = cooldownRemaining(selected, summary);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(layout.fitScale(), layout.fitScale(), 1.0F);
        try {
            int panelHalf = RadialLayoutHelper.PANEL_HALF_SIZE;
            int centerCardSize = RadialLayoutHelper.CENTER_CARD_SIZE;
            int panelTop = centerY - panelHalf;
            int centerCardLeft = centerX - centerCardSize / 2;
            int centerCardTop = centerY - centerCardSize / 2;

            guiGraphics.fill(centerX - panelHalf, centerY - panelHalf, centerX + panelHalf, centerY + panelHalf, 0xA0111015);
            guiGraphics.fill(centerCardLeft - 10, centerCardTop - 10, centerCardLeft + centerCardSize + 10, centerCardTop + centerCardSize + 10, 0xC92D1D2A);

            for (AbilityRadialSlotPayload slot : visible) {
                int index = clampSlot(slot.slot());
                int x = RadialLayoutHelper.slotX(centerX, index);
                int y = RadialLayoutHelper.slotY(centerY, index);
                boolean selectedEntry = slot.slot() == selected.slot();
                AbilitySummaryPayload slotSummary = summaryFor(slot.abilityId());
                double slotCooldown = cooldownRemaining(slot, slotSummary);
                if (selectedEntry) {
                    drawSelectionGlow(guiGraphics, x, y, RadialLayoutHelper.SLOT_SIZE, 0x80FFD27A);
                }
                guiGraphics.fill(x, y, x + RadialLayoutHelper.SLOT_SIZE, y + RadialLayoutHelper.SLOT_SIZE, slotFill(slot, slotCooldown, selectedEntry));
                drawBorder(guiGraphics, x, y, RadialLayoutHelper.SLOT_SIZE, RadialLayoutHelper.SLOT_SIZE, slotBorder(slot, slotCooldown, selectedEntry));

                int iconX = x + (RadialLayoutHelper.SLOT_SIZE - RadialLayoutHelper.SLOT_ICON_SIZE) / 2;
                int iconY = y + 5;
                RadialIconRenderer.renderIcon(guiGraphics, font, slot.icon(), displayName(slot), iconX, iconY, RadialLayoutHelper.SLOT_ICON_SIZE);
                RadialIconRenderer.renderCooldownOverlay(guiGraphics, font, iconX, iconY, RadialLayoutHelper.SLOT_ICON_SIZE, slotCooldown, selectedEntry);
                guiGraphics.drawCenteredString(font, AddonUiFont.text(slot.directionLabel()), x + RadialLayoutHelper.SLOT_SIZE / 2, y + RadialLayoutHelper.SLOT_SIZE + 3, 0xFFD8E7F2);
            }

            guiGraphics.fill(centerCardLeft, centerCardTop, centerCardLeft + centerCardSize, centerCardTop + centerCardSize, 0xE41B1318);
            drawBorder(guiGraphics, centerCardLeft, centerCardTop, centerCardSize, centerCardSize, 0xFFFFD27A);
            int centerIconX = centerCardLeft + (centerCardSize - RadialLayoutHelper.CENTER_ICON_SIZE) / 2;
            int centerIconY = centerCardTop + (centerCardSize - RadialLayoutHelper.CENTER_ICON_SIZE) / 2;
            RadialIconRenderer.renderIcon(guiGraphics, font, selected.icon(), displayName(selected), centerIconX, centerIconY, RadialLayoutHelper.CENTER_ICON_SIZE);
            RadialIconRenderer.renderCooldownOverlay(guiGraphics, font, centerIconX, centerIconY, RadialLayoutHelper.CENTER_ICON_SIZE, cooldownRemaining, true);

            guiGraphics.drawCenteredString(font, AddonUiFont.text(displayName(selected)), centerX, centerY - 86, 0xFFFFFFFF);
            if (summary != null) {
                guiGraphics.drawCenteredString(
                    font,
                    CooldownDisplayHelper.abilityStatsLabel(summary.mpCost(), summary.cooldown()),
                    centerX,
                    centerY + 54,
                    0xFFF4D06F
                );
                guiGraphics.drawCenteredString(font, CooldownDisplayHelper.cooldownLabel(cooldownRemaining), centerX, centerY + 67, CooldownDisplayHelper.cooldownColor(cooldownRemaining));
                RadialDescriptionRenderer.render(guiGraphics, font, layout, centerX, panelTop, descriptionLines(summary));
            } else if (normalizedAbilityId(selected).isBlank()) {
                guiGraphics.drawCenteredString(font, AddonUiFont.translatable("hud.eltenaaddon.ability_selection.label"), centerX, centerY - 86, 0xFFFFFFFF);
                guiGraphics.drawCenteredString(font, AddonUiFont.translatable("hud.eltenaaddon.ability_selection.unassigned_short"), centerX, centerY + 54, 0xFFECD9B5);
                guiGraphics.drawCenteredString(font, AddonUiFont.translatable("hud.eltenaaddon.ability_selection.assign_hint"), centerX, centerY + 67, 0xFFF4D06F);
            } else {
                guiGraphics.drawCenteredString(font, AddonUiFont.text(selected.category()), centerX, centerY + 54, 0xFFF4D06F);
            }
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private static AbilityRadialSlotPayload resolveSelectedSlot(List<AbilityRadialSlotPayload> visible, int mouseX, int mouseY, int centerX, int centerY) {
        Integer resolved = RadialLayoutHelper.resolveSelectionIndex(selectedSlot, mouseX, mouseY, centerX, centerY);
        if (resolved == null) {
            return null;
        }
        selectedSlot = clampSlot(resolved);
        for (AbilityRadialSlotPayload slot : visible) {
            if (slot.slot() == selectedSlot) {
                hasSelectedAbilitySlot = true;
                selectedAbilityId = normalizedAbilityId(slot);
                return slot;
            }
        }
        AbilityRadialSlotPayload fallback = visible.get(0);
        selectedSlot = clampSlot(fallback.slot());
        hasSelectedAbilitySlot = true;
        selectedAbilityId = normalizedAbilityId(fallback);
        return fallback;
    }

    private static AbilityRadialSlotPayload selectedSlotPayload() {
        for (AbilityRadialSlotPayload slot : visibleSlots()) {
            if (slot.slot() == selectedSlot) {
                return slot;
            }
        }
        List<AbilityRadialSlotPayload> slots = visibleSlots();
        return slots.isEmpty() ? null : slots.get(0);
    }

    private static List<AbilityRadialSlotPayload> visibleSlots() {
        AbilityStatePayload payload = ModSyncClient.getAbilityState();
        if (payload == null || payload.radialSlots() == null || payload.radialSlots().isEmpty()) {
            return List.of();
        }
        return payload.radialSlots().stream()
            .sorted(Comparator.comparingInt(AbilityRadialSlotPayload::slot))
            .limit(RadialLayoutHelper.MAX_SLOTS)
            .toList();
    }

    private static AbilitySummaryPayload selectedSummary() {
        if (selectedAbilityId == null || selectedAbilityId.isBlank()) {
            AbilityRadialSlotPayload slot = selectedSlotPayload();
            if (slot == null || normalizedAbilityId(slot).isBlank()) {
                return null;
            }
            selectedAbilityId = normalizedAbilityId(slot);
        }
        return summaryFor(selectedAbilityId);
    }

    private static AbilitySummaryPayload summaryFor(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        AbilityStatePayload payload = ModSyncClient.getAbilityState();
        if (payload == null) {
            return null;
        }
        for (AbilitySummaryPayload ability : payload.unlockedAbilities()) {
            if (abilityId.equals(ability.id())) {
                return ability;
            }
        }
        return null;
    }

    private static List<RadialDescriptionRenderer.DescriptionLine> descriptionLines(AbilitySummaryPayload summary) {
        List<RadialDescriptionRenderer.DescriptionLine> lines = new ArrayList<>();
        if (summary == null || summary.description().isEmpty()) {
            return lines;
        }
        for (String descriptionLine : summary.description()) {
            if (descriptionLine == null || descriptionLine.isBlank()) {
                continue;
            }
            lines.add(new RadialDescriptionRenderer.DescriptionLine(
                AddonUiFont.text(descriptionLine),
                0xFFD8E7F2
            ));
        }
        return lines;
    }

    private static String displayName(AbilityRadialSlotPayload slot) {
        return slot.displayName() == null || slot.displayName().isBlank()
            ? AddonUiFont.translatable("hud.eltenaaddon.ability_selection.unassigned_short").getString()
            : slot.displayName();
    }

    private static int slotFill(AbilityRadialSlotPayload slot, double cooldownRemaining, boolean selected) {
        if (selected && cooldownRemaining > 0.0D) {
            return 0xD07A5A1F;
        }
        if (selected) {
            return 0xD08B6C2B;
        }
        if (normalizedAbilityId(slot).isBlank()) {
            return 0x66333A42;
        }
        if (cooldownRemaining > 0.0D) {
            return 0xAA453522;
        }
        return 0xAA2B2631;
    }

    private static int slotBorder(AbilityRadialSlotPayload slot, double cooldownRemaining, boolean selected) {
        if (selected) {
            return 0xFFFFD27A;
        }
        if (normalizedAbilityId(slot).isBlank()) {
            return 0xFF59626C;
        }
        if (cooldownRemaining > 0.0D) {
            return 0xFFFFD27A;
        }
        return 0xFF9A8D78;
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(RadialLayoutHelper.MAX_SLOTS - 1, slot));
    }

    private static void renderSelectionHud(GuiGraphics guiGraphics, Minecraft minecraft) {
        Font font = minecraft.font;
        AbilitySummaryPayload summary = selectedSummary();
        AbilityRadialSlotPayload slot = selectedSlotPayload();
        String icon = summary != null
            ? summary.icon()
            : (hasSelectedAbilitySlot && slot != null ? slot.icon() : "");
        double cooldownRemaining = cooldownRemaining(slot, summary);
        boolean unassigned = isUnassignedSlot(slot, summary);
        Component mp = unassigned ? AddonUiFont.text("") : CooldownDisplayHelper.mpLabel(summary == null ? 0 : summary.mpCost());
        Component cooldown = unassigned
            ? AddonUiFont.translatable("hud.eltenaaddon.ability_selection.assign_hint")
            : CooldownDisplayHelper.cooldownLabel(cooldownRemaining);
        Component hint = unassigned ? AddonUiFont.text("") : AddonUiFont.translatable("hud.eltenaaddon.ability_selection.cast_hint");

        SelectedActionHudRenderer.render(
            guiGraphics,
            font,
            0,
            icon,
            summary != null ? summary.displayName() : displayNameOrFallback(slot),
            AddonUiFont.translatable("hud.eltenaaddon.ability_selection.label"),
            selectedAbilityLabel(slot, summary),
            mp,
            cooldown,
            hint,
            0xB018131A,
            0xFF9A8D78,
            0xFFECD9B5,
            summary != null ? 0xFFFFFFFF : 0xFFB9A58A,
            unassigned ? 0xFFD8E7F2 : 0xFFF4D06F,
            unassigned ? 0xFFF4D06F : CooldownDisplayHelper.cooldownColor(cooldownRemaining),
            cooldownRemaining
        );
    }

    private static String displayNameOrFallback(AbilityRadialSlotPayload slot) {
        if (!hasSelectedAbilitySlot || slot == null) {
            return AddonUiFont.translatable("hud.eltenaaddon.ability_selection.none").getString();
        }
        return displayName(slot);
    }

    private static Component selectedAbilityLabel(AbilityRadialSlotPayload slot, AbilitySummaryPayload summary) {
        if (summary != null) {
            return AddonUiFont.text(summary.displayName());
        }
        if (hasSelectedAbilitySlot && slot != null && !normalizedAbilityId(slot).isBlank()) {
            return AddonUiFont.text(displayName(slot));
        }
        if (hasSelectedAbilitySlot && slot != null) {
            return AddonUiFont.translatable("hud.eltenaaddon.ability_selection.unassigned_short");
        }
        return AddonUiFont.translatable("hud.eltenaaddon.ability_selection.none");
    }

    private static boolean isUnassignedSlot(AbilityRadialSlotPayload slot, AbilitySummaryPayload summary) {
        return summary == null
            && hasSelectedAbilitySlot
            && slot != null
            && normalizedAbilityId(slot).isBlank();
    }

    private static double cooldownRemaining(AbilityRadialSlotPayload slot, AbilitySummaryPayload summary) {
        double summaryCooldownRemaining = ModSyncClient.getAbilityCooldownRemaining(summary);
        if (summaryCooldownRemaining > 0.0D) {
            return summaryCooldownRemaining;
        }
        double slotCooldownRemaining = ModSyncClient.getAbilityCooldownRemaining(slot);
        if (slotCooldownRemaining > 0.0D) {
            return slotCooldownRemaining;
        }
        if (summary != null) {
            return summaryCooldownRemaining;
        }
        return slot == null ? 0.0D : slotCooldownRemaining;
    }

    private static String normalizedAbilityId(AbilityRadialSlotPayload slot) {
        if (slot == null || slot.abilityId() == null || slot.abilityId().isBlank()) {
            return "";
        }
        return slot.abilityId();
    }

    private static void notifyLocal(String key, String level) {
        EltenaNotificationOverlay.enqueue(new NotificationPayload(
            AddonUiFont.translatable(key).getString(),
            level
        ));
    }

    private static void debug(String message) {
        if (Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY)) {
            System.out.println(
                "[EltenaAddon] " + message
                    + " selectedAbilityId=" + selectedAbilityId
                    + " selectedSlot=" + selectedSlot
                    + " abilityOpen=" + open
            );
        }
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private static void drawSelectionGlow(GuiGraphics guiGraphics, int x, int y, int size, int color) {
        guiGraphics.fill(x - 2, y - 2, x + size + 2, y, color);
        guiGraphics.fill(x - 2, y + size, x + size + 2, y + size + 2, color);
        guiGraphics.fill(x - 2, y, x, y + size, color);
        guiGraphics.fill(x + size, y, x + size + 2, y + size, color);
    }
}
