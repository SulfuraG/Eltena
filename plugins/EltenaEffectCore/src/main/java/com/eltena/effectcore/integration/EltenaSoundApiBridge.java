package com.eltena.effectcore.integration;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class EltenaSoundApiBridge {
    private static final String SOUND_PLUGIN_NAME = "EltenaSoundCore";
    private static final String SOUND_API_CLASS_NAME = "com.eltena.soundcore.api.EltenaSoundApi";

    private final EltenaEffectCorePlugin plugin;

    public EltenaSoundApiBridge(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public DispatchResult playSound(Location sourceLocation, String soundId) {
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            throw new IllegalArgumentException("Source location must include a world.");
        }

        List<Player> targets = selectTargets(sourceLocation);
        ResolvedApi api = resolveApi();
        if (api == null) {
            return DispatchResult.serviceUnavailable(targets.size(), SOUND_PLUGIN_NAME);
        }
        if (!api.hasSound(soundId)) {
            return DispatchResult.soundNotFound(targets.size(), soundId);
        }

        int sentCount = 0;
        for (Player player : targets) {
            if (api.playSound(player, soundId)) {
                sentCount++;
            }
        }
        return DispatchResult.success(targets.size(), sentCount, soundId);
    }

    public DispatchResult playLiveAsset(Location sourceLocation, String assetId) {
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            throw new IllegalArgumentException("Source location must include a world.");
        }

        List<Player> targets = selectTargets(sourceLocation);
        ResolvedApi api = resolveApi();
        if (api == null || !api.supportsLiveAssets()) {
            return DispatchResult.serviceUnavailable(targets.size(), SOUND_PLUGIN_NAME);
        }
        if (!api.hasLiveAsset(assetId)) {
            return DispatchResult.liveAssetNotFound(targets.size(), assetId);
        }

        int sentCount = 0;
        for (Player player : targets) {
            if (api.playLiveAsset(player, assetId)) {
                sentCount++;
            }
        }
        return DispatchResult.success(targets.size(), sentCount, assetId);
    }

    public DurationResolveResult resolveSoundDurationMs(String soundId) {
        ResolvedApi api = resolveApi();
        if (api == null) {
            return DurationResolveResult.serviceUnavailable(SOUND_PLUGIN_NAME);
        }
        if (!api.hasSound(soundId)) {
            return DurationResolveResult.soundNotFound(soundId);
        }
        long durationMs = api.getSoundDurationMs(soundId);
        if (durationMs <= 0L) {
            return DurationResolveResult.unavailable("duration unavailable for sound " + soundId);
        }
        return DurationResolveResult.success(durationMs, soundId);
    }

    public DurationResolveResult resolveLiveAssetDurationMs(String assetId) {
        ResolvedApi api = resolveApi();
        if (api == null || !api.supportsLiveAssets()) {
            return DurationResolveResult.serviceUnavailable(SOUND_PLUGIN_NAME);
        }
        if (!api.hasLiveAsset(assetId)) {
            return DurationResolveResult.liveAssetNotFound(assetId);
        }
        long durationMs = api.getLiveAssetDurationMs(assetId);
        if (durationMs <= 0L) {
            return DurationResolveResult.unavailable("duration unavailable for live asset " + assetId);
        }
        return DurationResolveResult.success(durationMs, assetId);
    }

    private List<Player> selectTargets(Location sourceLocation) {
        World world = sourceLocation.getWorld();
        if (world == null) {
            return List.of();
        }
        return world.getPlayers()
            .stream()
            .filter(Player::isOnline)
            .toList();
    }

    private ResolvedApi resolveApi() {
        Plugin soundPlugin = plugin.getServer().getPluginManager().getPlugin(SOUND_PLUGIN_NAME);
        if (soundPlugin == null || !soundPlugin.isEnabled()) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName(
                SOUND_API_CLASS_NAME,
                true,
                soundPlugin.getClass().getClassLoader()
            );
            Object apiInstance = plugin.getServer().getServicesManager().load((Class) apiClass);
            if (apiInstance == null) {
                return null;
            }

            Method hasSound = apiClass.getMethod("hasSound", String.class);
            Method playSound = apiClass.getMethod("playSound", Player.class, String.class);
            Method hasLiveAsset = findOptionalMethod(apiClass, "hasLiveAsset", String.class);
            Method playLiveAsset = findOptionalMethod(apiClass, "playLiveAsset", Player.class, String.class);
            Method getSoundDurationMs = findOptionalMethod(apiClass, "getSoundDurationMs", String.class);
            Method getLiveAssetDurationMs = findOptionalMethod(apiClass, "getLiveAssetDurationMs", String.class);
            return new ResolvedApi(
                apiInstance,
                hasSound,
                playSound,
                hasLiveAsset,
                playLiveAsset,
                getSoundDurationMs,
                getLiveAssetDurationMs
            );
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Method findOptionalMethod(Class<?> apiClass, String methodName, Class<?>... parameterTypes) {
        try {
            return apiClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    public record DispatchResult(
        DispatchStatus status,
        int targetCount,
        int sentCount,
        String detail
    ) {
        public static DispatchResult success(int targetCount, int sentCount, String soundId) {
            return new DispatchResult(DispatchStatus.SUCCESS, targetCount, sentCount, soundId);
        }

        public static DispatchResult serviceUnavailable(int targetCount, String pluginName) {
            return new DispatchResult(
                DispatchStatus.SERVICE_UNAVAILABLE,
                targetCount,
                0,
                pluginName
            );
        }

        public static DispatchResult soundNotFound(int targetCount, String soundId) {
            return new DispatchResult(DispatchStatus.SOUND_NOT_FOUND, targetCount, 0, soundId);
        }

        public static DispatchResult liveAssetNotFound(int targetCount, String assetId) {
            return new DispatchResult(DispatchStatus.LIVE_ASSET_NOT_FOUND, targetCount, 0, assetId);
        }
    }

    public enum DispatchStatus {
        SUCCESS,
        SERVICE_UNAVAILABLE,
        SOUND_NOT_FOUND,
        LIVE_ASSET_NOT_FOUND
    }

    public record DurationResolveResult(
        DurationResolveStatus status,
        long durationMs,
        String detail
    ) {
        public static DurationResolveResult success(long durationMs, String detail) {
            return new DurationResolveResult(DurationResolveStatus.SUCCESS, durationMs, detail);
        }

        public static DurationResolveResult serviceUnavailable(String pluginName) {
            return new DurationResolveResult(
                DurationResolveStatus.SERVICE_UNAVAILABLE,
                0L,
                pluginName
            );
        }

        public static DurationResolveResult soundNotFound(String soundId) {
            return new DurationResolveResult(
                DurationResolveStatus.SOUND_NOT_FOUND,
                0L,
                soundId
            );
        }

        public static DurationResolveResult liveAssetNotFound(String assetId) {
            return new DurationResolveResult(
                DurationResolveStatus.LIVE_ASSET_NOT_FOUND,
                0L,
                assetId
            );
        }

        public static DurationResolveResult unavailable(String detail) {
            return new DurationResolveResult(DurationResolveStatus.UNAVAILABLE, 0L, detail);
        }
    }

    public enum DurationResolveStatus {
        SUCCESS,
        SERVICE_UNAVAILABLE,
        SOUND_NOT_FOUND,
        LIVE_ASSET_NOT_FOUND,
        UNAVAILABLE
    }

    private record ResolvedApi(
        Object apiInstance,
        Method hasSoundMethod,
        Method playSoundMethod,
        Method hasLiveAssetMethod,
        Method playLiveAssetMethod,
        Method getSoundDurationMsMethod,
        Method getLiveAssetDurationMsMethod
    ) {
        private boolean supportsLiveAssets() {
            return hasLiveAssetMethod != null && playLiveAssetMethod != null;
        }

        private boolean hasSound(String soundId) {
            return invokeBoolean(hasSoundMethod, soundId);
        }

        private boolean playSound(Player player, String soundId) {
            return invokeBoolean(playSoundMethod, player, soundId);
        }

        private boolean hasLiveAsset(String assetId) {
            return supportsLiveAssets() && invokeBoolean(hasLiveAssetMethod, assetId);
        }

        private boolean playLiveAsset(Player player, String assetId) {
            return supportsLiveAssets() && invokeBoolean(playLiveAssetMethod, player, assetId);
        }

        private long getSoundDurationMs(String soundId) {
            return invokeLong(getSoundDurationMsMethod, soundId);
        }

        private long getLiveAssetDurationMs(String assetId) {
            return invokeLong(getLiveAssetDurationMsMethod, assetId);
        }

        private boolean invokeBoolean(Method method, Object... args) {
            if (method == null) {
                return false;
            }
            try {
                Object result = method.invoke(apiInstance, args);
                return result instanceof Boolean bool && bool;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                return false;
            }
        }

        private long invokeLong(Method method, Object... args) {
            if (method == null) {
                return 0L;
            }
            try {
                Object result = method.invoke(apiInstance, args);
                return result instanceof Number number ? number.longValue() : 0L;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                return 0L;
            }
        }
    }
}
