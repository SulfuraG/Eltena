package com.eltena.addon.client.hud;

import net.minecraft.client.Minecraft;

public final class RadialInputController {
    public enum Mode {
        NONE,
        ABILITY
    }

    private static Mode activeMode = Mode.NONE;
    private static boolean releasedByController;

    private RadialInputController() {
    }

    public static Mode activeMode() {
        return activeMode;
    }

    public static boolean isAbilityOpen() {
        return activeMode == Mode.ABILITY;
    }

    public static void activate(Minecraft minecraft, Mode requestedMode) {
        if (minecraft == null || requestedMode == null || requestedMode == Mode.NONE) {
            deactivate(minecraft, true);
            return;
        }
        if (minecraft.screen != null) {
            deactivate(minecraft, false);
            return;
        }
        if (activeMode != requestedMode && !releasedByController) {
            minecraft.mouseHandler.releaseMouse();
            releasedByController = true;
        }
        activeMode = requestedMode;
        syncOverlayFlags();
    }

    public static void deactivate(Minecraft minecraft, boolean restoreMouse) {
        activeMode = Mode.NONE;
        syncOverlayFlags();
        if (minecraft == null) {
            releasedByController = false;
            return;
        }
        if (releasedByController && restoreMouse && minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
            minecraft.mouseHandler.setIgnoreFirstMove();
            releasedByController = false;
            return;
        }
        if (restoreMouse) {
            releasedByController = false;
        }
    }

    private static void syncOverlayFlags() {
        AbilityRadialOverlay.setOpen(activeMode == Mode.ABILITY);
    }
}
