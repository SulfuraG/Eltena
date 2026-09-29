package com.eltena.addon.client.hud;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.model.NotificationPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class EltenaNotificationOverlay {
    private static final long DISPLAY_MILLIS = 2500L;
    private static final long FADE_MILLIS = 450L;
    private static final int MAX_VISIBLE = 3;
    private static final int PADDING_X = 14;
    private static final int PADDING_Y = 8;
    private static final int GAP_Y = 6;
    private static final Queue<QueuedNotification> PENDING = new ArrayDeque<>();
    private static final List<ActiveNotification> ACTIVE = new ArrayList<>();

    private EltenaNotificationOverlay() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(EltenaNotificationOverlay::onRenderGuiLayerPost);
    }

    public static void enqueue(NotificationPayload payload) {
        if (payload == null || payload.message() == null || payload.message().isBlank()) {
            return;
        }
        PENDING.add(new QueuedNotification(payload.message(), NotificationLevel.from(payload.level())));
    }

    public static void renderOnScreen(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || guiGraphics == null) {
            return;
        }
        tickQueue();
        if (ACTIVE.isEmpty()) {
            return;
        }
        render(guiGraphics, minecraft.font);
    }

    private static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        renderOnScreen(event.getGuiGraphics());
    }

    private static void tickQueue() {
        long now = System.currentTimeMillis();
        Iterator<ActiveNotification> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveNotification active = iterator.next();
            if (active.isExpired(now)) {
                iterator.remove();
            }
        }
        while (ACTIVE.size() < MAX_VISIBLE && !PENDING.isEmpty()) {
            QueuedNotification queued = PENDING.poll();
            if (queued == null) {
                break;
            }
            ACTIVE.add(new ActiveNotification(queued.message(), queued.level(), now));
        }
    }

    private static void render(GuiGraphics guiGraphics, Font font) {
        int centerX = guiGraphics.guiWidth() / 2;
        int topY = 18;
        int y = topY;
        long now = System.currentTimeMillis();
        for (ActiveNotification notification : ACTIVE) {
            int alpha = notification.alpha(now);
            if (alpha <= 0) {
                continue;
            }
            Component text = AddonUiFont.text(notification.message());
            int textWidth = font.width(text);
            int boxWidth = Math.min(guiGraphics.guiWidth() - 24, textWidth + PADDING_X * 2);
            int boxHeight = font.lineHeight + PADDING_Y * 2;
            int x = centerX - boxWidth / 2;

            int backgroundColor = notification.level().background(alpha);
            int borderColor = notification.level().border(alpha);
            int textColor = notification.level().text(alpha);

            guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, backgroundColor);
            guiGraphics.fill(x + 1, y + 1, x + boxWidth - 1, y + boxHeight - 1, backgroundColor & 0xDFFFFFFF);
            drawBorder(guiGraphics, x, y, boxWidth, boxHeight, borderColor);
            guiGraphics.drawCenteredString(font, text, centerX, y + PADDING_Y, textColor);
            y += boxHeight + GAP_Y;
        }
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width, y, color);
        guiGraphics.hLine(x, x + width, y + height, color);
        guiGraphics.vLine(x, y, y + height, color);
        guiGraphics.vLine(x + width, y, y + height, color);
    }

    private record QueuedNotification(String message, NotificationLevel level) {
        private QueuedNotification {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(level, "level");
        }
    }

    private record ActiveNotification(String message, NotificationLevel level, long startedAt) {
        private boolean isExpired(long now) {
            return now - startedAt >= DISPLAY_MILLIS;
        }

        private int alpha(long now) {
            long elapsed = now - startedAt;
            long remaining = DISPLAY_MILLIS - elapsed;
            if (remaining <= 0L) {
                return 0;
            }
            if (remaining >= FADE_MILLIS) {
                return 255;
            }
            return (int) Math.max(0L, Math.min(255L, remaining * 255L / FADE_MILLIS));
        }
    }

    private enum NotificationLevel {
        SUCCESS(0x1A4E2D, 0x68E89D, 0xE9FFF3),
        WARNING(0x4A2818, 0xF3A15D, 0xFFF2E6),
        INFO(0x183246, 0x6FBFFF, 0xECF8FF);

        private final int backgroundRgb;
        private final int borderRgb;
        private final int textRgb;

        NotificationLevel(int backgroundRgb, int borderRgb, int textRgb) {
            this.backgroundRgb = backgroundRgb;
            this.borderRgb = borderRgb;
            this.textRgb = textRgb;
        }

        private static NotificationLevel from(String raw) {
            if (raw == null || raw.isBlank()) {
                return INFO;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "success" -> SUCCESS;
                case "warning" -> WARNING;
                default -> INFO;
            };
        }

        private int background(int alpha) {
            return withAlpha(alpha * 3 / 4, backgroundRgb);
        }

        private int border(int alpha) {
            return withAlpha(alpha, borderRgb);
        }

        private int text(int alpha) {
            return withAlpha(alpha, textRgb);
        }

        private static int withAlpha(int alpha, int rgb) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
        }
    }
}
