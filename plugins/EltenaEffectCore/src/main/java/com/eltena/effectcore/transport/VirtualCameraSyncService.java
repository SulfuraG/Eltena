package com.eltena.effectcore.transport;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.virtualcamera.VirtualCameraConfiguration;
import com.eltena.effectcore.virtualcamera.VirtualCameraPreset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class VirtualCameraSyncService implements Listener {
    private final EltenaEffectCorePlugin plugin;
    private final EffectPluginMessenger messenger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<UUID, PlayerCameraState> playerStates = new LinkedHashMap<>();

    public VirtualCameraSyncService(EltenaEffectCorePlugin plugin, EffectPluginMessenger messenger) {
        this.plugin = plugin;
        this.messenger = messenger;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        playerStates.clear();
    }

    public int handleReloadSync() {
        normalizeStates();
        if (plugin.virtualCameraConfig().syncOnReload()) {
            return syncAllOnlinePlayers();
        }
        return 0;
    }

    public int syncAllOnlinePlayers() {
        int synced = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sync(player);
            synced++;
        }
        return synced;
    }

    public void sync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerCameraState state = resolveState(player.getUniqueId());
        byte[] bytes = gson.toJson(buildPayload(state)).getBytes(StandardCharsets.UTF_8);
        messenger.send(player, "virtual_camera_config", 0.0D, bytes);
    }

    public boolean enablePlayer(Player player, String requestedPresetId) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        VirtualCameraConfiguration configuration = plugin.virtualCameraConfig();
        String resolvedPreset = configuration.resolvePresetOrFallback(requestedPresetId);
        if (resolvedPreset == null) {
            return false;
        }

        playerStates.put(
            player.getUniqueId(),
            new PlayerCameraState(true, resolvedPreset, System.currentTimeMillis())
        );
        sync(player);
        return true;
    }

    public boolean disablePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        PlayerCameraState current = resolveState(player.getUniqueId());
        playerStates.put(player.getUniqueId(), new PlayerCameraState(false, current.presetId(), 0L));
        sync(player);
        return true;
    }

    public boolean setPlayerPreset(Player player, String requestedPresetId) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        VirtualCameraConfiguration configuration = plugin.virtualCameraConfig();
        String resolvedPreset = configuration.resolvePresetOrFallback(requestedPresetId);
        if (resolvedPreset == null) {
            return false;
        }

        PlayerCameraState current = resolveState(player.getUniqueId());
        long sceneStartTimeMs = current.enabled() ? System.currentTimeMillis() : 0L;
        playerStates.put(
            player.getUniqueId(),
            new PlayerCameraState(current.enabled(), resolvedPreset, sceneStartTimeMs)
        );
        sync(player);
        return true;
    }

    public CameraStatus status(Player player) {
        PlayerCameraState state = resolveState(player.getUniqueId());
        String resolvedPreset = plugin.virtualCameraConfig().resolvePresetOrFallback(state.presetId());
        VirtualCameraPreset preset = plugin.virtualCameraConfig().preset(resolvedPreset);
        boolean effectiveEnabled = isEffectivelyEnabled(state.enabled(), preset);
        return new CameraStatus(state.enabled(), effectiveEnabled, resolvedPreset, preset);
    }

    public int enableAll(String presetId) {
        int affected = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (enablePlayer(player, presetId)) {
                affected++;
            }
        }
        return affected;
    }

    public int disableAll() {
        int affected = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (disablePlayer(player)) {
                affected++;
            }
        }
        return affected;
    }

    public int setPresetForAll(String presetId) {
        int affected = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (setPlayerPreset(player, presetId)) {
                affected++;
            }
        }
        return affected;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.virtualCameraConfig().syncOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sync(player), 20L);
    }

    private void normalizeStates() {
        VirtualCameraConfiguration configuration = plugin.virtualCameraConfig();
        playerStates.replaceAll((uuid, state) -> {
            String resolvedPreset = configuration.resolvePresetOrFallback(state.presetId());
            return new PlayerCameraState(
                state.enabled(),
                resolvedPreset,
                state.enabled() ? state.sceneStartTimeMs() : 0L
            );
        });
    }

    private JsonObject buildPayload(PlayerCameraState state) {
        VirtualCameraConfiguration configuration = plugin.virtualCameraConfig();
        String activePreset = configuration.resolvePresetOrFallback(state.presetId());

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "virtual_camera_config");
        payload.addProperty("enabled", configuration.enabled());
        payload.addProperty("playerCameraEnabled", state.enabled());
        payload.addProperty("defaultPreset", configuration.resolvedDefaultPreset());
        payload.addProperty("activePreset", activePreset == null ? "" : activePreset);
        payload.addProperty("sceneStartTimeMs", state.enabled() ? state.sceneStartTimeMs() : 0L);
        payload.add("resolvedEntityLookAts", buildResolvedEntityLookAts());

        JsonArray presetsArray = new JsonArray();
        for (VirtualCameraPreset entry : configuration.presets().values()) {
            presetsArray.add(buildPresetPayload(entry));
        }
        payload.add("presets", presetsArray);
        return payload;
    }

    private JsonObject buildPresetPayload(VirtualCameraPreset preset) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", preset.id());
        payload.addProperty("displayName", preset.displayName());
        payload.addProperty("enabled", preset.enabled());
        payload.addProperty("mode", preset.mode());

        if (preset.relativePosition() != null) {
            JsonObject relativePosition = new JsonObject();
            relativePosition.addProperty("distance", preset.relativePosition().distance());
            relativePosition.addProperty("verticalOffset", preset.relativePosition().verticalOffset());
            relativePosition.addProperty("horizontalOffset", preset.relativePosition().horizontalOffset());
            relativePosition.addProperty("forwardOffset", preset.relativePosition().forwardOffset());
            payload.add("relativePosition", relativePosition);
        }

        if (preset.worldPosition() != null) {
            JsonObject worldPosition = new JsonObject();
            worldPosition.addProperty("world", preset.worldPosition().world());
            worldPosition.addProperty("x", preset.worldPosition().x());
            worldPosition.addProperty("y", preset.worldPosition().y());
            worldPosition.addProperty("z", preset.worldPosition().z());
            payload.add("worldPosition", worldPosition);
        }

        JsonObject rotation = new JsonObject();
        rotation.addProperty("yaw", preset.rotation().yaw());
        rotation.addProperty("pitch", preset.rotation().pitch());
        rotation.addProperty("roll", preset.rotation().roll());
        payload.add("rotation", rotation);

        JsonObject movement = new JsonObject();
        movement.addProperty("allowPlayerMovement", preset.movement().allowPlayerMovement());
        movement.addProperty("allowCameraMovement", preset.movement().allowCameraMovement());
        movement.addProperty("lockPlayerInput", preset.movement().lockPlayerInput());
        payload.add("movement", movement);

        JsonObject smoothing = new JsonObject();
        smoothing.addProperty("enabled", preset.smoothing().enabled());
        smoothing.addProperty("positionSmoothTime", preset.smoothing().positionSmoothTime());
        smoothing.addProperty("rotationSmoothTime", preset.smoothing().rotationSmoothTime());
        payload.add("smoothing", smoothing);

        JsonObject behavior = new JsonObject();
        behavior.addProperty("resetOnDisable", preset.behavior().resetOnDisable());
        behavior.addProperty("restorePlayerCameraOnDisable", preset.behavior().restorePlayerCameraOnDisable());
        behavior.addProperty("useVirtualEntityCamera", preset.behavior().useVirtualEntityCamera());
        payload.add("behavior", behavior);

        if (preset.isKeyframedMode() && !preset.keyframes().isEmpty()) {
            JsonArray keyframes = new JsonArray();
            for (VirtualCameraPreset.Keyframe keyframe : preset.keyframes()) {
                keyframes.add(buildKeyframePayload(keyframe));
            }
            payload.add("keyframes", keyframes);
        }

        return payload;
    }

    private JsonObject buildKeyframePayload(VirtualCameraPreset.Keyframe keyframe) {
        JsonObject payload = new JsonObject();
        payload.addProperty("timeMs", keyframe.timeMs());

        if (keyframe.worldPosition() != null) {
            payload.add("worldPosition", buildWorldPositionPayload(keyframe.worldPosition()));
        }
        if (keyframe.lookAt() != null) {
            payload.add("lookAt", buildLookAtPayload(keyframe.lookAt()));
        }
        if (keyframe.fov() != null) {
            payload.addProperty("fov", keyframe.fov());
        }
        payload.addProperty("easing", keyframe.easing());
        return payload;
    }

    private JsonObject buildWorldPositionPayload(VirtualCameraPreset.WorldPosition worldPosition) {
        JsonObject payload = new JsonObject();
        payload.addProperty("world", worldPosition.world());
        payload.addProperty("x", worldPosition.x());
        payload.addProperty("y", worldPosition.y());
        payload.addProperty("z", worldPosition.z());
        return payload;
    }

    private JsonObject buildLookAtPayload(VirtualCameraPreset.LookAt lookAt) {
        JsonObject payload = new JsonObject();
        if (lookAt instanceof VirtualCameraPreset.WorldLookAt worldLookAt) {
            payload.addProperty("type", "world");
            payload.addProperty("x", worldLookAt.position().x());
            payload.addProperty("y", worldLookAt.position().y());
            payload.addProperty("z", worldLookAt.position().z());
            return payload;
        }
        if (lookAt instanceof VirtualCameraPreset.EntityLookAt entityLookAt) {
            payload.addProperty("type", "entity");
            payload.addProperty("entityRef", entityLookAt.entityRef());
            payload.addProperty("offsetX", entityLookAt.offset().getX());
            payload.addProperty("offsetY", entityLookAt.offset().getY());
            payload.addProperty("offsetZ", entityLookAt.offset().getZ());
            return payload;
        }
        throw new IllegalArgumentException("Unsupported keyframed look-at payload type: " + lookAt.getClass().getName());
    }

    private JsonObject buildResolvedEntityLookAts() {
        // TODO: Pass source entity from PlaybackContext to resolve entity-ref look-at targets.
        return new JsonObject();
    }

    private PlayerCameraState resolveState(UUID playerId) {
        VirtualCameraConfiguration configuration = plugin.virtualCameraConfig();
        PlayerCameraState state = playerStates.get(playerId);
        if (state != null) {
            String resolvedPreset = configuration.resolvePresetOrFallback(state.presetId());
            return new PlayerCameraState(
                state.enabled(),
                resolvedPreset,
                state.enabled() ? state.sceneStartTimeMs() : 0L
            );
        }

        return new PlayerCameraState(false, configuration.resolvedDefaultPreset(), 0L);
    }

    private boolean isEffectivelyEnabled(boolean desiredEnabled, VirtualCameraPreset preset) {
        return plugin.virtualCameraConfig().enabled()
            && desiredEnabled
            && preset != null
            && preset.enabled();
    }

    private record PlayerCameraState(boolean enabled, String presetId, long sceneStartTimeMs) {
    }

    public record CameraStatus(boolean desiredEnabled, boolean effectiveEnabled, String activePresetId, VirtualCameraPreset preset) {
    }
}
