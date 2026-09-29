package com.eltena.effect.effect;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

public final class ScreenEffectManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenaeffect.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";
    private static final double FULL_CIRCLE = Math.PI * 2.0D;
    private static final double MAX_FOV_OFFSET = 12.5D;
    private static final List<ActiveScreenEffect> ACTIVE = new ArrayList<>();

    private ScreenEffectManager() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(ScreenEffectManager::onClientTick);
        eventBus.addListener(ScreenEffectManager::onRenderGuiPre);
        eventBus.addListener(ScreenEffectManager::onRenderGui);
        eventBus.addListener(ScreenEffectManager::onComputeFov);
    }

    public static void enqueue(ScreenEffectPayload payload) {
        if (payload == null || payload.visualPattern() == ScreenVisualPattern.UNKNOWN) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ActiveScreenEffect active = new ActiveScreenEffect(
            payload,
            Util.getMillis(),
            computeSeed(payload)
        );
        ACTIVE.add(active);

        if (debugEnabled(payload)) {
            LOGGER.info(
                "Eltena screen effect start: effectId={} visualPattern={} durationMs={} strength={}",
                payload.effectId(),
                payload.visualPattern().name(),
                payload.durationMs(),
                formatDouble(payload.strength())
            );
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ACTIVE.clear();
            return;
        }

        long now = Util.getMillis();
        Iterator<ActiveScreenEffect> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveScreenEffect active = iterator.next();
            if (active.isExpired(now)) {
                iterator.remove();
                continue;
            }
            active.maybeLogFadeProgress(now);
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        if (graphics == null) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = Util.getMillis();
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        double maxDistance = Math.hypot(centerX, centerY);

        for (ActiveScreenEffect active : ACTIVE) {
            double intensity = active.intensity(now);
            if (intensity <= 0.0D) {
                continue;
            }

            if (usesShaderPipeline(active)) {
                if (ScreenEffectShaderManager.hasUsableShader(active.payload())) {
                    continue;
                }
                renderShaderFallback(graphics, active, intensity, now, width, height);
                continue;
            }

            switch (active.payload().visualPattern()) {
                case ROAR_DISTORTION -> renderRoarPressure(
                    graphics,
                    active,
                    intensity,
                    now,
                    width,
                    height
                );
                case SHOCKWAVE_RING -> renderShockwavePressure(
                    graphics,
                    active,
                    intensity,
                    now,
                    width,
                    height,
                    centerX,
                    centerY,
                    maxDistance
                );
                case HIT_STOP_FEEL -> renderHitStopFeel(graphics, active, intensity, now, width, height, centerX, centerY);
                case BLOOD_PRESSURE -> renderBloodPressure(graphics, active, intensity, now, width, height);
                case DARK_PRESSURE -> renderDarkPressure(graphics, active, intensity, now, width, height);
                case WORLD_EVENT_WARNING -> renderWorldEventWarning(graphics, active, intensity, now, width, height);
                case DIMENSION_SHIFT -> renderDimensionShift(graphics, active, intensity, now, width, height);
                case VOID_COLLAPSE -> renderVoidCollapse(graphics, active, intensity, now, width, height);
                case HEAT_HAZE -> renderHeatHaze(graphics, active, intensity, now, width, height);
                case BLIZZARD_WHITEOUT -> renderBlizzardWhiteout(graphics, active, intensity, now, width, height);
                case UNDERWATER_PRESSURE -> renderUnderwaterPressure(graphics, active, intensity, now, width, height);
                case ENRAGE_AURA -> renderEnrageAura(graphics, active, intensity, now, width, height);
                case FEAR_WAVE -> renderFearWave(graphics, active, intensity, now, width, height);
                case TIME_DISTORTION -> renderTimeDistortion(graphics, active, intensity, now, width, height);
                case UNKNOWN -> {
                }
            }
        }
    }

    private static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        long now = Util.getMillis();
        for (ActiveScreenEffect active : ACTIVE) {
            if (!usesShaderPipeline(active)) {
                continue;
            }

            double intensity = active.intensity(now);
            if (intensity <= 0.0D || !ScreenEffectShaderManager.hasUsableShader(active.payload())) {
                continue;
            }

            double progress = active.progress(now);
            double seconds = active.elapsedSeconds(now);
            ShaderPhaseState phaseState = computeShaderPhaseState(
                active.payload().visualPattern(),
                progress,
                seconds,
                intensity,
                active.seed()
            );
            ScreenEffectShaderManager.applyShader(
                active.payload(),
                intensity,
                progress,
                seconds,
                phaseState.focusLoss(),
                phaseState.pressurePeak(),
                phaseState.aftershock()
            );
        }
    }

    private static boolean usesShaderPipeline(ActiveScreenEffect active) {
        ScreenEffectPayload.Shader shader = active.payload().shader();
        return shader != null && shader.active();
    }

    private static void renderShaderFallback(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        ShaderPhaseState phaseState = computeShaderPhaseState(
            active.payload().visualPattern(),
            progress,
            seconds,
            intensity,
            active.seed()
        );

        double tintAlphaCap = switch (active.payload().visualPattern()) {
            case ROAR_DISTORTION, SHOCKWAVE_RING, HIT_STOP_FEEL -> 0.060D;
            case BLOOD_PRESSURE, ENRAGE_AURA -> 0.070D;
            case DARK_PRESSURE, VOID_COLLAPSE -> 0.080D;
            case BLIZZARD_WHITEOUT -> 0.095D;
            case UNDERWATER_PRESSURE, HEAT_HAZE -> 0.055D;
            case WORLD_EVENT_WARNING, DIMENSION_SHIFT, FEAR_WAVE, TIME_DISTORTION -> 0.050D;
            case UNKNOWN -> 0.040D;
        };
        double accentAlphaCap = switch (active.payload().visualPattern()) {
            case BLIZZARD_WHITEOUT -> 0.085D;
            case HIT_STOP_FEEL, SHOCKWAVE_RING, ROAR_DISTORTION -> 0.055D;
            case DIMENSION_SHIFT, TIME_DISTORTION -> 0.040D;
            case DARK_PRESSURE, VOID_COLLAPSE -> 0.018D;
            case UNKNOWN -> 0.030D;
            default -> 0.032D;
        };

        double tintAlpha = clamp(
            ((phaseState.pressurePeak() * 0.028D) + (phaseState.aftershock() * 0.016D) + 0.008D)
                * Math.max(0.25D, intensity),
            0.0D,
            tintAlphaCap
        );
        double accentAlpha = clamp(
            (phaseState.focusLoss() * 0.024D) * Math.max(0.25D, intensity),
            0.0D,
            accentAlphaCap
        );

        double tintR = color.r();
        double tintG = color.g();
        double tintB = color.b();
        double accentR = 0.95D;
        double accentG = 0.97D;
        double accentB = 1.0D;

        switch (active.payload().visualPattern()) {
            case DARK_PRESSURE, VOID_COLLAPSE -> {
                tintR *= 0.60D;
                tintG *= 0.58D;
                tintB *= 0.72D;
                accentR = 0.18D;
                accentG = 0.22D;
                accentB = 0.28D;
            }
            case BLOOD_PRESSURE, ENRAGE_AURA -> {
                accentR = 1.0D;
                accentG = 0.24D;
                accentB = 0.22D;
            }
            case UNDERWATER_PRESSURE -> {
                accentR = 0.78D;
                accentG = 0.90D;
                accentB = 1.0D;
            }
            case HEAT_HAZE -> {
                accentR = 1.0D;
                accentG = 0.84D;
                accentB = 0.60D;
            }
            case BLIZZARD_WHITEOUT, HIT_STOP_FEEL, SHOCKWAVE_RING, ROAR_DISTORTION -> {
                accentR = 0.98D;
                accentG = 0.99D;
                accentB = 1.0D;
            }
            default -> {
            }
        }

        if (tintAlpha > 0.0D) {
            drawOverlay(graphics, width, height, argb(tintR, tintG, tintB, tintAlpha));
        }
        if (accentAlpha > 0.0D) {
            drawOverlay(graphics, width, height, argb(accentR, accentG, accentB, accentAlpha));
        }
    }

    private static ShaderPhaseState computeShaderPhaseState(
        ScreenVisualPattern visualPattern,
        double progress,
        double seconds,
        double intensity,
        double seed
    ) {
        return switch (visualPattern) {
            case ROAR_DISTORTION -> {
                RoarPhaseState roar = computeRoarPhaseState(progress, seconds, intensity, seed);
                yield shaderPhaseState(roar.focusLoss(), roar.pressurePeak(), roar.aftershock());
            }
            case SHOCKWAVE_RING -> shaderPhaseState(
                (Math.exp(-progress * 11.5D) * 1.18D) + (gaussianPulse(progress, 0.14D, 0.08D) * 0.38D),
                (gaussianPulse(progress, 0.16D, 0.09D) * 1.28D) + (Math.exp(-progress * 3.6D) * 0.26D),
                Math.abs(Math.sin((seconds * 17.0D) + seed)) * Math.exp(-progress * 3.2D) * 0.40D
            );
            case HIT_STOP_FEEL -> shaderPhaseState(
                (Math.exp(-progress * 18.0D) * 1.30D) + (gaussianPulse(progress, 0.10D, 0.05D) * 0.46D),
                Math.exp(-progress * 13.0D) * 1.10D,
                Math.abs(Math.sin((seconds * 26.0D) + seed)) * Math.exp(-progress * 7.0D) * 0.24D
            );
            case BLOOD_PRESSURE -> {
                double pulse = heartPulse(seconds, 1.18D, seed);
                yield shaderPhaseState(
                    0.26D + (pulse * 0.34D),
                    0.18D + (pulse * 0.22D),
                    0.12D + (Math.abs(Math.sin((seconds * 3.6D) + seed)) * 0.12D)
                );
            }
            case DARK_PRESSURE -> shaderPhaseState(
                0.22D + (Math.exp(-progress * 0.65D) * 0.18D),
                0.40D + ((1.0D - Math.exp(-progress * 3.2D)) * 0.70D),
                Math.abs(Math.sin((seconds * 1.9D) + seed)) * 0.28D
            );
            case WORLD_EVENT_WARNING -> shaderPhaseState(
                0.12D + (Math.exp(-progress * 0.55D) * 0.12D),
                0.18D + (Math.abs(Math.sin((seconds * 0.9D) + seed)) * 0.18D),
                0.10D + (Math.abs(Math.cos((seconds * 0.55D) + seed)) * 0.08D)
            );
            case DIMENSION_SHIFT -> shaderPhaseState(
                0.36D + (gaussianPulse(progress, 0.22D, 0.12D) * 0.54D),
                0.38D + (Math.abs(Math.sin((seconds * 6.2D) + seed)) * 0.30D),
                0.26D + (Math.abs(Math.cos((seconds * 4.4D) + (seed * 0.8D))) * 0.26D)
            );
            case VOID_COLLAPSE -> shaderPhaseState(
                0.28D + (gaussianPulse(progress, 0.30D, 0.16D) * 0.34D),
                0.42D + ((1.0D - Math.exp(-progress * 4.0D)) * 0.92D),
                Math.abs(Math.sin((seconds * 8.4D) + seed)) * Math.exp(-progress * 0.8D) * 0.46D
            );
            case HEAT_HAZE -> shaderPhaseState(
                0.08D + (Math.abs(Math.sin((seconds * 2.0D) + seed)) * 0.12D),
                0.16D + (Math.abs(Math.sin((seconds * 1.4D) + seed)) * 0.20D),
                0.10D + (Math.abs(Math.cos((seconds * 2.6D) + seed)) * 0.14D)
            );
            case BLIZZARD_WHITEOUT -> shaderPhaseState(
                0.40D + (Math.exp(-progress * 0.5D) * 0.22D),
                0.24D + (Math.abs(Math.sin((seconds * 3.2D) + seed)) * 0.20D),
                0.14D + (Math.abs(Math.cos((seconds * 2.8D) + seed)) * 0.16D)
            );
            case UNDERWATER_PRESSURE -> shaderPhaseState(
                0.18D + (Math.abs(Math.sin((seconds * 1.3D) + seed)) * 0.16D),
                0.26D + (Math.abs(Math.cos((seconds * 1.1D) + seed)) * 0.22D),
                0.16D + (Math.abs(Math.sin((seconds * 2.2D) + seed)) * 0.18D)
            );
            case ENRAGE_AURA -> {
                double pulse = heartPulse(seconds, 1.40D, seed);
                yield shaderPhaseState(
                    0.18D + (pulse * 0.20D),
                    0.34D + (pulse * 0.42D),
                    0.14D + (Math.abs(Math.sin((seconds * 10.0D) + seed)) * 0.26D)
                );
            }
            case FEAR_WAVE -> shaderPhaseState(
                0.38D + (gaussianPulse(progress, 0.16D, 0.10D) * 0.56D),
                0.18D + (Math.exp(-progress * 1.5D) * 0.24D),
                0.12D + (Math.abs(Math.sin((seconds * 5.8D) + seed)) * Math.exp(-progress * 1.6D) * 0.20D)
            );
            case TIME_DISTORTION -> shaderPhaseState(
                0.24D + (Math.abs(Math.sin((seconds * 8.0D) + seed)) * 0.24D),
                0.20D + (Math.abs(Math.cos((seconds * 5.8D) + seed)) * 0.20D),
                0.26D + (Math.abs(Math.sin((seconds * 12.0D) + seed)) * 0.34D)
            );
            case UNKNOWN -> shaderPhaseState(0.0D, 0.0D, 0.0D);
        };
    }

    private static ShaderPhaseState shaderPhaseState(
        double focusLoss,
        double pressurePeak,
        double aftershock
    ) {
        return new ShaderPhaseState(
            clamp(focusLoss, 0.0D, 2.0D),
            clamp(pressurePeak, 0.0D, 2.0D),
            clamp(aftershock, 0.0D, 2.0D)
        );
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        long now = Util.getMillis();
        double fovOffset = 0.0D;
        for (ActiveScreenEffect active : ACTIVE) {
            double intensity = active.intensity(now);
            if (intensity <= 0.0D) {
                continue;
            }

            double progress = active.progress(now);
            double seconds = active.elapsedSeconds(now);
            fovOffset += switch (active.payload().visualPattern()) {
                case ROAR_DISTORTION -> computeRoarFovOffset(progress, seconds, intensity, active.seed());
                case SHOCKWAVE_RING -> computeShockwaveFovOffset(progress, seconds, intensity);
                case HIT_STOP_FEEL -> computeHitStopFovOffset(progress, seconds, intensity);
                case BLOOD_PRESSURE -> computeBloodPressureFovOffset(progress, seconds, intensity, active.seed());
                case DARK_PRESSURE -> computeDarkPressureFovOffset(progress, seconds, intensity);
                case WORLD_EVENT_WARNING -> computeWorldEventWarningFovOffset(progress, seconds, intensity, active.seed());
                case DIMENSION_SHIFT -> computeDimensionShiftFovOffset(progress, seconds, intensity, active.seed());
                case VOID_COLLAPSE -> computeVoidCollapseFovOffset(progress, seconds, intensity, active.seed());
                case HEAT_HAZE -> computeHeatHazeFovOffset(progress, seconds, intensity, active.seed());
                case BLIZZARD_WHITEOUT -> computeBlizzardWhiteoutFovOffset(progress, seconds, intensity, active.seed());
                case UNDERWATER_PRESSURE -> computeUnderwaterPressureFovOffset(progress, seconds, intensity, active.seed());
                case ENRAGE_AURA -> computeEnrageAuraFovOffset(progress, seconds, intensity, active.seed());
                case FEAR_WAVE -> computeFearWaveFovOffset(progress, seconds, intensity);
                case TIME_DISTORTION -> computeTimeDistortionFovOffset(progress, seconds, intensity, active.seed());
                case UNKNOWN -> 0.0D;
            };
        }

        if (fovOffset == 0.0D) {
            return;
        }

        event.setFOV(event.getFOV() + clamp(fovOffset, -MAX_FOV_OFFSET, MAX_FOV_OFFSET));
    }

    private static void renderRoarPressure(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        RoarPhaseState phaseState = computeRoarPhaseState(progress, seconds, intensity, active.seed());
        double focusLoss = phaseState.focusLoss();
        double pressurePeak = phaseState.pressurePeak();
        double aftershock = phaseState.aftershock();
        boolean shaderActive = ScreenEffectShaderManager.hasUsableShader(active.payload());
        if (shaderActive) {
            return;
        }

        double whiteFlashAlpha = clamp(
            ((focusLoss * 0.040D) + (pressurePeak * 0.012D)) * Math.max(0.35D, intensity),
            0.0D,
            0.075D
        );
        double pressureTintAlpha = clamp(
            ((pressurePeak * 0.026D) + (aftershock * 0.014D)) * Math.max(0.25D, intensity),
            0.0D,
            0.050D
        );

        if (whiteFlashAlpha > 0.0D) {
            drawOverlay(graphics, width, height, argb(0.96D, 0.98D, 1.0D, whiteFlashAlpha));
        }
        if (pressureTintAlpha > 0.0D) {
            drawOverlay(
                graphics,
                width,
                height,
                argb(
                    clamp((color.r() * 0.70D) + 0.24D, 0.0D, 1.0D),
                    clamp((color.g() * 0.76D) + 0.18D, 0.0D, 1.0D),
                    clamp((color.b() * 0.82D) + 0.12D, 0.0D, 1.0D),
                    pressureTintAlpha
                )
            );
        }
    }

    private static RoarPhaseState computeRoarPhaseState(
        double progress,
        double seconds,
        double intensity,
        double seed
    ) {
        double pulse = 0.88D + (Math.sin((seconds * 7.2D) + seed) * 0.12D);
        double focusLoss = clamp(
            ((1.0D - clamp(progress / 0.16D, 0.0D, 1.0D)) * 1.30D)
                + (gaussianPulse(progress, 0.07D, 0.045D) * 0.95D)
                + (gaussianPulse(progress, 0.16D, 0.07D) * 0.25D),
            0.0D,
            1.95D
        );
        double pressurePeak = clamp(
            (gaussianPulse(progress, 0.14D, 0.07D) * 1.42D)
                + (gaussianPulse(progress, 0.26D, 0.10D) * 0.68D)
                + (Math.exp(-progress * 5.0D) * 0.52D),
            0.0D,
            1.85D
        );
        double aftershock = clamp(
            progress < 0.34D
                ? 0.0D
                : ((1.0D - clamp((progress - 0.34D) / 0.26D, 0.0D, 1.0D)) * 0.42D)
                    + (gaussianPulse(progress, 0.48D, 0.09D) * 0.18D),
            0.0D,
            0.58D
        );
        double blurWeight = ((focusLoss * 1.10D) + (pressurePeak * 0.98D) + (aftershock * 0.20D)) * intensity;
        double ghostWeight = ((focusLoss * 0.34D) + (pressurePeak * 0.92D) + (aftershock * 0.18D)) * intensity;
        double pressureWeight = ((pressurePeak * 1.24D) + (aftershock * 0.16D)) * intensity;
        double vignetteWeight = clamp((pressurePeak * 0.18D) + (aftershock * 0.06D), 0.0D, 0.28D) * intensity;
        return new RoarPhaseState(
            pulse,
            focusLoss,
            pressurePeak,
            aftershock,
            blurWeight,
            ghostWeight,
            pressureWeight,
            vignetteWeight
        );
    }

    private static void renderShockwavePressure(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height,
        double centerX,
        double centerY,
        double maxDistance
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double earlyWindow = clamp(1.0D - (progress / 0.18D), 0.0D, 1.0D);
        double flash = (Math.pow(earlyWindow, 0.45D) * 0.90D * intensity) + (Math.exp(-progress * 12.8D) * intensity * 0.25D);
        double afterglow = Math.exp(-progress * 2.6D) * intensity;

        drawOverlay(
            graphics,
            width,
            height,
            argb(
                0.98D,
                0.99D,
                1.0D,
                clamp((flash * 0.62D) + (afterglow * 0.06D), 0.0D, 0.82D)
            )
        );
        drawCenterBloom(
            graphics,
            centerX,
            centerY,
            width,
            height,
            flash * 1.15D,
            argb(0.98D, 0.99D, 1.0D, 0.24D + (flash * 0.28D))
        );
        drawShockwaveWall(
            graphics,
            width,
            height,
            centerX,
            centerY,
            seconds,
            progress,
            active.seed(),
            intensity * 1.08D,
            argb(0.88D, 0.95D, 1.0D, 0.12D + (afterglow * 0.09D))
        );
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            16.0D + (intensity * 14.0D),
            afterglow * 0.88D,
            argb(0.10D, 0.16D, 0.24D, 0.05D + (afterglow * 0.10D))
        );
        drawGhostingBands(graphics, width, height, seconds * 1.2D, active.seed() + 0.65D, intensity * 0.85D);
        drawChromaticFrame(graphics, width, height, seconds * 1.3D, active.seed() + 0.85D, intensity * 1.42D, 22.0D);
        drawWaveBands(
            graphics,
            width,
            height,
            seconds * 1.8D,
            active.seed() + 1.2D,
            flash + (afterglow * 0.72D),
            true,
            argb(0.90D, 0.96D, 1.0D, 0.042D + (flash * 0.05D)),
            22
        );
        drawBurstStreaks(
            graphics,
            centerX,
            centerY,
            maxDistance,
            width,
            height,
            seconds,
            progress,
            active.seed(),
            intensity,
            argb(0.94D, 0.98D, 1.0D, 0.11D),
            132
        );
        drawBurstStreaks(
            graphics,
            centerX,
            centerY,
            maxDistance,
            width,
            height,
            seconds + 0.18D,
            progress,
            active.seed() + 0.91D,
            intensity,
            argb(1.0D, 0.84D, 0.68D, 0.07D),
            88
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            flash + (afterglow * 0.90D),
            260,
            false,
            argb(color.r(), color.g(), color.b(), 0.048D)
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds + 0.27D,
            active.seed() + 0.43D,
            afterglow * 1.05D,
            220,
            false,
            argb(0.74D, 0.68D, 0.58D, 0.040D)
        );
    }

    private static void renderHitStopFeel(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height,
        double centerX,
        double centerY
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double snap = Math.exp(-progress * 10.5D) * intensity;
        double residual = Math.exp(-progress * 5.4D) * intensity;

        drawOverlay(graphics, width, height, argb(color, clamp((snap * 0.20D) + (residual * 0.03D), 0.0D, 0.26D)));
        drawCenterBloom(graphics, centerX, centerY, width, height, snap * 0.88D, argb(1.0D, 1.0D, 1.0D, 0.10D + (snap * 0.18D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            active.elapsedSeconds(now),
            active.seed(),
            16.0D + (intensity * 12.0D),
            residual * 0.88D,
            argb(0.02D, 0.02D, 0.04D, 0.07D + (residual * 0.12D))
        );
        drawGhostingBands(graphics, width, height, active.elapsedSeconds(now) * 1.3D, active.seed(), residual * 0.72D);
        drawWaveBands(
            graphics,
            width,
            height,
            active.elapsedSeconds(now) * 2.0D,
            active.seed() + 0.55D,
            snap * 0.75D,
            true,
            argb(1.0D, 1.0D, 1.0D, 0.028D + (snap * 0.022D)),
            12
        );
    }

    private static void renderBloodPressure(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double seconds = active.elapsedSeconds(now);
        double heart = heartPulse(seconds, 1.18D, active.seed());
        double pulse = intensity * (0.52D + (heart * 0.78D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.62D, color.g() * 0.22D, color.b() * 0.22D, 0.05D + (pulse * 0.07D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            34.0D + (pulse * 26.0D),
            pulse * 1.04D,
            argb(0.18D, 0.00D, 0.00D, 0.14D + (pulse * 0.14D))
        );
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds + 0.28D,
            active.seed() + 0.31D,
            24.0D + (pulse * 18.0D),
            pulse * 0.82D,
            argb(0.05D, 0.00D, 0.00D, 0.10D + (pulse * 0.10D))
        );
        drawGhostingBands(graphics, width, height, seconds * 0.85D, active.seed(), pulse * 0.45D);
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            pulse * 0.70D,
            180,
            true,
            argb(0.86D, 0.12D, 0.10D, 0.028D)
        );
    }

    private static void renderDarkPressure(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double pressure = intensity * (0.70D + (Math.sin((seconds * 1.1D) + active.seed()) * 0.10D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.22D, color.g() * 0.20D, color.b() * 0.32D, 0.10D + (pressure * 0.08D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            42.0D + (pressure * 32.0D),
            pressure * 1.16D,
            argb(0.00D, 0.00D, 0.00D, 0.18D + (pressure * 0.16D))
        );
        drawCompressionTunnel(graphics, width, height, progress, pressure * 0.95D, active.seed());
        drawWaveBands(
            graphics,
            width,
            height,
            seconds * 0.75D,
            active.seed(),
            pressure * 0.55D,
            true,
            argb(0.14D, 0.08D, 0.18D, 0.026D + (pressure * 0.020D)),
            12
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            pressure * 0.85D,
            200,
            true,
            argb(0.16D, 0.12D, 0.22D, 0.026D)
        );
    }

    private static void renderWorldEventWarning(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double seconds = active.elapsedSeconds(now);
        double warning = intensity * (0.86D + (Math.sin((seconds * 0.55D) + active.seed()) * 0.06D));

        drawOverlay(graphics, width, height, argb(color.r(), color.g(), color.b(), 0.04D + (warning * 0.05D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            18.0D + (warning * 12.0D),
            warning * 0.55D,
            argb(0.10D, 0.12D, 0.14D, 0.05D + (warning * 0.04D))
        );
        drawWaveBands(
            graphics,
            width,
            height,
            seconds * 0.45D,
            active.seed(),
            warning * 0.40D,
            true,
            argb(0.80D, 0.84D, 0.88D, 0.014D + (warning * 0.012D)),
            10
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            warning * 0.35D,
            120,
            false,
            argb(0.72D, 0.76D, 0.80D, 0.010D)
        );
    }

    private static void renderDimensionShift(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double shift = intensity * (0.78D + (Math.sin((seconds * 2.1D) + active.seed()) * 0.14D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.72D, color.g() * 0.82D, color.b(), 0.05D + (shift * 0.05D)));
        drawGhostingBands(graphics, width, height, seconds * 1.4D, active.seed(), shift * 0.90D);
        drawChromaticFrame(graphics, width, height, seconds * 1.2D, active.seed(), shift * 1.15D, 22.0D);
        drawDiagonalBands(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            shift * 0.82D,
            argb(0.74D, 0.88D, 1.0D, 0.026D + (shift * 0.020D)),
            12,
            0.22D
        );
        drawCompressionTunnel(graphics, width, height, progress, shift * 0.55D, active.seed() + 0.7D);
    }

    private static void renderVoidCollapse(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double collapse = intensity * (0.82D + (Math.sin((seconds * 1.7D) + active.seed()) * 0.12D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.24D, color.g() * 0.20D, color.b() * 0.28D, 0.10D + (collapse * 0.10D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            44.0D + (collapse * 34.0D),
            collapse * 1.20D,
            argb(0.00D, 0.00D, 0.00D, 0.20D + (collapse * 0.16D))
        );
        drawCompressionTunnel(graphics, width, height, progress, collapse * 1.05D, active.seed());
        drawFractureBars(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            collapse * 0.95D,
            argb(0.10D, 0.06D, 0.14D, 0.05D + (collapse * 0.04D)),
            16
        );
        drawGhostingBands(graphics, width, height, seconds, active.seed() + 0.45D, collapse * 0.44D);
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            collapse,
            220,
            true,
            argb(0.28D, 0.20D, 0.34D, 0.024D)
        );
    }

    private static void renderHeatHaze(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double seconds = active.elapsedSeconds(now);
        double haze = intensity * (0.76D + (Math.sin((seconds * 0.8D) + active.seed()) * 0.08D));

        drawOverlay(graphics, width, height, argb(color.r(), color.g(), color.b(), 0.03D + (haze * 0.03D)));
        drawWaveBands(
            graphics,
            width,
            height,
            seconds * 0.55D,
            active.seed(),
            haze * 0.72D,
            true,
            argb(1.0D, 0.78D, 0.36D, 0.018D + (haze * 0.012D)),
            16
        );
        drawDiagonalBands(
            graphics,
            width,
            height,
            seconds * 0.65D,
            active.seed() + 0.4D,
            haze * 0.55D,
            argb(0.96D, 0.56D, 0.20D, 0.010D + (haze * 0.010D)),
            8,
            0.10D
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            haze * 0.34D,
            100,
            false,
            argb(1.0D, 0.74D, 0.30D, 0.010D)
        );
    }

    private static void renderBlizzardWhiteout(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double seconds = active.elapsedSeconds(now);
        double whiteout = intensity * (0.84D + (Math.sin((seconds * 0.95D) + active.seed()) * 0.08D));

        drawOverlay(graphics, width, height, argb(color.r(), color.g(), color.b(), 0.05D + (whiteout * 0.06D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            24.0D + (whiteout * 18.0D),
            whiteout * 0.72D,
            argb(0.92D, 0.96D, 1.0D, 0.08D + (whiteout * 0.08D))
        );
        drawSweepLines(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            whiteout * 0.90D,
            argb(0.96D, 0.98D, 1.0D, 0.026D + (whiteout * 0.020D)),
            20,
            0.18D
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            whiteout * 0.78D,
            260,
            false,
            argb(0.96D, 0.98D, 1.0D, 0.018D)
        );
    }

    private static void renderUnderwaterPressure(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double pressure = intensity * (0.82D + (Math.sin((seconds * 0.9D) + active.seed()) * 0.08D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.72D, color.g() * 0.86D, color.b(), 0.07D + (pressure * 0.06D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            28.0D + (pressure * 20.0D),
            pressure * 0.94D,
            argb(0.02D, 0.08D, 0.14D, 0.12D + (pressure * 0.08D))
        );
        drawCompressionTunnel(graphics, width, height, progress, pressure * 0.42D, active.seed());
        drawWaveBands(
            graphics,
            width,
            height,
            seconds * 0.50D,
            active.seed(),
            pressure * 0.72D,
            true,
            argb(0.20D, 0.56D, 0.94D, 0.020D + (pressure * 0.016D)),
            14
        );
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            pressure * 0.44D,
            120,
            false,
            argb(0.34D, 0.72D, 1.0D, 0.014D)
        );
    }

    private static void renderEnrageAura(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double seconds = active.elapsedSeconds(now);
        double pulse = intensity * (0.56D + (heartPulse(seconds, 1.42D, active.seed()) * 0.90D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.70D, color.g() * 0.18D, color.b() * 0.18D, 0.04D + (pulse * 0.08D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            28.0D + (pulse * 24.0D),
            pulse * 1.05D,
            argb(0.10D, 0.00D, 0.00D, 0.12D + (pulse * 0.12D))
        );
        drawGhostingBands(graphics, width, height, seconds * 1.1D, active.seed(), pulse * 0.58D);
        drawChromaticFrame(graphics, width, height, seconds * 1.2D, active.seed(), pulse * 0.72D, 14.0D);
        drawPressureNoise(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            pulse * 0.66D,
            180,
            true,
            argb(0.92D, 0.18D, 0.10D, 0.024D)
        );
    }

    private static void renderFearWave(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double retreat = gaussianPulse(progress, 0.14D, 0.10D) * intensity;
        double pressure = intensity * (0.70D + retreat);

        drawOverlay(graphics, width, height, argb(color.r() * 0.78D, color.g() * 0.80D, color.b() * 0.84D, 0.05D + (pressure * 0.05D)));
        drawSegmentedVignette(
            graphics,
            width,
            height,
            seconds,
            active.seed(),
            34.0D + (pressure * 22.0D),
            pressure * 0.98D,
            argb(0.00D, 0.00D, 0.00D, 0.12D + (pressure * 0.10D))
        );
        drawGhostingBands(graphics, width, height, seconds * 0.95D, active.seed(), pressure * 0.78D);
        drawChromaticFrame(graphics, width, height, seconds, active.seed(), pressure * 0.62D, 12.0D);
        drawCompressionTunnel(graphics, width, height, progress, pressure * 0.62D, active.seed());
    }

    private static void renderTimeDistortion(
        GuiGraphics graphics,
        ActiveScreenEffect active,
        double intensity,
        long now,
        int width,
        int height
    ) {
        ScreenEffectPayload.Color color = active.payload().color();
        double progress = active.progress(now);
        double seconds = active.elapsedSeconds(now);
        double warp = intensity * (0.76D + (Math.sin((seconds * 1.6D) + active.seed()) * 0.10D));

        drawOverlay(graphics, width, height, argb(color.r() * 0.86D, color.g() * 0.90D, color.b() * 0.94D, 0.04D + (warp * 0.04D)));
        drawGhostingBands(graphics, width, height, seconds * 1.35D, active.seed(), warp * 0.95D);
        drawChromaticFrame(graphics, width, height, seconds * 0.9D, active.seed(), warp * 0.76D, 16.0D);
        drawDiagonalBands(
            graphics,
            width,
            height,
            seconds * 0.80D,
            active.seed() + 0.2D,
            warp * 0.58D,
            argb(0.84D, 0.88D, 0.96D, 0.016D + (warp * 0.014D)),
            10,
            -0.16D
        );
        drawCompressionTunnel(graphics, width, height, progress, warp * 0.35D, active.seed() + 0.5D);
    }

    private static double computeRoarFovOffset(
        double progress,
        double seconds,
        double intensity,
        double seed
    ) {
        double punchIn = gaussianPulse(progress, 0.10D, 0.08D) * intensity * -0.42D;
        double pressureSwell = gaussianPulse(progress, 0.32D, 0.15D) * intensity * 0.72D;
        double breathing = Math.sin((seconds * 4.8D) + seed) * intensity * 0.18D * Math.exp(-progress * 2.2D);
        double recovery = gaussianPulse(progress, 0.72D, 0.18D) * intensity * 0.16D;
        return punchIn + pressureSwell + breathing + recovery;
    }

    private static double computeShockwaveFovOffset(
        double progress,
        double seconds,
        double intensity
    ) {
        double punch = Math.exp(-progress * 13.6D) * intensity * 10.6D;
        double rebound = Math.exp(-progress * 5.0D) * intensity * -1.9D;
        double residual = Math.sin(seconds * 18.0D) * intensity * 0.62D * Math.exp(-progress * 3.3D);
        return punch + rebound + residual;
    }

    private static double computeHitStopFovOffset(double progress, double seconds, double intensity) {
        double compression = Math.exp(-progress * 18.0D) * intensity * -9.2D;
        double rebound = gaussianPulse(progress, 0.14D, 0.06D) * intensity * 2.8D;
        double residual = Math.sin(seconds * 24.0D) * intensity * 0.45D * Math.exp(-progress * 6.0D);
        return compression + rebound + residual;
    }

    private static double computeBloodPressureFovOffset(double progress, double seconds, double intensity, double seed) {
        double heartbeat = heartPulse(seconds, 1.16D, seed) * intensity * -1.1D;
        double residual = Math.sin((seconds * 3.6D) + seed) * intensity * 0.22D * Math.exp(-progress * 0.9D);
        return heartbeat + residual;
    }

    private static double computeDarkPressureFovOffset(double progress, double seconds, double intensity) {
        double collapse = (1.0D - Math.exp(-progress * 3.2D)) * intensity * -4.2D;
        double sway = Math.sin(seconds * 1.8D) * intensity * 0.32D * Math.exp(-progress * 0.5D);
        return collapse + sway;
    }

    private static double computeWorldEventWarningFovOffset(double progress, double seconds, double intensity, double seed) {
        double ambient = Math.sin((seconds * 0.9D) + seed) * intensity * 0.18D * Math.exp(-progress * 0.35D);
        return ambient + (intensity * -0.35D);
    }

    private static double computeDimensionShiftFovOffset(double progress, double seconds, double intensity, double seed) {
        double drift = Math.sin((seconds * 6.0D) + seed) * intensity * 0.95D * Math.exp(-progress * 0.9D);
        double rebound = Math.cos((seconds * 4.2D) + (seed * 0.8D)) * intensity * 0.42D * Math.exp(-progress * 1.3D);
        return drift + rebound;
    }

    private static double computeVoidCollapseFovOffset(double progress, double seconds, double intensity, double seed) {
        double collapse = (1.0D - Math.exp(-progress * 4.8D)) * intensity * -5.6D;
        double instability = Math.sin((seconds * 8.5D) + seed) * intensity * 0.72D * Math.exp(-progress * 1.5D);
        return collapse + instability;
    }

    private static double computeHeatHazeFovOffset(double progress, double seconds, double intensity, double seed) {
        return Math.sin((seconds * 2.1D) + seed) * intensity * 0.32D * Math.exp(-progress * 0.35D);
    }

    private static double computeBlizzardWhiteoutFovOffset(double progress, double seconds, double intensity, double seed) {
        double compression = intensity * -1.15D;
        double gust = Math.sin((seconds * 3.4D) + seed) * intensity * 0.26D * Math.exp(-progress * 0.4D);
        return compression + gust;
    }

    private static double computeUnderwaterPressureFovOffset(double progress, double seconds, double intensity, double seed) {
        double compression = intensity * -1.9D;
        double bob = Math.sin((seconds * 1.4D) + seed) * intensity * 0.28D * Math.exp(-progress * 0.25D);
        return compression + bob;
    }

    private static double computeEnrageAuraFovOffset(double progress, double seconds, double intensity, double seed) {
        double pulse = heartPulse(seconds, 1.40D, seed) * intensity * -2.0D;
        double jitter = Math.sin((seconds * 10.0D) + seed) * intensity * 0.42D * Math.exp(-progress * 0.9D);
        return pulse + jitter;
    }

    private static double computeFearWaveFovOffset(double progress, double seconds, double intensity) {
        double retreat = gaussianPulse(progress, 0.16D, 0.10D) * intensity * -5.1D;
        double after = Math.sin(seconds * 6.0D) * intensity * 0.44D * Math.exp(-progress * 2.0D);
        return retreat + after;
    }

    private static double computeTimeDistortionFovOffset(double progress, double seconds, double intensity, double seed) {
        double micro = Math.sin((seconds * 12.0D) + seed) * intensity * 0.42D * Math.exp(-progress * 0.9D);
        double dual = Math.cos((seconds * 8.4D) + (seed * 0.7D)) * intensity * 0.30D * Math.exp(-progress * 1.1D);
        return micro + dual;
    }

    private static void drawOverlay(
        GuiGraphics graphics,
        int width,
        int height,
        int argb
    ) {
        graphics.fill(0, 0, width, height, argb);
    }

    private static void drawSegmentedVignette(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double baseThickness,
        double intensity,
        int argb
    ) {
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        double outerRx = (width * 0.64D) + (baseThickness * 0.45D);
        double outerRy = (height * 0.66D) + (baseThickness * 0.48D);
        int layers = 7;

        for (int layer = 0; layer < layers; layer++) {
            double layerProgress = layer / (double) Math.max(1, layers - 1);
            double waveA = bandWave(layerProgress, seconds, seed, 0.7D);
            double waveB = bandWave(layerProgress, seconds * 0.72D, seed + 0.9D, 1.6D);
            double feather = 1.0D - layerProgress;
            double shellDepth = baseThickness * intensity * (0.24D + (feather * 0.64D));
            double wobbleX = waveA * baseThickness * 0.16D;
            double wobbleY = waveB * baseThickness * 0.18D;
            double innerRx = Math.max(width * 0.18D, (width * 0.50D) - shellDepth + wobbleX - (layer * 3.0D));
            double innerRy = Math.max(height * 0.18D, (height * 0.50D) - (shellDepth * 1.06D) + wobbleY - (layer * 3.0D));
            double layerOuterRx = outerRx + wobbleX + (layer * 4.0D);
            double layerOuterRy = outerRy + wobbleY + (layer * 4.0D);
            int layerColor = scaleAlpha(argb, (0.18D + (feather * 0.30D)) * Math.max(0.35D, intensity));
            drawSoftEllipseShell(
                graphics,
                width,
                height,
                centerX,
                centerY,
                layerOuterRx,
                layerOuterRy,
                innerRx,
                innerRy,
                layerColor
            );
        }
    }

    private static void drawCompressionTunnel(
        GuiGraphics graphics,
        int width,
        int height,
        double progress,
        double intensity,
        double seed
    ) {
        int layers = 8;
        double pulse = Math.exp(-progress * 1.2D);
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        for (int layer = 0; layer < layers; layer++) {
            double layerProgress = (layer + 1.0D) / layers;
            double baseRx = width * (0.16D + (layerProgress * 0.26D));
            double baseRy = height * (0.14D + (layerProgress * 0.24D));
            double swayX = Math.round(
                (Math.sin((progress * 12.0D) + (seed * (layer + 1.0D))) * 5.0D
                    + Math.cos((progress * 7.0D) + (layer * 0.8D) + seed) * 3.0D) * intensity
            );
            double swayY = Math.round(
                (Math.cos((progress * 9.0D) + (seed * (layer + 1.5D))) * 4.0D
                    + Math.sin((progress * 14.0D) + (layer * 0.6D) + seed) * 2.5D) * intensity
            );
            double alpha = (0.030D + (0.048D * pulse)) * (1.0D - (layerProgress * 0.06D));
            int layerColor = argb(0.01D, 0.03D, 0.10D, alpha);
            double shellWidthX = 12.0D + (layer * 3.2D * Math.max(0.7D, intensity));
            double shellWidthY = 14.0D + (layer * 3.4D * Math.max(0.7D, intensity));
            drawSoftEllipseShell(
                graphics,
                width,
                height,
                centerX + swayX,
                centerY + swayY,
                baseRx + shellWidthX,
                baseRy + shellWidthY,
                Math.max(width * 0.08D, baseRx - shellWidthX),
                Math.max(height * 0.08D, baseRy - shellWidthY),
                layerColor
            );
        }
    }

    private static void drawFocusBlurShells(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        int fillArgb,
        int shellArgb
    ) {
        if (intensity <= 0.0D) {
            return;
        }
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        int layers = 4;
        for (int layer = 0; layer < layers; layer++) {
            double layerProgress = layer / (double) Math.max(1, layers - 1);
            double driftScale = 3.5D + (intensity * 4.8D) + (layer * 1.8D);
            double driftX = Math.sin((seconds * 4.8D) + seed + layer) * driftScale;
            double driftY = Math.cos((seconds * 3.9D) + (seed * 0.8D) + layer) * driftScale * 0.78D;
            double radiusX = width * (0.17D + (layerProgress * 0.17D));
            double radiusY = height * (0.16D + (layerProgress * 0.16D));
            drawSoftEllipseFill(
                graphics,
                width,
                height,
                centerX + driftX,
                centerY + driftY,
                radiusX,
                radiusY,
                scaleAlpha(fillArgb, 0.26D + (layerProgress * 0.16D))
            );
            drawSoftEllipseShell(
                graphics,
                width,
                height,
                centerX - (driftX * 0.42D),
                centerY - (driftY * 0.36D),
                radiusX + (14.0D * Math.max(0.6D, intensity)),
                radiusY + (16.0D * Math.max(0.6D, intensity)),
                Math.max(width * 0.18D, radiusX - 12.0D),
                Math.max(height * 0.18D, radiusY - 12.0D),
                scaleAlpha(shellArgb, 0.28D + (layerProgress * 0.18D))
            );
        }
    }

    private static void drawRoarPressureShells(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double progress,
        double seed,
        double intensity,
        int argb
    ) {
        if (intensity <= 0.0D) {
            return;
        }
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        int shells = 5;
        for (int shell = 0; shell < shells; shell++) {
            double shellFront = clamp((progress * 1.42D) - (shell * 0.13D), 0.0D, 1.0D);
            if (shellFront <= 0.02D) {
                continue;
            }
            double envelope = Math.exp(-shellFront * 1.55D) * Math.max(0.22D, intensity * (1.0D - (shell * 0.08D)));
            double wobbleX = Math.sin((seconds * 2.9D) + (shell * 0.44D) + seed) * 4.0D * Math.max(0.7D, intensity);
            double wobbleY = Math.cos((seconds * 3.4D) + (shell * 0.36D) + seed) * 5.2D * Math.max(0.7D, intensity);
            double outerRx = width * (0.16D + (shellFront * 0.30D)) + (shell * 10.0D);
            double outerRy = height * (0.15D + (shellFront * 0.27D)) + (shell * 9.0D);
            double shellWidthX = 8.0D + (intensity * 8.0D) + (shell * 1.7D);
            double shellWidthY = 10.0D + (intensity * 8.8D) + (shell * 1.9D);
            drawSoftEllipseShell(
                graphics,
                width,
                height,
                centerX + wobbleX,
                centerY + wobbleY,
                outerRx + shellWidthX,
                outerRy + shellWidthY,
                Math.max(width * 0.17D, outerRx - shellWidthX),
                Math.max(height * 0.16D, outerRy - shellWidthY),
                scaleAlpha(argb, 0.24D + (envelope * 0.44D))
            );
        }
    }

    private static void drawRadialBlurStreaks(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double progress,
        double seed,
        double intensity,
        int argb,
        int rayCount
    ) {
        if (intensity <= 0.0D) {
            return;
        }
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        double maxDistance = Math.hypot(centerX, centerY);
        double innerRadius = maxDistance * (0.11D + (gaussianPulse(progress, 0.20D, 0.12D) * 0.05D));
        double streakLength = maxDistance * (0.18D + (0.16D * Math.max(0.7D, intensity)));

        for (int ray = 0; ray < rayCount; ray++) {
            double angle = ((ray / (double) rayCount) * FULL_CIRCLE)
                + (hashedSigned(seed, ray, 61L) * 0.16D)
                + (Math.sin((seconds * 1.2D) + (ray * 0.07D)) * 0.03D);
            double rayWeight = 0.58D + (hashedUnit(seed, ray, 62L) * 0.42D);
            int samples = 10;
            for (int sample = 0; sample < samples; sample++) {
                double travel = sample / (double) Math.max(1, samples - 1);
                double distance = innerRadius + (travel * streakLength * rayWeight);
                double edgeBias = Math.pow(travel, 1.35D);
                double lateral = hashedSigned(seed + seconds, (ray * 29) + sample, 63L)
                    * (4.0D + (intensity * 5.5D))
                    * (0.35D + (edgeBias * 0.65D));
                double x = centerX + (Math.cos(angle) * distance) - (Math.sin(angle) * lateral);
                double y = centerY + (Math.sin(angle) * distance) + (Math.cos(angle) * lateral);
                double radiusX = 6.0D + (edgeBias * 18.0D * Math.max(0.7D, intensity));
                double radiusY = 1.6D + (edgeBias * 3.8D * Math.max(0.6D, intensity));
                drawSoftBlob(
                    graphics,
                    width,
                    height,
                    x,
                    y,
                    radiusX,
                    radiusY,
                    scaleAlpha(argb, 0.16D + (edgeBias * 0.28D)),
                    2
                );
            }
        }
    }

    private static void drawWaveBands(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        boolean horizontal,
        int argb,
        int bandCount
    ) {
        int count = Math.max(4, bandCount);
        double majorAmplitude = (horizontal ? height : width) * 0.040D * intensity;
        double minorAmplitude = (horizontal ? width : height) * 0.085D * intensity;
        int segments = 20;

        for (int index = 0; index < count; index++) {
            double phase = index / (double) count;
            for (int segment = 0; segment < segments; segment++) {
                double travel = segment / (double) Math.max(1, segments - 1);
                double wave = bandWave(phase + (travel * 0.24D), seconds, seed, horizontal ? 0.0D : 2.1D);
                double curve = bandWave(travel, seconds * 0.78D, seed + (phase * 4.0D), horizontal ? 1.1D : 3.2D);
                double taper = 1.0D - Math.pow(Math.abs((travel * 2.0D) - 1.0D), 1.3D);
                double alphaFactor = (0.12D + (taper * 0.38D)) * Math.max(0.5D, intensity);
                int bandColor = scaleAlpha(argb, alphaFactor);

                if (horizontal) {
                    double x = travel * width;
                    double insetCurve = Math.sin((travel * FULL_CIRCLE * 0.65D) + (seconds * 1.4D) + seed) * minorAmplitude * 0.24D;
                    double edgeCurve = Math.pow(Math.abs((travel * 2.0D) - 1.0D), 1.25D) * height * 0.05D * Math.signum(curve);
                    double y = (phase * height) + (wave * majorAmplitude) + edgeCurve + insetCurve;
                    double radiusX = Math.max(6.0D, width / (segments * 1.5D));
                    double radiusY = Math.max(2.0D, (height * 0.006D) + (Math.abs(curve) * 4.0D * intensity));
                    drawSoftBlob(graphics, width, height, x, y, radiusX, radiusY, bandColor, 2);
                } else {
                    double y = travel * height;
                    double insetCurve = Math.cos((travel * FULL_CIRCLE * 0.70D) + (seconds * 1.5D) + seed) * minorAmplitude * 0.24D;
                    double edgeCurve = Math.pow(Math.abs((travel * 2.0D) - 1.0D), 1.20D) * width * 0.05D * Math.signum(curve);
                    double x = (phase * width) + (wave * majorAmplitude) + edgeCurve + insetCurve;
                    double radiusX = Math.max(2.0D, (width * 0.006D) + (Math.abs(curve) * 4.0D * intensity));
                    double radiusY = Math.max(6.0D, height / (segments * 1.5D));
                    drawSoftBlob(graphics, width, height, x, y, radiusX, radiusY, bandColor, 2);
                }
            }
        }
    }

    private static void drawChromaticFrame(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        double shiftPixels
    ) {
        double redShift = shiftPixels * intensity;
        double cyanShift = shiftPixels * 0.85D * intensity;
        double blueShift = shiftPixels * 0.55D * intensity;
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;

        drawSoftEllipseShell(
            graphics,
            width,
            height,
            centerX + (redShift * 0.28D),
            centerY - (redShift * 0.18D),
            (width * 0.62D) + redShift,
            (height * 0.63D) + (redShift * 0.92D),
            (width * 0.50D) - (redShift * 0.05D),
            (height * 0.51D) - (redShift * 0.04D),
            scaleAlpha(argb(1.0D, 0.22D, 0.18D, 0.050D + (intensity * 0.022D)), 0.85D)
        );
        drawSoftEllipseShell(
            graphics,
            width,
            height,
            centerX - (cyanShift * 0.24D),
            centerY + (cyanShift * 0.14D),
            (width * 0.61D) + cyanShift,
            (height * 0.62D) + (cyanShift * 0.90D),
            (width * 0.50D) - (cyanShift * 0.05D),
            (height * 0.51D) - (cyanShift * 0.04D),
            scaleAlpha(argb(0.36D, 0.92D, 1.0D, 0.046D + (intensity * 0.020D)), 0.82D)
        );
        drawSoftEllipseShell(
            graphics,
            width,
            height,
            centerX + (blueShift * 0.12D),
            centerY + (blueShift * 0.10D),
            (width * 0.60D) + blueShift,
            (height * 0.61D) + (blueShift * 0.88D),
            (width * 0.50D) - (blueShift * 0.04D),
            (height * 0.51D) - (blueShift * 0.04D),
            scaleAlpha(argb(0.28D, 0.44D, 1.0D, 0.036D + (intensity * 0.016D)), 0.78D)
        );
    }

    private static void drawGhostingBands(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity
    ) {
        int shiftX = Math.max(1, (int) Math.round(6.0D + (intensity * 8.0D) + (Math.sin(seconds * 5.0D + seed) * 2.5D)));
        int shiftY = Math.max(1, (int) Math.round(3.0D + (intensity * 4.0D) + (Math.cos(seconds * 4.0D + seed) * 2.0D)));
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;

        drawSoftEllipseShell(
            graphics,
            width,
            height,
            centerX + (shiftX * 0.40D),
            centerY + (shiftY * 0.28D),
            width * 0.58D,
            height * 0.58D,
            width * 0.46D,
            height * 0.46D,
            argb(1.0D, 0.28D, 0.24D, 0.026D + (intensity * 0.014D))
        );
        drawSoftEllipseShell(
            graphics,
            width,
            height,
            centerX - (shiftX * 0.36D),
            centerY - (shiftY * 0.24D),
            width * 0.58D,
            height * 0.58D,
            width * 0.46D,
            height * 0.46D,
            argb(0.36D, 0.94D, 1.0D, 0.024D + (intensity * 0.014D))
        );

        for (int index = 0; index < 6; index++) {
            double phase = index / 6.0D;
            double smearX = (phase * width) + (bandWave(phase, seconds, seed, 0.9D) * width * 0.12D);
            double smearY = (phase * height) + (bandWave(phase, seconds, seed, 1.4D) * height * 0.08D);
            double radiusX = width * (0.08D + (0.015D * intensity));
            double radiusY = 4.0D + (Math.abs(Math.sin((phase * 8.0D) + seconds + seed)) * 4.0D * intensity);
            drawSoftBlob(graphics, width, height, smearX + shiftX, smearY, radiusX, radiusY, argb(0.36D, 0.94D, 1.0D, 0.018D + (intensity * 0.010D)), 2);
            drawSoftBlob(graphics, width, height, smearX - shiftX, smearY + shiftY, radiusX, radiusY, argb(1.0D, 0.28D, 0.24D, 0.016D + (intensity * 0.010D)), 2);
        }
    }

    private static void drawPressureNoise(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        int particleCount,
        boolean edgeBiased,
        int argb
    ) {
        int count = Math.max(12, (int) Math.round(particleCount * 0.58D));
        double centerX = width * 0.5D;
        double centerY = height * 0.5D;
        double maxRadius = Math.min(centerX, centerY) * 0.98D;

        for (int index = 0; index < count; index++) {
            double angle = hashedUnit(seed, index, 1L) * FULL_CIRCLE;
            double distanceNorm = edgeBiased
                ? 0.72D + (hashedUnit(seed, index, 2L) * 0.28D)
                : 0.18D + (hashedUnit(seed, index, 2L) * 0.82D);
            double distance = maxRadius * distanceNorm;
            double drift = Math.sin((seconds * 4.0D) + (index * 0.37D) + seed) * intensity * 6.0D;
            double x = centerX + (Math.cos(angle) * distance) + drift;
            double y = centerY + (Math.sin(angle) * distance) - drift;
            double size = 1.8D + (hashedUnit(seed, index, 3L) * 5.0D * Math.max(0.55D, intensity));
            if (x < -size || y < -size || x >= width + size || y >= height + size) {
                continue;
            }
            drawSoftBlob(graphics, width, height, x, y, size * 0.95D, size * 0.72D, scaleAlpha(argb, 0.34D), 2);
            drawSoftBlob(
                graphics,
                width,
                height,
                x + (Math.cos(angle + 0.7D) * size * 0.6D),
                y + (Math.sin(angle + 0.7D) * size * 0.45D),
                size * 0.55D,
                size * 0.42D,
                scaleAlpha(argb, 0.18D),
                1
            );
        }
    }

    private static void drawCenterBloom(
        GuiGraphics graphics,
        double centerX,
        double centerY,
        int width,
        int height,
        double intensity,
        int argb
    ) {
        int layers = 6;
        for (int layer = 0; layer < layers; layer++) {
            double layerProgress = 1.0D - (layer / (double) layers);
            int layerColor = replaceAlpha(
                argb,
                clamp((alphaFromArgb(argb) / 255.0D) * (0.42D + (layerProgress * 0.58D)), 0.0D, 1.0D)
            );
            drawSoftEllipseFill(
                graphics,
                width,
                height,
                centerX,
                centerY,
                width * (0.10D + (layerProgress * 0.14D * intensity)),
                height * (0.08D + (layerProgress * 0.12D * intensity)),
                layerColor
            );
        }
    }

    private static void drawBurstStreaks(
        GuiGraphics graphics,
        double centerX,
        double centerY,
        double maxDistance,
        int width,
        int height,
        double seconds,
        double progress,
        double seed,
        double intensity,
        int argb,
        int rayCount
    ) {
        double frontDistance = maxDistance * clamp(0.14D + (progress * 1.02D), 0.14D, 1.08D);
        double tailLength = (maxDistance * 0.16D * intensity) + 28.0D;

        for (int ray = 0; ray < rayCount; ray++) {
            double rayNoise = hashedSigned(seed, ray, 7L);
            double angle = ((ray / (double) rayCount) * FULL_CIRCLE)
                + (rayNoise * 0.16D)
                + (Math.sin((seconds * 0.9D) + (ray * 0.18D)) * 0.04D);
            int samples = 9;
            for (int sample = 0; sample < samples; sample++) {
                double along = frontDistance - ((sample / (double) samples) * tailLength);
                if (along <= 0.0D) {
                    continue;
                }
                double lateral = hashedSigned(seed + seconds, ray * 31 + sample, 9L) * (6.0D + (intensity * 5.0D));
                double x = centerX + (Math.cos(angle) * along) - (Math.sin(angle) * lateral);
                double y = centerY + (Math.sin(angle) * along) + (Math.cos(angle) * lateral);
                double size = 1.4D + ((1.0D - (sample / (double) samples)) * 3.8D * Math.max(0.7D, intensity));
                if (x < -size || y < -size) {
                    continue;
                }
                drawSoftBlob(
                    graphics,
                    width,
                    height,
                    x,
                    y,
                    size * 0.95D,
                    Math.max(1.0D, size * 0.48D),
                    scaleAlpha(argb, 0.22D + ((1.0D - (sample / (double) samples)) * 0.35D)),
                    1
                );
            }
        }
    }

    private static void drawShockwaveWall(
        GuiGraphics graphics,
        int width,
        int height,
        double centerX,
        double centerY,
        double seconds,
        double progress,
        double seed,
        double intensity,
        int argb
    ) {
        int layers = 4;
        for (int layer = 0; layer < layers; layer++) {
            double layerOffset = layer * 0.07D;
            double front = clamp(progress + layerOffset, 0.0D, 1.0D);
            double envelope = Math.exp(-(front * 1.5D));
            double shockRx = width * (0.12D + (front * (0.42D + (layer * 0.06D))));
            double shockRy = height * (0.10D + (front * (0.36D + (layer * 0.05D))));
            double shellWidth = (18.0D + (intensity * 10.0D)) * (1.0D - (layer * 0.08D));
            double swayY = Math.sin((seconds * 6.5D) + seed + layer) * intensity * 12.0D;
            double swayX = Math.cos((seconds * 5.4D) + seed + (layer * 0.7D)) * intensity * 10.0D;
            int layerColor = replaceAlpha(argb, clamp((alphaFromArgb(argb) / 255.0D) * envelope, 0.0D, 1.0D));
            drawSoftEllipseShell(
                graphics,
                width,
                height,
                centerX + swayX,
                centerY + swayY,
                shockRx + shellWidth,
                shockRy + shellWidth,
                Math.max(width * 0.06D, shockRx - (shellWidth * 0.45D)),
                Math.max(height * 0.06D, shockRy - (shellWidth * 0.45D)),
                layerColor
            );

            for (int fragment = 0; fragment < 18; fragment++) {
                double angle = ((fragment / 18.0D) * FULL_CIRCLE)
                    + (hashedSigned(seed + layer, fragment, 51L) * 0.16D)
                    + (Math.sin(seconds * 1.7D + fragment + layer) * 0.02D);
                double radius = Math.max(shockRx, shockRy) * (0.92D + (hashedUnit(seed + layer, fragment, 52L) * 0.18D));
                double x = centerX + swayX + (Math.cos(angle) * radius);
                double y = centerY + swayY + (Math.sin(angle) * (shockRy * (radius / Math.max(1.0D, shockRx))));
                drawSoftBlob(
                    graphics,
                    width,
                    height,
                    x,
                    y,
                    6.0D + (intensity * 4.0D),
                    2.2D + (intensity * 1.8D),
                    scaleAlpha(layerColor, 0.34D),
                    1
                );
            }
        }
    }

    private static void drawDiagonalBands(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        int argb,
        int bandCount,
        double slope
    ) {
        int count = Math.max(4, bandCount);
        int segments = 18;
        for (int band = 0; band < count; band++) {
            double phase = band / (double) count;
            double baseY = (phase * height) + (bandWave(phase, seconds, seed, 0.7D) * height * 0.12D * Math.max(0.4D, intensity));
            for (int segment = 0; segment < segments; segment++) {
                double x0 = segment / (double) segments;
                double tilt = ((x0 - 0.5D) * slope * height) + (Math.sin((seconds * 1.8D) + (segment * 0.35D) + seed) * intensity * 4.0D);
                double x = x0 * width;
                double y = baseY + tilt + (bandWave(x0, seconds * 0.85D, seed + phase, 0.4D) * height * 0.03D * intensity);
                double radiusX = Math.max(6.0D, width / (segments * 1.8D));
                double radiusY = Math.max(2.0D, 2.0D + (Math.abs(Math.sin((phase * 11.0D) + seconds + seed)) * 3.8D * intensity));
                drawSoftBlob(graphics, width, height, x, y, radiusX, radiusY, scaleAlpha(argb, 0.38D), 2);
            }
        }
    }

    private static void drawFractureBars(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        int argb,
        int count
    ) {
        int bars = Math.max(4, count);
        int segments = 16;
        for (int bar = 0; bar < bars; bar++) {
            boolean fromLeft = hashedUnit(seed, bar, 31L) > 0.5D;
            double anchorY = hashedUnit(seed, bar, 32L) * height;
            double reach = width * (0.12D + (hashedUnit(seed, bar, 33L) * 0.22D * Math.max(0.6D, intensity)));
            double drift = Math.sin((seconds * 1.7D) + (bar * 0.42D) + seed) * height * 0.03D * intensity;

            for (int segment = 0; segment < segments; segment++) {
                double t0 = segment / (double) segments;
                double x = fromLeft ? (t0 * reach) : (width - (t0 * reach));
                double y = anchorY + drift + ((t0 - 0.5D) * height * 0.12D * hashedSigned(seed, bar, 35L));
                double taper = 1.0D - Math.abs((t0 * 2.0D) - 1.0D);
                double radiusX = 3.0D + (taper * 8.0D * intensity);
                double radiusY = 1.6D + (taper * 3.4D * intensity);
                drawSoftBlob(graphics, width, height, x, y, radiusX, radiusY, scaleAlpha(argb, 0.30D + (taper * 0.18D)), 1);
            }
        }
    }

    private static void drawSweepLines(
        GuiGraphics graphics,
        int width,
        int height,
        double seconds,
        double seed,
        double intensity,
        int argb,
        int lineCount,
        double slant
    ) {
        int count = Math.max(6, lineCount);
        int segments = 18;
        for (int line = 0; line < count; line++) {
            double phase = line / (double) count;
            double baseY = (phase * height) + (Math.sin((seconds * 2.4D) + (line * 0.31D) + seed) * height * 0.06D * intensity);
            for (int segment = 0; segment < segments; segment++) {
                double x0 = segment / (double) segments;
                double offset = ((x0 - 0.5D) * slant * height) + (Math.cos((seconds * 3.1D) + (segment * 0.45D) + seed) * intensity * 3.5D);
                double x = x0 * width;
                double y = baseY + offset;
                double taper = 1.0D - Math.pow(Math.abs((x0 * 2.0D) - 1.0D), 1.5D);
                double radiusX = Math.max(5.0D, width / (segments * 2.2D));
                double radiusY = 1.6D + (hashedUnit(seed, line * 31 + segment, 41L) * 3.4D * intensity);
                drawSoftBlob(graphics, width, height, x, y, radiusX, radiusY, scaleAlpha(argb, 0.26D + (taper * 0.18D)), 1);
            }
        }
    }

    private static void drawSoftEllipseFill(
        GuiGraphics graphics,
        int width,
        int height,
        double centerX,
        double centerY,
        double radiusX,
        double radiusY,
        int argb
    ) {
        if (radiusX <= 1.0D || radiusY <= 1.0D) {
            return;
        }
        double baseAlpha = alphaFromArgb(argb) / 255.0D;
        int top = clampInt((int) Math.floor(centerY - radiusY), 0, height - 1);
        int bottom = clampInt((int) Math.ceil(centerY + radiusY), 0, height - 1);

        for (int y = top; y <= bottom; y++) {
            double dy = (y + 0.5D) - centerY;
            double normalizedY = dy / radiusY;
            if (Math.abs(normalizedY) > 1.0D) {
                continue;
            }
            double span = radiusX * Math.sqrt(Math.max(0.0D, 1.0D - (normalizedY * normalizedY)));
            int left = clampInt((int) Math.floor(centerX - span), 0, width - 1);
            int right = clampInt((int) Math.ceil(centerX + span), left + 1, width);
            double edgeFade = Math.sqrt(Math.max(0.0D, 1.0D - (normalizedY * normalizedY)));
            graphics.fill(left, y, right, Math.min(y + 1, height), replaceAlpha(argb, baseAlpha * edgeFade));
        }
    }

    private static void drawSoftEllipseShell(
        GuiGraphics graphics,
        int width,
        int height,
        double centerX,
        double centerY,
        double outerRadiusX,
        double outerRadiusY,
        double innerRadiusX,
        double innerRadiusY,
        int argb
    ) {
        if (outerRadiusX <= 1.0D || outerRadiusY <= 1.0D) {
            return;
        }
        double clampedInnerRadiusX = Math.max(0.0D, Math.min(innerRadiusX, outerRadiusX - 1.0D));
        double clampedInnerRadiusY = Math.max(0.0D, Math.min(innerRadiusY, outerRadiusY - 1.0D));
        double baseAlpha = alphaFromArgb(argb) / 255.0D;
        int top = clampInt((int) Math.floor(centerY - outerRadiusY), 0, height - 1);
        int bottom = clampInt((int) Math.ceil(centerY + outerRadiusY), 0, height - 1);

        for (int y = top; y <= bottom; y++) {
            double dy = (y + 0.5D) - centerY;
            double normalizedOuterY = dy / outerRadiusY;
            if (Math.abs(normalizedOuterY) > 1.0D) {
                continue;
            }

            double outerSpan = outerRadiusX * Math.sqrt(Math.max(0.0D, 1.0D - (normalizedOuterY * normalizedOuterY)));
            double innerSpan = 0.0D;
            if (clampedInnerRadiusX > 1.0D && clampedInnerRadiusY > 1.0D && Math.abs(dy) < clampedInnerRadiusY) {
                double normalizedInnerY = dy / clampedInnerRadiusY;
                innerSpan = clampedInnerRadiusX * Math.sqrt(Math.max(0.0D, 1.0D - (normalizedInnerY * normalizedInnerY)));
            }

            int outerLeft = clampInt((int) Math.floor(centerX - outerSpan), 0, width - 1);
            int outerRight = clampInt((int) Math.ceil(centerX + outerSpan), outerLeft + 1, width);
            int lineColor = replaceAlpha(argb, baseAlpha * Math.sqrt(Math.max(0.0D, 1.0D - (normalizedOuterY * normalizedOuterY))));
            if (innerSpan <= 1.0D) {
                graphics.fill(outerLeft, y, outerRight, Math.min(y + 1, height), lineColor);
                continue;
            }

            int innerLeft = clampInt((int) Math.floor(centerX - innerSpan), outerLeft, width - 1);
            int innerRight = clampInt((int) Math.ceil(centerX + innerSpan), innerLeft + 1, width);
            if (outerLeft < innerLeft) {
                graphics.fill(outerLeft, y, innerLeft, Math.min(y + 1, height), lineColor);
            }
            if (innerRight < outerRight) {
                graphics.fill(innerRight, y, outerRight, Math.min(y + 1, height), lineColor);
            }
        }
    }

    private static void drawSoftBlob(
        GuiGraphics graphics,
        int width,
        int height,
        double centerX,
        double centerY,
        double radiusX,
        double radiusY,
        int argb,
        int layers
    ) {
        int count = Math.max(1, layers);
        for (int layer = 0; layer < count; layer++) {
            double layerProgress = 1.0D - (layer / (double) count);
            double layerRadiusX = radiusX * (0.55D + (layerProgress * 0.45D));
            double layerRadiusY = radiusY * (0.55D + (layerProgress * 0.45D));
            int layerColor = scaleAlpha(argb, 0.28D + (layerProgress * 0.46D));
            drawSoftEllipseFill(graphics, width, height, centerX, centerY, layerRadiusX, layerRadiusY, layerColor);
        }
    }

    private static void drawFrame(
        GuiGraphics graphics,
        int left,
        int top,
        int right,
        int bottom,
        int thickness,
        int argb
    ) {
        if (left >= right || top >= bottom || thickness <= 0) {
            return;
        }
        graphics.fill(left, top, right, clampInt(top + thickness, top + 1, bottom), argb);
        graphics.fill(left, clampInt(bottom - thickness, top, bottom - 1), right, bottom, argb);
        graphics.fill(left, top, clampInt(left + thickness, left + 1, right), bottom, argb);
        graphics.fill(clampInt(right - thickness, left, right - 1), top, right, bottom, argb);
    }

    private static int replaceAlpha(int argb, double alpha) {
        return (toColorChannel(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaleAlpha(int argb, double factor) {
        return replaceAlpha(argb, (alphaFromArgb(argb) / 255.0D) * clamp(factor, 0.0D, 1.5D));
    }

    private static int alphaFromArgb(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static double bandWave(double phase, double seconds, double seed, double phaseOffset) {
        return (Math.sin((phase * 10.0D) + (seconds * 3.2D) + seed + phaseOffset) * 0.48D)
            + (Math.cos((phase * 19.0D) - (seconds * 2.4D) + (seed * 0.7D) + phaseOffset) * 0.29D)
            + (Math.sin((phase * 33.0D) + (seconds * 5.2D) + (seed * 1.1D) - phaseOffset) * 0.17D)
            + (Math.cos((phase * 47.0D) - (seconds * 7.1D) + (seed * 0.5D)) * 0.09D);
    }

    private static double gaussianPulse(double value, double center, double sigma) {
        double distance = value - center;
        return Math.exp(-((distance * distance) / Math.max(0.0001D, 2.0D * sigma * sigma)));
    }

    private static double heartPulse(double seconds, double rate, double seed) {
        double phase = ((seconds * rate) + (seed * 0.03D)) % 1.0D;
        if (phase < 0.0D) {
            phase += 1.0D;
        }
        return gaussianPulse(phase, 0.10D, 0.07D) + (gaussianPulse(phase, 0.56D, 0.10D) * 0.72D);
    }

    private static double hashedUnit(double seed, int index, long salt) {
        long mixed = Double.doubleToLongBits(seed);
        mixed ^= ((long) index * 0x9E3779B97F4A7C15L);
        mixed ^= (salt * 0xBF58476D1CE4E5B9L);
        mixed ^= (mixed >>> 30);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 27);
        mixed *= 0x94D049BB133111EBL;
        mixed ^= (mixed >>> 31);
        return (mixed & 0xFFFFL) / 65535.0D;
    }

    private static double hashedSigned(double seed, int index, long salt) {
        return (hashedUnit(seed, index, salt) * 2.0D) - 1.0D;
    }

    private static int argb(ScreenEffectPayload.Color color, double alphaOverride) {
        return argb(color.r(), color.g(), color.b(), alphaOverride);
    }

    private static int argb(double r, double g, double b, double a) {
        int alpha = toColorChannel(a);
        int red = toColorChannel(r);
        int green = toColorChannel(g);
        int blue = toColorChannel(b);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int toColorChannel(double value) {
        return (int) Math.round(clamp(value, 0.0D, 1.0D) * 255.0D);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double computeSeed(ScreenEffectPayload payload) {
        long hash = 17L;
        hash = (31L * hash) + payload.effectId().hashCode();
        hash = (31L * hash) + payload.visualPattern().name().hashCode();
        return (hash & 0xFFFFL) / 191.0D;
    }

    private static boolean debugEnabled(ScreenEffectPayload payload) {
        return payload.debugLog() || Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ActiveScreenEffect {
        private final ScreenEffectPayload payload;
        private final long startedAtMs;
        private final double seed;
        private int lastLoggedBucket = -1;

        private ActiveScreenEffect(ScreenEffectPayload payload, long startedAtMs, double seed) {
            this.payload = payload;
            this.startedAtMs = startedAtMs;
            this.seed = seed;
        }

        private ScreenEffectPayload payload() {
            return payload;
        }

        private double seed() {
            return seed;
        }

        private boolean isExpired(long now) {
            return now >= startedAtMs + Math.max(1L, payload.durationMs());
        }

        private double progress(long now) {
            long duration = Math.max(1L, payload.durationMs());
            return clamp((now - startedAtMs) / (double) duration, 0.0D, 1.0D);
        }

        private double elapsedSeconds(long now) {
            return Math.max(0.0D, (now - startedAtMs) / 1000.0D);
        }

        private double intensity(long now) {
            long elapsed = now - startedAtMs;
            long duration = Math.max(1L, payload.durationMs());
            if (elapsed < 0L || elapsed >= duration) {
                return 0.0D;
            }
            return Math.max(0.0D, payload.strength()) * envelope(elapsed, duration);
        }

        private void maybeLogFadeProgress(long now) {
            if (!debugEnabled(payload)) {
                return;
            }

            int bucket = Math.min(10, (int) Math.floor(progress(now) * 10.0D));
            if (bucket == lastLoggedBucket) {
                return;
            }
            lastLoggedBucket = bucket;

            LOGGER.info(
                "Eltena screen effect fade progress: effectId={} visualPattern={} durationMs={} strength={} progress={} fadeProgress={}",
                payload.effectId(),
                payload.visualPattern().name(),
                payload.durationMs(),
                formatDouble(payload.strength()),
                (int) Math.round(progress(now) * 100.0D),
                (int) Math.round(currentFadeProgress(now) * 100.0D)
            );
        }

        private double currentFadeProgress(long now) {
            long elapsed = now - startedAtMs;
            long duration = Math.max(1L, payload.durationMs());
            if (elapsed < 0L || elapsed >= duration) {
                return 0.0D;
            }
            return envelope(elapsed, duration);
        }

        private double envelope(long elapsed, long duration) {
            long fadeIn = Math.max(0L, payload.fadeInMs());
            long fadeOut = Math.max(0L, payload.fadeOutMs());
            long fadeOutStart = Math.max(fadeIn, duration - fadeOut);

            if (fadeIn > 0L && elapsed < fadeIn) {
                double progress = elapsed / (double) fadeIn;
                return Math.sin(clamp(progress, 0.0D, 1.0D) * Math.PI * 0.5D);
            }
            if (fadeOut > 0L && elapsed >= fadeOutStart) {
                double progress = (elapsed - fadeOutStart) / (double) fadeOut;
                return Math.cos(clamp(progress, 0.0D, 1.0D) * Math.PI * 0.5D);
            }
            return 1.0D;
        }
    }

    private record RoarPhaseState(
        double pulse,
        double focusLoss,
        double pressurePeak,
        double aftershock,
        double blurWeight,
        double ghostWeight,
        double pressureWeight,
        double vignetteWeight
    ) {
    }

    private record ShaderPhaseState(
        double focusLoss,
        double pressurePeak,
        double aftershock
    ) {
    }
}
