package com.eltena.addon.client.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class RadialDescriptionRenderer {
    private static final int MAX_BOX_WIDTH = 420;
    private static final int SCREEN_MARGIN = 8;
    private static final int PANEL_GAP = 14;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 8;
    private static final int LINE_SPACING = 2;
    private static final int BACKGROUND_COLOR = 0xC0141A22;
    private static final int BORDER_COLOR = 0xA7D8E7F2;

    private RadialDescriptionRenderer() {
    }

    static void render(GuiGraphics guiGraphics, Font font, RadialOverlayLayout layout, int centerX, int panelTop, List<DescriptionLine> descriptionLines) {
        if (descriptionLines == null || descriptionLines.isEmpty()) {
            return;
        }
        int maxContentWidth = Math.max(
            120,
            Math.min(MAX_BOX_WIDTH - (PADDING_X * 2), layout.virtualScreenWidth() - (SCREEN_MARGIN * 2) - (PADDING_X * 2))
        );
        List<WrappedLine> wrappedLines = new ArrayList<>();
        int widestLine = 0;
        for (DescriptionLine descriptionLine : descriptionLines) {
            if (descriptionLine == null || descriptionLine.text() == null || descriptionLine.text().getString().isBlank()) {
                continue;
            }
            List<FormattedCharSequence> splitLines = font.split(descriptionLine.text(), maxContentWidth);
            for (FormattedCharSequence splitLine : splitLines) {
                widestLine = Math.max(widestLine, font.width(splitLine));
                wrappedLines.add(new WrappedLine(splitLine, descriptionLine.color()));
            }
        }
        if (wrappedLines.isEmpty()) {
            return;
        }
        int boxWidth = Math.min(layout.virtualScreenWidth() - (SCREEN_MARGIN * 2), Math.max(160, widestLine + (PADDING_X * 2)));
        int lineHeight = font.lineHeight + LINE_SPACING;
        int boxHeight = (PADDING_Y * 2) + (wrappedLines.size() * font.lineHeight) + ((wrappedLines.size() - 1) * LINE_SPACING);
        int boxX = Math.max(SCREEN_MARGIN, Math.min(centerX - (boxWidth / 2), layout.virtualScreenWidth() - SCREEN_MARGIN - boxWidth));
        int boxY = Math.max(SCREEN_MARGIN, panelTop - boxHeight - PANEL_GAP);

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BACKGROUND_COLOR);
        drawBorder(guiGraphics, boxX, boxY, boxWidth, boxHeight, BORDER_COLOR);

        int textY = boxY + PADDING_Y;
        for (WrappedLine wrappedLine : wrappedLines) {
            int textWidth = font.width(wrappedLine.text());
            int textX = boxX + Math.max(PADDING_X, (boxWidth - textWidth) / 2);
            guiGraphics.drawString(font, wrappedLine.text(), textX, textY, wrappedLine.color(), false);
            textY += lineHeight;
        }
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    record DescriptionLine(Component text, int color) {
    }

    private record WrappedLine(FormattedCharSequence text, int color) {
    }
}
