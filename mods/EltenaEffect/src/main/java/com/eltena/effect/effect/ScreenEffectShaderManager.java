package com.eltena.effect.effect;

import com.eltena.effect.EltenaEffect;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

public final class ScreenEffectShaderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SCREEN_LAYER_SHADER = ResourceLocation.fromNamespaceAndPath(
        EltenaEffect.MOD_ID,
        "screen_layer_post"
    );
    private static final int MAX_SHADER_SAMPLES = 24;

    private static ShaderInstance screenLayerShader;
    private static TextureTarget primaryScratchTarget;
    private static TextureTarget secondaryScratchTarget;
    private static boolean shaderLoadFailed;
    private static boolean runtimeDisabled;
    private static boolean warnedUnavailable;
    private static boolean warnedRuntimeFailure;
    private static final Set<String> WARNED_UNSUPPORTED_LAYERS = new HashSet<>();

    private ScreenEffectShaderManager() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ScreenEffectShaderManager::onRegisterShaders);
    }

    public static boolean hasUsableShader(ScreenEffectPayload payload) {
        return payload != null
            && payload.shader() != null
            && payload.shader().active()
            && hasSupportedLayerDefinition(payload.shader())
            && screenLayerShader != null
            && !runtimeDisabled
            && !shaderLoadFailed;
    }

    public static boolean applyShader(
        ScreenEffectPayload payload,
        double intensity,
        double progress,
        double seconds,
        double focusLoss,
        double pressurePeak,
        double aftershock
    ) {
        if (payload == null || payload.shader() == null || !payload.shader().active()) {
            return false;
        }
        if (runtimeDisabled || shaderLoadFailed || screenLayerShader == null) {
            warnUnavailableOnce(payload);
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) {
            return false;
        }

        try {
            primaryScratchTarget = prepareScratchTarget(minecraft, primaryScratchTarget);
            secondaryScratchTarget = prepareScratchTarget(minecraft, secondaryScratchTarget);

            LayerSelection selection = collectActiveLayers(payload, intensity, progress);
            if (!selection.hasSupportedLayer()) {
                warnUnavailableOnce(payload);
                return false;
            }
            if (selection.layers().isEmpty()) {
                return true;
            }

            copyRenderTarget(mainTarget, primaryScratchTarget);
            TextureTarget sourceTarget = primaryScratchTarget;
            TextureTarget bufferTarget = secondaryScratchTarget;

            for (int index = 0; index < selection.layers().size(); index++) {
                ActiveLayer layer = selection.layers().get(index);
                boolean lastLayer = index == selection.layers().size() - 1;
                RenderTarget destinationTarget = lastLayer ? mainTarget : bufferTarget;
                renderLayerPass(destinationTarget, sourceTarget, layer, progress, seconds, focusLoss, pressurePeak, aftershock);

                if (!lastLayer) {
                    TextureTarget previousSource = sourceTarget;
                    sourceTarget = bufferTarget;
                    bufferTarget = previousSource;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            runtimeDisabled = true;
            if (!warnedRuntimeFailure) {
                warnedRuntimeFailure = true;
                LOGGER.warn(
                    "EltenaEffect shader layer pipeline disabled after runtime failure for effectId={}. Falling back to minimal overlay/FOV support: {}",
                    payload.effectId(),
                    exception.getMessage(),
                    exception
                );
            }
            return false;
        }
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        shaderLoadFailed = false;
        runtimeDisabled = false;
        warnedUnavailable = false;
        warnedRuntimeFailure = false;
        WARNED_UNSUPPORTED_LAYERS.clear();
        screenLayerShader = null;

        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SCREEN_LAYER_SHADER, DefaultVertexFormat.BLIT_SCREEN),
                shader -> screenLayerShader = shader
            );
        } catch (IOException exception) {
            shaderLoadFailed = true;
            LOGGER.warn(
                "EltenaEffect failed to load shader {}. Disabling shader layer pipeline and falling back to minimal overlay/FOV support.",
                SCREEN_LAYER_SHADER,
                exception
            );
        }
    }

    private static TextureTarget prepareScratchTarget(Minecraft minecraft, TextureTarget existingTarget) {
        int width = Math.max(1, minecraft.getWindow().getWidth());
        int height = Math.max(1, minecraft.getWindow().getHeight());
        if (existingTarget == null) {
            TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.setFilterMode(9729);
            return target;
        }

        if (existingTarget.width != width || existingTarget.height != height) {
            existingTarget.resize(width, height, Minecraft.ON_OSX);
            existingTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            existingTarget.setFilterMode(9729);
        }
        return existingTarget;
    }

    private static LayerSelection collectActiveLayers(
        ScreenEffectPayload payload,
        double intensity,
        double progress
    ) {
        List<ScreenEffectPayload.Shader.Layer> declaredLayers = payload.shader().layersOrLegacy();
        if (declaredLayers.isEmpty()) {
            return new LayerSelection(List.of(), false);
        }

        List<ActiveLayer> activeLayers = new ArrayList<>();
        boolean hasSupportedLayer = false;
        for (int index = 0; index < declaredLayers.size(); index++) {
            ScreenEffectPayload.Shader.Layer layer = declaredLayers.get(index);
            if (!isSupportedLayerType(layer.type())) {
                warnUnsupportedLayerOnce(payload, index, layer.type());
                continue;
            }

            hasSupportedLayer = true;
            if (!layer.active()) {
                continue;
            }

            double windowProgress = computeWindowProgress(layer, progress);
            if (windowProgress < 0.0D) {
                continue;
            }
            double windowEnvelope = computeWindowEnvelope(windowProgress);
            double effectiveStrength = Math.max(0.0D, layer.strength()) * Math.max(0.0D, intensity) * windowEnvelope;
            if (effectiveStrength <= 0.0001D) {
                continue;
            }

            activeLayers.add(new ActiveLayer(
                layer.type(),
                (float) effectiveStrength,
                Math.max(1, Math.min(MAX_SHADER_SAMPLES, layer.samples())),
                (float) Math.max(0.0D, layer.radius()),
                (float) Math.max(0.0D, layer.frequency()),
                (float) Math.max(0.0D, layer.speed()),
                layer.edgeOnly() ? 1 : 0,
                layer.direction().uniformCode(),
                (float) layer.curve().apply(windowProgress),
                (float) layer.color().r(),
                (float) layer.color().g(),
                (float) layer.color().b(),
                (float) layer.color().a(),
                (float) Math.max(0.0D, layer.noiseStrength()),
                (float) Math.max(0.0D, layer.wobble()),
                (float) Math.max(0.0D, layer.blurScale()),
                (float) Math.max(0.0D, layer.refractionStrength()),
                (float) Math.max(0.0D, layer.desaturation()),
                (float) Math.max(0.0D, layer.smear()),
                (float) Math.max(0.0D, layer.pulse()),
                Math.max(0, Math.min(128, layer.count())),
                (float) Math.max(0.0D, layer.length()),
                (float) Math.max(0.0D, layer.sharpness()),
                (float) Math.max(0.0D, layer.centerBias()),
                (float) Math.max(0.0D, layer.edgeBias()),
                (float) Math.max(0.0D, layer.chromaticOffset())
            ));
        }

        return new LayerSelection(List.copyOf(activeLayers), hasSupportedLayer);
    }

    private static double computeWindowProgress(ScreenEffectPayload.Shader.Layer layer, double progress) {
        double start = layer.startProgress();
        double end = layer.endProgress();
        if (progress < start || progress > end || end <= start) {
            return -1.0D;
        }
        return clamp((progress - start) / Math.max(0.0001D, end - start), 0.0D, 1.0D);
    }

    private static double computeWindowEnvelope(double windowProgress) {
        double attack = clamp(windowProgress / 0.14D, 0.0D, 1.0D);
        double release = clamp((1.0D - windowProgress) / 0.18D, 0.0D, 1.0D);
        return attack * release;
    }

    private static boolean isSupportedLayerType(ScreenShaderType type) {
        return switch (type) {
            case RADIAL_BLUR,
                SONIC_PRESSURE_BURST,
                MOTION_BLUR,
                MOTION_BLUR_FAKE,
                PRESSURE_DISTORTION,
                SHOCKWAVE_REFRACTION,
                HEAT_REFRACTION,
                UNDERWATER_REFRACTION,
                CHROMATIC_ABERRATION,
                COLOR_GRADE,
                DESATURATION,
                WHITEOUT,
                DARK_VIGNETTE_SOFT,
                NOISE_FOG,
                SCREEN_SHAKE_WARP,
                GHOSTING,
                TIME_WARP,
                FRACTURE_DISTORTION -> true;
            case NONE -> false;
        };
    }

    private static boolean hasSupportedLayerDefinition(ScreenEffectPayload.Shader shader) {
        for (ScreenEffectPayload.Shader.Layer layer : shader.layersOrLegacy()) {
            if (isSupportedLayerType(layer.type())) {
                return true;
            }
        }
        return false;
    }

    private static void warnUnsupportedLayerOnce(
        ScreenEffectPayload payload,
        int layerIndex,
        ScreenShaderType type
    ) {
        String key = payload.effectId() + "#" + layerIndex + "#" + type.name();
        if (!WARNED_UNSUPPORTED_LAYERS.add(key)) {
            return;
        }
        LOGGER.warn(
            "EltenaEffect skipped unsupported shader layer. effectId={} layerIndex={} layerType={}",
            payload.effectId(),
            layerIndex,
            type.name()
        );
    }

    private static void copyRenderTarget(RenderTarget sourceTarget, RenderTarget destinationTarget) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._glBindFramebuffer(36008, sourceTarget.frameBufferId);
        GlStateManager._glBindFramebuffer(36009, destinationTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
            0,
            0,
            sourceTarget.width,
            sourceTarget.height,
            0,
            0,
            destinationTarget.width,
            destinationTarget.height,
            16384,
            9728
        );
        GlStateManager._glBindFramebuffer(36160, 0);
    }

    private static void renderLayerPass(
        RenderTarget destinationTarget,
        RenderTarget sourceTarget,
        ActiveLayer layer,
        double progress,
        double seconds,
        double focusLoss,
        double pressurePeak,
        double aftershock
    ) {
        destinationTarget.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        screenLayerShader.setSampler("DiffuseSampler", sourceTarget);
        screenLayerShader.safeGetUniform("LayerType").set(layer.typeCode());
        screenLayerShader.safeGetUniform("Strength").set(layer.strength());
        screenLayerShader.safeGetUniform("BlurRadius").set(layer.radius());
        screenLayerShader.safeGetUniform("Progress").set((float) progress);
        screenLayerShader.safeGetUniform("LayerProgress").set(layer.layerProgress());
        screenLayerShader.safeGetUniform("FocusLoss").set((float) focusLoss);
        screenLayerShader.safeGetUniform("PressurePeak").set((float) pressurePeak);
        screenLayerShader.safeGetUniform("Aftershock").set((float) aftershock);
        screenLayerShader.safeGetUniform("SampleCount").set(layer.samples());
        screenLayerShader.safeGetUniform("Frequency").set(layer.frequency());
        screenLayerShader.safeGetUniform("Speed").set(layer.speed());
        screenLayerShader.safeGetUniform("EdgeOnly").set(layer.edgeOnly());
        screenLayerShader.safeGetUniform("DirectionMode").set(layer.directionMode());
        screenLayerShader.safeGetUniform("TintColor").set(layer.colorR(), layer.colorG(), layer.colorB(), layer.colorA());
        screenLayerShader.safeGetUniform("NoiseStrength").set(layer.noiseStrength());
        screenLayerShader.safeGetUniform("Wobble").set(layer.wobble());
        screenLayerShader.safeGetUniform("BlurScale").set(layer.blurScale());
        screenLayerShader.safeGetUniform("RefractionStrength").set(layer.refractionStrength());
        screenLayerShader.safeGetUniform("Desaturation").set(layer.desaturation());
        screenLayerShader.safeGetUniform("Smear").set(layer.smear());
        screenLayerShader.safeGetUniform("Pulse").set(layer.pulse());
        screenLayerShader.safeGetUniform("BurstCount").set(layer.count());
        screenLayerShader.safeGetUniform("BurstLength").set(layer.length());
        screenLayerShader.safeGetUniform("BurstSharpness").set(layer.sharpness());
        screenLayerShader.safeGetUniform("CenterBias").set(layer.centerBias());
        screenLayerShader.safeGetUniform("EdgeBias").set(layer.edgeBias());
        screenLayerShader.safeGetUniform("BurstChromaticOffset").set(layer.chromaticOffset());
        screenLayerShader.safeGetUniform("Time").set((float) seconds);
        screenLayerShader.apply();

        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
        bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.draw(bufferBuilder.buildOrThrow());
        screenLayerShader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        destinationTarget.bindWrite(true);
    }

    private static void warnUnavailableOnce(ScreenEffectPayload payload) {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        LOGGER.warn(
            "EltenaEffect shader layer pipeline is unavailable for effectId={}. Shader disabled; using minimal fallback only.",
            payload.effectId()
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayerSelection(
        List<ActiveLayer> layers,
        boolean hasSupportedLayer
    ) {
    }

    private record ActiveLayer(
        ScreenShaderType type,
        float strength,
        int samples,
        float radius,
        float frequency,
        float speed,
        int edgeOnly,
        int directionMode,
        float layerProgress,
        float colorR,
        float colorG,
        float colorB,
        float colorA,
        float noiseStrength,
        float wobble,
        float blurScale,
        float refractionStrength,
        float desaturation,
        float smear,
        float pulse,
        int count,
        float length,
        float sharpness,
        float centerBias,
        float edgeBias,
        float chromaticOffset
    ) {
        private int typeCode() {
            return switch (type) {
                case RADIAL_BLUR -> 1;
                case SONIC_PRESSURE_BURST -> 2;
                case MOTION_BLUR -> 3;
                case MOTION_BLUR_FAKE -> 4;
                case PRESSURE_DISTORTION -> 5;
                case SHOCKWAVE_REFRACTION -> 6;
                case HEAT_REFRACTION -> 7;
                case UNDERWATER_REFRACTION -> 8;
                case CHROMATIC_ABERRATION -> 9;
                case COLOR_GRADE -> 10;
                case DESATURATION -> 11;
                case WHITEOUT -> 12;
                case DARK_VIGNETTE_SOFT -> 13;
                case NOISE_FOG -> 14;
                case SCREEN_SHAKE_WARP -> 15;
                case GHOSTING -> 16;
                case TIME_WARP -> 17;
                case FRACTURE_DISTORTION -> 18;
                case NONE -> 0;
            };
        }
    }
}
