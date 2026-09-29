package com.eltena.addon.client.screen;

import com.eltena.addon.client.hud.EltenaNotificationOverlay;
import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.EltenaAddonNetwork;
import com.eltena.addon.network.ModSyncClient;
import com.eltena.addon.network.model.AbilityRadialSlotPayload;
import com.eltena.addon.network.model.AbilityStatePayload;
import com.eltena.addon.network.model.AbilitySummaryPayload;
import com.eltena.addon.network.model.SkillTreePayload;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class EltenaMenuScreen extends Screen {
    private static final int SIDE_PADDING = 18;
    private static final int PANEL_GAP = 14;
    private static final int NAV_WIDTH = 212;
    private static final int RIGHT_WIDTH = 272;
    private static final int MENU_ITEM_HEIGHT = 48;
    private static final int MENU_ITEM_GAP = 8;
    private static final int ABILITY_SLOT_HEIGHT = 58;
    private static final int ABILITY_LIST_ITEM_HEIGHT = 34;

    private final List<MenuEntry> entries = List.of(
        new MenuEntry("menu.eltenaaddon.entry.skill_tree", "menu.eltenaaddon.entry.skill_tree.desc", SkillTreeScreen::new),
        new MenuEntry("menu.eltenaaddon.entry.ability", "menu.eltenaaddon.entry.ability.desc", null),
        new MenuEntry("menu.eltenaaddon.entry.profile", "menu.eltenaaddon.entry.profile.desc", ProfileScreen::new)
    );

    private int selectedIndex;
    private int selectedAbilitySlot;
    private int abilityListScroll;
    private String selectedAbilityId = "";

    public EltenaMenuScreen() {
        super(AddonUiFont.translatable("screen.eltenaaddon.menu"));
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
        renderLeftMenu(guiGraphics, virtualMouseX, virtualMouseY);
        renderCenterPanel(guiGraphics, virtualMouseX, virtualMouseY);
        renderRightPanel(guiGraphics);
        renderFooter(guiGraphics);
        super.render(guiGraphics, virtualMouseX, virtualMouseY, partialTick);
        guiGraphics.pose().popPose();
        EltenaNotificationOverlay.renderOnScreen(guiGraphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.minecraft != null) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            selectedIndex = Math.min(entries.size() - 1, selectedIndex + 1);
            return true;
        }
        if (isAbilityEntry()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                selectedAbilitySlot = Math.max(0, selectedAbilitySlot - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                selectedAbilitySlot = Math.min(7, selectedAbilitySlot + 1);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            openSelectedEntry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int virtualMouseX = AddonScreenLayout.toVirtualX(this, mouseX);
        int virtualMouseY = AddonScreenLayout.toVirtualY(this, mouseY);
        if (button == 0) {
            for (int index = 0; index < entries.size(); index++) {
                if (isInside(virtualMouseX, virtualMouseY, navLeft() + 10, menuItemY(index), NAV_WIDTH - 20, MENU_ITEM_HEIGHT)) {
                    selectedIndex = index;
                    if (!isAbilityEntry()) {
                        openSelectedEntry();
                    }
                    return true;
                }
            }
            if (isAbilityEntry() && handleAbilityClick(virtualMouseX, virtualMouseY)) {
                return true;
            }
        }
        return super.mouseClicked(virtualMouseX, virtualMouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int virtualMouseX = AddonScreenLayout.toVirtualX(this, mouseX);
        int virtualMouseY = AddonScreenLayout.toVirtualY(this, mouseY);
        if (isAbilityEntry() && isInside(virtualMouseX, virtualMouseY, centerLeft(), contentTop(), centerWidth(), contentHeight()) && scrollY != 0.0D) {
            int next = abilityListScroll + (scrollY < 0.0D ? 1 : -1);
            abilityListScroll = Math.max(0, Math.min(next, maxAbilityScroll()));
            return true;
        }
        return super.mouseScrolled(virtualMouseX, virtualMouseY, scrollX, scrollY);
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
        drawScaledString(guiGraphics, AddonUiFont.apply(this.title), rootLeft() + 20, rootTop() + 14, 0xFFFFFFFF, 1.16F);
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.sync.label"), rootRight() - 210, rootTop() + 18, 0xFFA9C8E8, 1.0F);
        drawScaledString(guiGraphics, AddonUiFont.text(currentSyncLabel()), rootRight() - 132, rootTop() + 18, 0xFFC7D2DF, 1.0F);
    }

    private void renderLeftMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanel(guiGraphics, navLeft(), contentTop(), NAV_WIDTH, contentHeight());
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.section.functions"), navLeft() + 14, contentTop() + 12, 0xFFF4D06F, 1.04F);
        for (int index = 0; index < entries.size(); index++) {
            MenuEntry entry = entries.get(index);
            int y = menuItemY(index);
            boolean selected = selectedIndex == index;
            boolean hovered = isInside(mouseX, mouseY, navLeft() + 10, y, NAV_WIDTH - 20, MENU_ITEM_HEIGHT);
            int fill = selected ? 0x7A509BD4 : hovered ? 0x4A325D87 : 0x2D0F1E2E;
            guiGraphics.fill(navLeft() + 10, y, navLeft() + NAV_WIDTH - 10, y + MENU_ITEM_HEIGHT, fill);
            drawBorder(guiGraphics, navLeft() + 10, y, NAV_WIDTH - 20, MENU_ITEM_HEIGHT, selected ? 0xFF4D8BC0 : 0xAA1F384C);
            drawScaledString(guiGraphics, AddonUiFont.translatable(entry.titleKey()), navLeft() + 20, y + 8, 0xFFEAF3FF, 1.02F);
            drawScaledString(guiGraphics, AddonUiFont.translatable(entry.descriptionKey()), navLeft() + 20, y + 24, 0xFFB6C7D8, 0.86F);
        }
    }

    private void renderCenterPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (isAbilityEntry()) {
            renderAbilityPanel(guiGraphics, mouseX, mouseY);
            return;
        }
        renderPlayerPreviewPanel(guiGraphics, mouseX, mouseY);
    }

    private void renderPlayerPreviewPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanel(guiGraphics, centerLeft(), contentTop(), centerWidth(), contentHeight());
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.section.preview"), centerLeft() + 14, contentTop() + 12, 0xFFF4D06F, 1.04F);
        int modelTop = contentTop() + 42;
        int modelHeight = contentHeight() - 88;
        int frameLeft = centerLeft() + 24;
        int frameTop = modelTop;
        int frameWidth = centerWidth() - 48;
        guiGraphics.fill(frameLeft, frameTop, frameLeft + frameWidth, frameTop + modelHeight, 0x70152333);
        drawBorder(guiGraphics, frameLeft, frameTop, frameWidth, modelHeight, 0xAA3B617F);
        Minecraft minecraft = Minecraft.getInstance();
        boolean rendered = false;
        if (minecraft.player != null) {
            rendered = PlayerModelRenderer.render(guiGraphics, centerLeft() + 36, modelTop + 10, centerWidth() - 72, modelHeight - 28, mouseX, mouseY, minecraft.player, 0.44F, 0.08F, 20);
        }
        if (!rendered) {
            renderPlayerPreviewFallback(guiGraphics, frameLeft, frameTop, frameWidth, modelHeight);
        }
        drawScaledCenteredString(guiGraphics, AddonUiFont.text(ModSyncClient.getPlayerName()), centerLeft() + centerWidth() / 2, contentTop() + contentHeight() - 38, 0xFFEAF3FF, 1.04F);
        drawScaledCenteredString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.profile.line", ModSyncClient.getLevel(), ModSyncClient.getCurrentJobName()), centerLeft() + centerWidth() / 2, contentTop() + contentHeight() - 22, 0xFFF4D06F, 0.94F);
    }

    private void renderPlayerPreviewFallback(GuiGraphics guiGraphics, int left, int top, int width, int height) {
        int textX = left + 22;
        int y = top + 18;
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.player_panel"), textX, y, 0xFFEAF3FF, 1.00F);
        y += 22;
        drawScaledString(guiGraphics, AddonUiFont.text(ModSyncClient.getPlayerName()), textX, y, 0xFFFFFFFF, 1.10F);
        y += 20;
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.progress.profile_level", ModSyncClient.getLevel()), textX, y, 0xFFF4D06F, 0.94F);
        y += 16;
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.progress.profile_job", ModSyncClient.getCurrentJobName()), textX, y, 0xFFB6C7D8, 0.92F);
        y += 16;
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.progress.profile_hpmp", ModSyncClient.getHp(), ModSyncClient.getMaxHp(), ModSyncClient.getMp(), ModSyncClient.getMaxMp()), textX, y, 0xFF9DE9C0, 0.90F);
        y += 18;
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.summary.current_title"), textX, y, 0xFFA9C8E8, 0.90F);
        y += 13;
        drawScaledString(guiGraphics, AddonUiFont.text(profileValue(ModSyncClient.getCurrentTitleName())), textX + 6, y, 0xFFEAF3FF, 0.90F);
        y += 16;
        drawScaledString(guiGraphics, AddonUiFont.translatable("screen.eltenaaddon.profile.summary.world_title"), textX, y, 0xFFA9C8E8, 0.90F);
        y += 13;
        drawScaledString(guiGraphics, AddonUiFont.text(profileValue(ModSyncClient.getWorldTitleName())), textX + 6, y, 0xFFEAF3FF, 0.90F);
    }

    private void renderAbilityPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanel(guiGraphics, centerLeft(), contentTop(), centerWidth(), contentHeight());
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.ability.panel.title"), centerLeft() + 14, contentTop() + 12, 0xFFF4D06F, 1.04F);
        int slotWidth = (centerWidth() - 58) / 4;
        int slotGap = 8;
        int slotTop = contentTop() + 42;
        for (int slot = 0; slot < 8; slot++) {
            int row = slot / 4;
            int column = slot % 4;
            int x = centerLeft() + 14 + column * (slotWidth + slotGap);
            int y = slotTop + row * (ABILITY_SLOT_HEIGHT + 8);
            renderAbilitySlot(guiGraphics, slot, x, y, slotWidth, ABILITY_SLOT_HEIGHT);
        }
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.ability.panel.unlocked"), centerLeft() + 14, contentTop() + 178, 0xFFEAF3FF, 0.96F);
        int listTop = contentTop() + 198;
        int listHeight = contentBottom() - listTop - 12;
        renderAbilityList(guiGraphics, mouseX, mouseY, centerLeft() + 14, listTop, centerWidth() - 28, listHeight);
    }

    private void renderAbilitySlot(GuiGraphics guiGraphics, int slot, int x, int y, int width, int height) {
        AbilityRadialSlotPayload equipped = equippedSlot(slot);
        boolean selected = slot == selectedAbilitySlot;
        guiGraphics.fill(x, y, x + width, y + height, selected ? 0x7A509BD4 : 0x2D0F1E2E);
        drawBorder(guiGraphics, x, y, width, height, selected ? 0xFF4D8BC0 : 0xAA1F384C);
        drawScaledCenteredString(guiGraphics, AddonUiFont.text(slotKeyLabel(slot)), x + width / 2, y + 8, 0xFFF4D06F, 1.0F);
        drawScaledCenteredString(guiGraphics, AddonUiFont.text(equipped == null || equipped.displayName().isBlank() ? AddonUiFont.translatable("menu.eltenaaddon.ability.slot.empty").getString() : equipped.displayName()), x + width / 2, y + 26, 0xFFEAF3FF, 0.92F);
        drawScaledCenteredString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.ability.slot.hint"), x + width / 2, y + 42, 0xFFB6C7D8, 0.82F);
    }

    private void renderAbilityList(GuiGraphics guiGraphics, int mouseX, int mouseY, int left, int top, int width, int height) {
        List<AbilitySummaryPayload> abilities = unlockedAbilities();
        int visible = Math.max(1, height / (ABILITY_LIST_ITEM_HEIGHT + 6));
        int start = Math.min(abilityListScroll, Math.max(0, abilities.size() - visible));
        int end = Math.min(abilities.size(), start + visible);
        for (int index = start; index < end; index++) {
            AbilitySummaryPayload ability = abilities.get(index);
            int y = top + (index - start) * (ABILITY_LIST_ITEM_HEIGHT + 6);
            boolean selected = ability.id().equals(selectedAbilityId);
            boolean hovered = isInside(mouseX, mouseY, left, y, width, ABILITY_LIST_ITEM_HEIGHT);
            guiGraphics.fill(left, y, left + width, y + ABILITY_LIST_ITEM_HEIGHT, selected ? 0x7A509BD4 : hovered ? 0x4A325D87 : 0x2D0F1E2E);
            drawBorder(guiGraphics, left, y, width, ABILITY_LIST_ITEM_HEIGHT, selected ? 0xFF4D8BC0 : 0xAA1F384C);
            drawScaledString(guiGraphics, AddonUiFont.text(ability.displayName()), left + 10, y + 6, 0xFFEAF3FF, 0.96F);
            drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.ability.line", ability.mpCost(), ability.cooldown()), left + 10, y + 20, 0xFFB6C7D8, 0.84F);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics) {
        drawPanel(guiGraphics, rightLeft(), contentTop(), RIGHT_WIDTH, contentHeight());
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.section.summary"), rightLeft() + 14, contentTop() + 12, 0xFFF4D06F, 1.04F);
        MenuEntry entry = entries.get(selectedIndex);
        int y = contentTop() + 38;
        drawScaledString(guiGraphics, AddonUiFont.translatable(entry.titleKey()), rightLeft() + 14, y, 0xFFEAF3FF, 1.06F);
        y += 18;
        drawScaledString(guiGraphics, AddonUiFont.translatable(entry.descriptionKey()), rightLeft() + 14, y, 0xFFB6C7D8, 0.88F);
        y += 24;
        for (SummaryLine line : summaryLines(entry)) {
            drawScaledString(guiGraphics, AddonUiFont.translatable(line.labelKey(), line.args()), rightLeft() + 14, y, line.color(), line.scale());
            y += line.advance();
        }
    }

    private void renderFooter(GuiGraphics guiGraphics) {
        int left = rootLeft() + 14;
        int top = rootBottom() - AddonScreenLayout.footerHeight() + 9;
        drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.footer.help"), left, top, 0xFFB6C7D8, 0.96F);
        if (isAbilityEntry()) {
            drawScaledString(guiGraphics, AddonUiFont.translatable("menu.eltenaaddon.ability.footer"), left, top + 16, 0xFFF4D06F, 0.90F);
        }
    }

    private List<SummaryLine> summaryLines(MenuEntry entry) {
        if ("menu.eltenaaddon.entry.skill_tree".equals(entry.titleKey())) {
            SkillTreePayload payload = ModSyncClient.getSkillTreeState();
            int total = payload == null ? 0 : payload.nodes().size();
            int learned = payload == null ? 0 : (int) payload.nodes().stream().filter(node -> node.currentRank() > 0).count();
            int points = payload == null ? 0 : payload.skillPoint();
            return List.of(
                new SummaryLine("menu.eltenaaddon.progress.skills", 0xFFEAF3FF, 0.96F, 16, learned, total),
                new SummaryLine("menu.eltenaaddon.progress.skill_points", 0xFFF4D06F, 0.92F, 14, points)
            );
        }
        if ("menu.eltenaaddon.entry.ability".equals(entry.titleKey())) {
            AbilityStatePayload payload = ModSyncClient.getAbilityState();
            int unlocked = payload == null ? 0 : payload.unlockedAbilities().size();
            int total = payload == null ? 0 : payload.totalAbilities();
            int assigned = payload == null ? 0 : (int) payload.radialSlots().stream().filter(slot -> slot.abilityId() != null && !slot.abilityId().isBlank()).count();
            return List.of(
                new SummaryLine("menu.eltenaaddon.progress.abilities", 0xFFEAF3FF, 0.96F, 16, unlocked, total),
                new SummaryLine("menu.eltenaaddon.progress.abilities_assigned", 0xFFF4D06F, 0.92F, 14, assigned)
            );
        }
        return List.of(
            new SummaryLine("menu.eltenaaddon.progress.profile_level", 0xFFEAF3FF, 0.96F, 16, ModSyncClient.getLevel()),
            new SummaryLine("menu.eltenaaddon.progress.profile_job", 0xFFF4D06F, 0.92F, 14, ModSyncClient.getCurrentJobName()),
            new SummaryLine("menu.eltenaaddon.progress.profile_combat", 0xFF9DE9C0, 0.92F, 14, ModSyncClient.getAttack(), formatStat(ModSyncClient.getWeaponAttack()), formatStat(ModSyncClient.getAttackPower()), formatStat(ModSyncClient.getDefense())),
            new SummaryLine("menu.eltenaaddon.progress.profile_hpmp", 0xFFB6C7D8, 0.90F, 14, ModSyncClient.getHp(), ModSyncClient.getMaxHp(), ModSyncClient.getMp(), ModSyncClient.getMaxMp())
        );
    }

    private boolean handleAbilityClick(int mouseX, int mouseY) {
        int slotWidth = (centerWidth() - 58) / 4;
        int slotGap = 8;
        int slotTop = contentTop() + 42;
        for (int slot = 0; slot < 8; slot++) {
            int row = slot / 4;
            int column = slot % 4;
            int x = centerLeft() + 14 + column * (slotWidth + slotGap);
            int y = slotTop + row * (ABILITY_SLOT_HEIGHT + 8);
            if (isInside(mouseX, mouseY, x, y, slotWidth, ABILITY_SLOT_HEIGHT)) {
                selectedAbilitySlot = slot;
                AbilitySummaryPayload equipped = equippedAbilitySummary(slot);
                selectedAbilityId = equipped == null ? "" : equipped.id();
                return true;
            }
        }

        List<AbilitySummaryPayload> abilities = unlockedAbilities();
        int listTop = contentTop() + 198;
        int visible = Math.max(1, (contentBottom() - listTop - 12) / (ABILITY_LIST_ITEM_HEIGHT + 6));
        int start = Math.min(abilityListScroll, Math.max(0, abilities.size() - visible));
        int end = Math.min(abilities.size(), start + visible);
        for (int index = start; index < end; index++) {
            int y = listTop + (index - start) * (ABILITY_LIST_ITEM_HEIGHT + 6);
            if (isInside(mouseX, mouseY, centerLeft() + 14, y, centerWidth() - 28, ABILITY_LIST_ITEM_HEIGHT)) {
                selectedAbilityId = abilities.get(index).id();
                EltenaAddonNetwork.sendAbilityAssignRadialRequest(selectedAbilitySlot, abilities.get(index).id());
                return true;
            }
        }
        return false;
    }

    private void openSelectedEntry() {
        MenuEntry entry = entries.get(selectedIndex);
        if (entry.targetFactory() != null && this.minecraft != null) {
            this.minecraft.setScreen(entry.targetFactory().get());
        }
    }

    private String currentSyncLabel() {
        if (!ModSyncClient.lastPlayerStateSyncSource().isBlank()) {
            return AddonUiFont.translatable("menu.eltenaaddon.sync.channel.player_state").getString();
        }
        if (!ModSyncClient.lastAbilityStateSyncSource().isBlank()) {
            return AddonUiFont.translatable("menu.eltenaaddon.sync.channel.ability_state").getString();
        }
        if (!ModSyncClient.lastSkillTreeSyncSource().isBlank()) {
            return AddonUiFont.translatable("menu.eltenaaddon.sync.channel.skill_tree").getString();
        }
        return AddonUiFont.translatable("menu.eltenaaddon.sync.channel.mock").getString();
    }

    private List<AbilitySummaryPayload> unlockedAbilities() {
        AbilityStatePayload payload = ModSyncClient.getAbilityState();
        return payload == null ? List.of() : payload.unlockedAbilities();
    }

    private AbilityRadialSlotPayload equippedSlot(int slot) {
        AbilityStatePayload payload = ModSyncClient.getAbilityState();
        if (payload == null) {
            return null;
        }
        return payload.radialSlots().stream().filter(entry -> entry.slot() == slot).findFirst().orElse(null);
    }

    private AbilitySummaryPayload equippedAbilitySummary(int slot) {
        AbilityRadialSlotPayload payload = equippedSlot(slot);
        if (payload == null || payload.abilityId() == null || payload.abilityId().isBlank()) {
            return null;
        }
        return unlockedAbilities().stream().filter(ability -> ability.id().equals(payload.abilityId())).findFirst().orElse(null);
    }

    private int maxAbilityScroll() {
        return Math.max(0, unlockedAbilities().size() - Math.max(1, (contentBottom() - (contentTop() + 198) - 12) / (ABILITY_LIST_ITEM_HEIGHT + 6)));
    }

    private boolean isAbilityEntry() {
        return "menu.eltenaaddon.entry.ability".equals(entries.get(selectedIndex).titleKey());
    }

    private int menuItemY(int index) {
        return contentTop() + 42 + index * (MENU_ITEM_HEIGHT + MENU_ITEM_GAP);
    }

    private String slotKeyLabel(int slot) {
        return Integer.toString(slot + 1);
    }

    private String formatStat(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String profileValue(String value) {
        return value == null || value.isBlank()
            ? AddonUiFont.translatable("screen.eltenaaddon.profile.value.unset").getString()
            : value;
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

    private void drawScaledCenteredString(GuiGraphics guiGraphics, Component text, int centerX, int y, int color, float scale) {
        int width = Math.round(this.font.width(text) * scale);
        drawScaledString(guiGraphics, text, centerX - width / 2, y, color, scale);
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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

    private int contentTop() {
        return AddonScreenLayout.contentTop(this) + 8;
    }

    private int contentBottom() {
        return AddonScreenLayout.contentBottom(this);
    }

    private int contentHeight() {
        return contentBottom() - contentTop();
    }

    private int navLeft() {
        return rootLeft() + SIDE_PADDING;
    }

    private int centerLeft() {
        return navLeft() + NAV_WIDTH + PANEL_GAP;
    }

    private int centerWidth() {
        return AddonScreenLayout.ROOT_WIDTH - NAV_WIDTH - RIGHT_WIDTH - SIDE_PADDING * 2 - PANEL_GAP * 2;
    }

    private int rightLeft() {
        return centerLeft() + centerWidth() + PANEL_GAP;
    }

    private record MenuEntry(String titleKey, String descriptionKey, Supplier<Screen> targetFactory) {
    }

    private record SummaryLine(String labelKey, int color, float scale, int advance, Object... args) {
    }
}
