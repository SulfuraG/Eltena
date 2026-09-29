package com.eltena.effect.client.virtualcamera;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

public final class VirtualCameraController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DEFAULT_CAMERA_FOV = 70.0D;
    private static final VirtualCameraState STATE = new VirtualCameraState();

    private static VirtualCameraConfig registry = VirtualCameraConfig.defaults();
    private static String activePresetId;
    private static boolean pendingEnableFromServer;
    private static CameraType previousCameraType;
    private static Marker dedicatedCameraEntity;
    private static boolean registered;

    private VirtualCameraController() {
    }

    public static void register(IEventBus eventBus) {
        if (registered) {
            return;
        }

        registered = true;
        eventBus.addListener(VirtualCameraController::onClientTick);
        eventBus.addListener(VirtualCameraController::onMovementInputUpdate);
        eventBus.addListener(VirtualCameraController::onMouseButtonPre);
        eventBus.addListener(VirtualCameraController::onInteractionKeyTriggered);
        eventBus.addListener(VirtualCameraController::onComputeCameraAngles);
        eventBus.addListener(VirtualCameraController::onComputeFov);
    }

    public static void applyServerConfig(VirtualCameraConfig syncedRegistry) {
        Minecraft minecraft = Minecraft.getInstance();
        registry = syncedRegistry == null ? VirtualCameraConfig.defaults() : syncedRegistry;
        activePresetId = registry.resolvedActivePreset();

        VirtualCameraConfig.Preset preset = activePreset();
        pendingEnableFromServer = registry.enabled() && registry.playerCameraEnabled() && preset != null;

        if (preset == null) {
            pendingEnableFromServer = false;
            disableInternal(minecraft, true);
            return;
        }

        if (!registry.enabled() || !registry.playerCameraEnabled()) {
            pendingEnableFromServer = false;
            disableInternal(minecraft, true);
            return;
        }

        if (minecraft.player == null || minecraft.level == null) {
            pendingEnableFromServer = true;
            return;
        }

        if (STATE.enabled()) {
            pendingEnableFromServer = false;
            return;
        }

        enableFromPreset(minecraft, preset, true);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (pendingEnableFromServer && !STATE.enabled() && minecraft.player != null && minecraft.level != null) {
            VirtualCameraConfig.Preset preset = activePreset();
            if (preset != null) {
                enableFromPreset(minecraft, preset, true);
            }
        }

        if (!STATE.enabled()) {
            minecraft.gameRenderer.setRenderHand(true);
            return;
        }

        VirtualCameraConfig.Preset preset = activePreset();
        if (preset == null) {
            disableInternal(minecraft, true);
            return;
        }

        if (minecraft.player == null || minecraft.level == null) {
            disableInternal(minecraft, false);
            return;
        }

        if (!registry.enabled() || !registry.playerCameraEnabled()) {
            disableInternal(minecraft, true);
            return;
        }

        if (!ensureDedicatedCameraEntity(minecraft)) {
            disableInternal(minecraft, true);
            return;
        }

        if (!syncDedicatedCameraEntity(dedicatedCameraEntity, STATE)) {
            disableInternal(minecraft, true);
            return;
        }

        if (minecraft.getCameraEntity() != dedicatedCameraEntity) {
            minecraft.setCameraEntity(dedicatedCameraEntity);
        }

        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.gameRenderer.setRenderHand(false);

        if (!preset.movement().allowPlayerMovement() || preset.movement().lockPlayerInput()) {
            clearMovementKeys(minecraft, preset);
        }
    }

    private static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        VirtualCameraConfig.Preset preset = activePreset();
        if (!STATE.enabled() || preset == null) {
            return;
        }
        if (preset.movement().allowPlayerMovement() && !preset.movement().lockPlayerInput()) {
            return;
        }

        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    private static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        VirtualCameraConfig.Preset preset = activePreset();
        if (STATE.enabled() && preset != null && preset.movement().lockPlayerInput()) {
            event.setCanceled(true);
        }
    }

    private static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        VirtualCameraConfig.Preset preset = activePreset();
        if (STATE.enabled() && preset != null && preset.movement().lockPlayerInput()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        VirtualCameraConfig.Preset preset = activePreset();
        if (!STATE.enabled() || preset == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!registry.enabled() || !registry.playerCameraEnabled()) {
            return;
        }
        if (dedicatedCameraEntity == null || event.getCamera().getEntity() != dedicatedCameraEntity) {
            return;
        }

        if (preset.isKeyframedMode()) {
            STATE.prepareKeyframedTarget(preset, registry.sceneStartTimeMs(), registry.resolvedEntityLookAts());
            STATE.resetStoredState();
        } else {
            STATE.prepareTarget(preset, minecraft.player, event.getPartialTick());
            STATE.tick(preset.smoothing());
        }
        if (!syncDedicatedCameraEntity(dedicatedCameraEntity, STATE)) {
            disableInternal(minecraft, true);
            return;
        }

        event.setYaw(STATE.cameraYaw());
        event.setPitch(STATE.cameraPitch());
        event.setRoll(STATE.cameraRoll());
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        VirtualCameraConfig.Preset preset = activePreset();
        if (!STATE.enabled() || preset == null || !preset.isKeyframedMode()) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!registry.enabled() || !registry.playerCameraEnabled()) {
            return;
        }
        if (dedicatedCameraEntity == null || event.getCamera().getEntity() != dedicatedCameraEntity) {
            return;
        }
        if (!STATE.hasCameraFov()) {
            return;
        }

        double baseFov = minecraft.options == null ? DEFAULT_CAMERA_FOV : minecraft.options.fov().get().intValue();
        event.setFOV(event.getFOV() + (STATE.cameraFov() - baseFov));
    }

    private static void enableFromPreset(Minecraft minecraft, VirtualCameraConfig.Preset preset, boolean capturePreviousState) {
        if (capturePreviousState && !STATE.enabled()) {
            previousCameraType = minecraft.options.getCameraType();
        }

        pendingEnableFromServer = false;
        activePresetId = preset.id();
        if (minecraft.player != null) {
            STATE.initializeFromPlayerView(minecraft.player, 1.0D);
            if (preset.isKeyframedMode()) {
                STATE.prepareKeyframedTarget(preset, registry.sceneStartTimeMs(), registry.resolvedEntityLookAts());
            } else {
                STATE.prepareTarget(preset, minecraft.player, 1.0D);
            }
            if (preset.isKeyframedMode() || !preset.smoothing().enabled()) {
                STATE.resetStoredState();
            }
        }

        if (!ensureDedicatedCameraEntity(minecraft)) {
            disableInternal(minecraft, true);
            return;
        }

        if (!syncDedicatedCameraEntity(dedicatedCameraEntity, STATE)) {
            disableInternal(minecraft, true);
            return;
        }

        STATE.setEnabled(true);
        minecraft.setCameraEntity(dedicatedCameraEntity);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.gameRenderer.setRenderHand(false);
    }

    private static void disableInternal(Minecraft minecraft, boolean restorePlayerCamera) {
        VirtualCameraConfig.Preset preset = activePreset();
        boolean wasMovementLocked = preset != null
            && (!preset.movement().allowPlayerMovement() || preset.movement().lockPlayerInput());

        pendingEnableFromServer = false;
        STATE.setEnabled(false);
        restoreVanillaCamera(minecraft, restorePlayerCamera && shouldRestorePlayerCamera(preset));
        if (preset == null || preset.behavior().resetOnDisable()) {
            STATE.resetStoredState();
        }
        if (restorePlayerCamera) {
            previousCameraType = null;
        }

        if (wasMovementLocked) {
            clearMovementKeys(minecraft, preset);
        }

        dedicatedCameraEntity = null;
    }

    private static void restoreVanillaCamera(Minecraft minecraft, boolean restoreCameraType) {
        minecraft.setCameraEntity(minecraft.player);
        if (restoreCameraType && previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        minecraft.gameRenderer.setRenderHand(true);
    }

    private static void clearMovementKeys(Minecraft minecraft, VirtualCameraConfig.Preset preset) {
        releaseKey(minecraft.options.keyUp);
        releaseKey(minecraft.options.keyDown);
        releaseKey(minecraft.options.keyLeft);
        releaseKey(minecraft.options.keyRight);
        releaseKey(minecraft.options.keyJump);
        releaseKey(minecraft.options.keyShift);
        releaseKey(minecraft.options.keySprint);
        if (preset != null && preset.movement().lockPlayerInput()) {
            releaseKey(minecraft.options.keyAttack);
            releaseKey(minecraft.options.keyUse);
            releaseKey(minecraft.options.keyPickItem);
        }
    }

    private static boolean shouldRestorePlayerCamera(VirtualCameraConfig.Preset preset) {
        return preset == null || preset.behavior().restorePlayerCameraOnDisable();
    }

    private static VirtualCameraConfig.Preset activePreset() {
        if (activePresetId == null) {
            return registry.activePresetDefinition();
        }
        VirtualCameraConfig.Preset selected = registry.presets().get(activePresetId);
        if (selected != null && selected.enabled()) {
            return selected;
        }
        return registry.activePresetDefinition();
    }

    private static void releaseKey(KeyMapping keyMapping) {
        keyMapping.setDown(false);
    }

    private static boolean ensureDedicatedCameraEntity(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        if (dedicatedCameraEntity != null && dedicatedCameraEntity.level() == minecraft.level) {
            return true;
        }

        Marker cameraEntity = new Marker(EntityType.MARKER, minecraft.level);
        cameraEntity.setNoGravity(true);
        dedicatedCameraEntity = cameraEntity;
        return true;
    }

    private static boolean syncDedicatedCameraEntity(Marker cameraEntity, VirtualCameraState state) {
        if (cameraEntity == null) {
            return false;
        }

        try {
            cameraEntity.xo = cameraEntity.getX();
            cameraEntity.yo = cameraEntity.getY();
            cameraEntity.zo = cameraEntity.getZ();
            cameraEntity.yRotO = cameraEntity.getYRot();
            cameraEntity.xRotO = cameraEntity.getXRot();
            cameraEntity.setPos(state.cameraX(), state.cameraY(), state.cameraZ());
            cameraEntity.setYRot(state.cameraYaw());
            cameraEntity.setXRot(state.cameraPitch());
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("VirtualCamera 専用カメラエンティティの座標反映に失敗しました。安全のため仮想カメラを無効化します。", exception);
            return false;
        }
    }
}
