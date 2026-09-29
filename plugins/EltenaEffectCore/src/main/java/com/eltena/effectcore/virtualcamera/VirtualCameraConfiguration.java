package com.eltena.effectcore.virtualcamera;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record VirtualCameraConfiguration(
    boolean enabled,
    String defaultPreset,
    boolean syncOnJoin,
    boolean syncOnReload,
    Loading loading,
    Behavior behavior,
    Map<String, VirtualCameraPreset> presets
) {
    public VirtualCameraConfiguration {
        presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets));
    }

    public static VirtualCameraConfiguration defaults() {
        return new VirtualCameraConfiguration(
            true,
            "debug_freecam",
            true,
            true,
            new Loading("presets", false),
            new Behavior(true, true, true),
            Map.of()
        );
    }

    public String resolvedDefaultPreset() {
        if (defaultPreset != null) {
            VirtualCameraPreset configured = presets.get(defaultPreset);
            if (configured != null && configured.enabled()) {
                return configured.id();
            }
        }

        for (VirtualCameraPreset preset : presets.values()) {
            if (preset.enabled()) {
                return preset.id();
            }
        }
        return null;
    }

    public String resolvePresetOrFallback(String requestedPreset) {
        if (requestedPreset != null) {
            VirtualCameraPreset preset = presets.get(requestedPreset);
            if (preset != null && preset.enabled()) {
                return preset.id();
            }
        }
        return resolvedDefaultPreset();
    }

    public VirtualCameraPreset preset(String presetId) {
        if (presetId == null) {
            return null;
        }
        return presets.get(presetId);
    }

    public record Loading(String presetDirectory, boolean failOnInvalidPreset) {
    }

    public record Behavior(
        boolean restorePlayerCameraOnDisable,
        boolean resetStateOnWorldChange,
        boolean resetStateOnServerSwitch
    ) {
    }
}
