package com.eltena.addon.client.input;

import com.eltena.addon.client.hud.AbilityRadialOverlay;
import com.eltena.addon.client.hud.RadialInputController;
import com.eltena.addon.client.screen.EltenaMenuScreen;
import com.eltena.addon.client.screen.SkillTreeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class KeyInputHandler {
    private static final String DEBUG_PROPERTY = "eltena.syncDebug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";

    private static boolean waitForAbilityRelease;

    private KeyInputHandler() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(KeyInputHandler::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (EltenaKeyMappings.OPEN_MENU.consumeClick()) {
            toggleMenu(minecraft);
        }

        boolean abilityDown = EltenaKeyMappings.OPEN_ABILITY_RADIAL.isDown();
        if (!abilityDown) {
            waitForAbilityRelease = false;
        }

        if (minecraft.screen != null) {
            if (abilityDown) {
                waitForAbilityRelease = true;
            }
            RadialInputController.deactivate(minecraft, false);
        } else if (abilityDown && !waitForAbilityRelease) {
            RadialInputController.activate(minecraft, RadialInputController.Mode.ABILITY);
        } else {
            RadialInputController.deactivate(minecraft, true);
        }

        while (EltenaKeyMappings.CAST_SELECTED_ABILITY.consumeClick()) {
            if (isInputDebugEnabled()) {
                System.out.println(
                    "[EltenaAddon] N pressed"
                        + " abilityOpen=" + RadialInputController.isAbilityOpen()
                        + " selectedAbilityId=" + AbilityRadialOverlay.selectedAbilityId()
                        + " selectedSlot=" + AbilityRadialOverlay.selectedSlotIndex()
                );
            }
            if (minecraft.screen == null) {
                AbilityRadialOverlay.castSelectedAbility();
            }
        }
    }

    private static void toggleMenu(Minecraft minecraft) {
        Screen current = minecraft.screen;
        if (current instanceof EltenaMenuScreen) {
            minecraft.setScreen(null);
            return;
        }
        if (current == null || current instanceof SkillTreeScreen) {
            minecraft.setScreen(new EltenaMenuScreen());
        }
    }

    private static boolean isInputDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }
}
