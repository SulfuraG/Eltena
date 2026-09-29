package com.eltena.addon.client.hud;

import java.util.List;
public final class RadialLayoutHelper {
    public static final int MAX_SLOTS = 8;
    public static final int SLOT_SIZE = 40;
    public static final int SLOT_RADIUS = 92;
    public static final int SLOT_ICON_SIZE = 24;
    public static final int CENTER_CARD_SIZE = 52;
    public static final int CENTER_ICON_SIZE = 30;
    public static final int PANEL_HALF_SIZE = 120;
    public static final int INNER_DEADZONE = 26;
    private RadialLayoutHelper() {
    }

    public static int slotX(int centerX, int slotIndex) {
        double radians = Math.toRadians(-90 + (slotIndex * 45));
        return centerX + (int) Math.round(Math.cos(radians) * SLOT_RADIUS) - SLOT_SIZE / 2;
    }

    public static int slotY(int centerY, int slotIndex) {
        double radians = Math.toRadians(-90 + (slotIndex * 45));
        return centerY + (int) Math.round(Math.sin(radians) * SLOT_RADIUS) - SLOT_SIZE / 2;
    }

    public static <T> T resolveSelection(List<T> visible, String currentId, java.util.function.Function<T, String> idResolver, int mouseX, int mouseY, int centerX, int centerY) {
        if (visible == null || visible.isEmpty()) {
            return null;
        }
        T existing = null;
        if (currentId != null && !currentId.isBlank()) {
            for (T entry : visible) {
                if (currentId.equals(idResolver.apply(entry))) {
                    existing = entry;
                    break;
                }
            }
        }
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if ((dx * dx) + (dy * dy) <= INNER_DEADZONE * INNER_DEADZONE && existing != null) {
            return existing;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double normalized = (angle + 90.0D + 360.0D) % 360.0D;
        int index = ((int) Math.round(normalized / 45.0D)) % MAX_SLOTS;
        if (index >= visible.size()) {
            index = visible.size() - 1;
        }
        return visible.get(index);
    }

    public static Integer resolveSelectionIndex(Integer currentIndex, int mouseX, int mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if ((dx * dx) + (dy * dy) <= INNER_DEADZONE * INNER_DEADZONE && currentIndex != null) {
            return clampIndex(currentIndex);
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double normalized = (angle + 90.0D + 360.0D) % 360.0D;
        int index = ((int) Math.round(normalized / 45.0D)) % MAX_SLOTS;
        return clampIndex(index);
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(MAX_SLOTS - 1, index));
    }
}
