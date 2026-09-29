package com.eltena.addon.client.screen;

import net.minecraft.client.gui.GuiGraphics;

final class TreeEdgeRenderer {
    private static final int LINE_THICKNESS = 2;

    private TreeEdgeRenderer() {
    }

    static void renderPolyline(GuiGraphics guiGraphics, int color, int... points) {
        if (points.length < 4 || points.length % 2 != 0) {
            return;
        }
        for (int index = 0; index < points.length - 2; index += 2) {
            int x1 = points[index];
            int y1 = points[index + 1];
            int x2 = points[index + 2];
            int y2 = points[index + 3];
            if (x1 == x2) {
                drawVertical(guiGraphics, x1, y1, y2, color);
            } else if (y1 == y2) {
                drawHorizontal(guiGraphics, x1, x2, y1, color);
            }
        }
    }

    static int clampLaneX(int startX, int endX, int desiredX, int lanePadding) {
        int minX = Math.min(startX, endX) + lanePadding;
        int maxX = Math.max(startX, endX) - lanePadding;
        if (minX > maxX) {
            return (startX + endX) / 2;
        }
        return Math.max(minX, Math.min(maxX, desiredX));
    }

    private static void drawHorizontal(GuiGraphics guiGraphics, int x1, int x2, int y, int color) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        guiGraphics.fill(minX, y, maxX + LINE_THICKNESS, y + LINE_THICKNESS, color);
    }

    private static void drawVertical(GuiGraphics guiGraphics, int x, int y1, int y2, int color) {
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        guiGraphics.fill(x, minY, x + LINE_THICKNESS, maxY + LINE_THICKNESS, color);
    }
}
