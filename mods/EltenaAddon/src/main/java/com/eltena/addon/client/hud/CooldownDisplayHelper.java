package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import java.util.Locale;
import net.minecraft.network.chat.Component;

final class CooldownDisplayHelper {
    private CooldownDisplayHelper() {
    }

    static Component cooldownLabel(double cooldownRemaining) {
        if (cooldownRemaining > 0.0D) {
            return AddonUiFont.translatable("hud.eltenaaddon.cooldown.seconds", formatSeconds(cooldownRemaining));
        }
        return AddonUiFont.translatable("hud.eltenaaddon.cooldown.ready");
    }

    static Component mpLabel(int mpCost) {
        return AddonUiFont.translatable("hud.eltenaaddon.mp.value", formatWholeNumber(mpCost));
    }

    static Component abilityStatsLabel(int mpCost, int baseCooldownSeconds) {
        return AddonUiFont.translatable(
            "hud.eltenaaddon.ability_selection.stats",
            formatWholeNumber(mpCost),
            formatWholeNumber(baseCooldownSeconds)
        );
    }

    static int cooldownColor(double cooldownRemaining) {
        return cooldownRemaining > 0.0D ? 0xFFFFD27A : 0xFF8FE3AA;
    }

    static String formatSeconds(double value) {
        return String.format(Locale.US, "%.1f", normalizeSeconds(value));
    }

    static String formatCompactSeconds(double value) {
        double normalized = normalizeSeconds(value);
        if (normalized >= 9.95D) {
            return Integer.toString((int) Math.ceil(normalized));
        }
        return formatSeconds(normalized);
    }

    static String formatWholeNumber(int value) {
        return Integer.toString(Math.max(0, value));
    }

    private static double normalizeSeconds(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0.0D;
        }
        return value;
    }
}
