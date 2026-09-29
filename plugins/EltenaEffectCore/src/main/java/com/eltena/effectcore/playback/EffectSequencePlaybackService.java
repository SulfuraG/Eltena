package com.eltena.effectcore.playback;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.config.EffectSequenceRegistry;
import com.eltena.effectcore.integration.EltenaSoundApiBridge;
import com.eltena.effectcore.sequence.SequenceDefinition;
import com.eltena.effectcore.sequence.SequenceStep;
import com.eltena.effectcore.sequence.SequenceStepType;
import com.eltena.effectcore.transport.VirtualCameraSyncService;
import com.eltena.effectcore.virtualcamera.VirtualCameraPreset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class EffectSequencePlaybackService {
    private final EltenaEffectCorePlugin plugin;
    private final EffectSequenceRegistry sequenceRegistry;
    private final EffectPlaybackService effectPlaybackService;
    private final EltenaSoundApiBridge soundApiBridge;
    private final VirtualCameraSyncService virtualCameraSyncService;

    public EffectSequencePlaybackService(
        EltenaEffectCorePlugin plugin,
        EffectSequenceRegistry sequenceRegistry,
        EffectPlaybackService effectPlaybackService,
        EltenaSoundApiBridge soundApiBridge,
        VirtualCameraSyncService virtualCameraSyncService
    ) {
        this.plugin = plugin;
        this.sequenceRegistry = sequenceRegistry;
        this.effectPlaybackService = effectPlaybackService;
        this.soundApiBridge = soundApiBridge;
        this.virtualCameraSyncService = virtualCameraSyncService;
    }

    public SequencePlayResult play(String sequenceId, Location sourceLocation) {
        return play(sequenceId, sourceLocation, null, null);
    }

    public SequencePlayResult play(
        String sequenceId,
        Location sourceLocation,
        Collection<Player> audience,
        Entity sourceEntity
    ) {
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            throw new IllegalArgumentException("Source location must include a world.");
        }

        SequenceDefinition sequence = sequenceRegistry.require(sequenceId);
        List<SequenceStep> steps = sequence.steps();
        SequenceRuntimeContext runtimeContext = buildRuntimeContext(sequence);
        PlaybackContext playbackContext = new PlaybackContext(
            sourceLocation.clone(),
            audience == null ? null : List.copyOf(audience),
            sourceEntity
        );
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.sequence-start",
                Map.of(
                    "sequenceId", sequence.id(),
                    "stepCount", Integer.toString(steps.size()),
                    "world", sourceLocation.getWorld().getName(),
                    "x", plugin.formatDecimal(sourceLocation.getX()),
                    "y", plugin.formatDecimal(sourceLocation.getY()),
                    "z", plugin.formatDecimal(sourceLocation.getZ())
                )
            );
        }

        for (int index = 0; index < steps.size(); index++) {
            SequenceStep step = steps.get(index);
            int stepIndex = index + 1;
            boolean lastStep = stepIndex == steps.size();
            long delayTicks = Math.max(0L, Math.round(step.delayMs() / 50.0D));
            Location scheduledSource = sourceLocation.clone();
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> executeStep(
                    sequence,
                    step,
                    stepIndex,
                    steps.size(),
                    delayTicks,
                    playbackContext.withSourceLocation(scheduledSource),
                    lastStep,
                    runtimeContext
                ),
                delayTicks
            );
        }

        return new SequencePlayResult(sequence.id(), sequence.displayName(), steps.size());
    }

    private void executeStep(
        SequenceDefinition sequence,
        SequenceStep step,
        int stepIndex,
        int stepCount,
        long delayTicks,
        PlaybackContext playbackContext,
        boolean lastStep,
        SequenceRuntimeContext runtimeContext
    ) {
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.sequence-step",
                Map.of(
                    "sequenceId", sequence.id(),
                    "stepIndex", Integer.toString(stepIndex),
                    "stepCount", Integer.toString(stepCount),
                    "stepType", step.type().name(),
                    "stepId", step.id(),
                    "delayMs", Long.toString(step.delayMs()),
                    "delayTicks", Long.toString(delayTicks)
                )
            );
        }

        try {
            switch (step.type()) {
                case EFFECT -> executeEffectStep(
                    sequence,
                    step,
                    stepIndex,
                    stepCount,
                    playbackContext.sourceLocation(),
                    runtimeContext
                );
                case SOUND -> executeSoundStep(
                    sequence,
                    step,
                    stepIndex,
                    stepCount,
                    playbackContext.sourceLocation()
                );
                case LIVE_SOUND -> executeLiveSoundStep(
                    sequence,
                    step,
                    stepIndex,
                    stepCount,
                    playbackContext.sourceLocation()
                );
                case CAMERA -> executeCameraStep(
                    sequence,
                    step,
                    stepIndex,
                    stepCount,
                    playbackContext
                );
            }
        } finally {
            if (lastStep && plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.sequence-complete",
                    Map.of(
                        "sequenceId", sequence.id(),
                        "stepCount", Integer.toString(stepCount)
                    )
                );
            }
        }
    }

    private void executeEffectStep(
        SequenceDefinition sequence,
        SequenceStep step,
        int stepIndex,
        int stepCount,
        Location sourceLocation,
        SequenceRuntimeContext runtimeContext
    ) {
        try {
            Long durationOverrideMs = resolveEffectDurationOverride(sequence, step, runtimeContext);
            effectPlaybackService.play(step.id(), sourceLocation, durationOverrideMs);
        } catch (IllegalArgumentException exception) {
            if (plugin.isDebugLogEnabled()) {
                plugin.logWarning(
                    "log.sequence-missing-effect",
                    Map.of(
                        "sequenceId", sequence.id(),
                        "stepIndex", Integer.toString(stepIndex),
                        "stepCount", Integer.toString(stepCount),
                        "effectId", step.id()
                    )
                );
            }
        }
    }

    private void executeSoundStep(
        SequenceDefinition sequence,
        SequenceStep step,
        int stepIndex,
        int stepCount,
        Location sourceLocation
    ) {
        EltenaSoundApiBridge.DispatchResult result = soundApiBridge.playSound(sourceLocation, step.id());
        switch (result.status()) {
            case SUCCESS -> {
            }
            case SERVICE_UNAVAILABLE -> plugin.logWarning(
                "log.sequence-sound-service-unavailable",
                Map.of(
                    "sequenceId", sequence.id(),
                    "stepIndex", Integer.toString(stepIndex),
                    "stepCount", Integer.toString(stepCount),
                    "pluginName", result.detail()
                )
            );
            case SOUND_NOT_FOUND -> plugin.logWarning(
                "log.sequence-missing-sound",
                Map.of(
                    "sequenceId", sequence.id(),
                    "stepIndex", Integer.toString(stepIndex),
                    "stepCount", Integer.toString(stepCount),
                    "soundId", result.detail()
                )
            );
            case LIVE_ASSET_NOT_FOUND -> {
            }
        }
    }

    private void executeLiveSoundStep(
        SequenceDefinition sequence,
        SequenceStep step,
        int stepIndex,
        int stepCount,
        Location sourceLocation
    ) {
        EltenaSoundApiBridge.DispatchResult result = soundApiBridge.playLiveAsset(sourceLocation, step.id());
        switch (result.status()) {
            case SUCCESS -> {
            }
            case SERVICE_UNAVAILABLE -> plugin.getLogger().warning(
                "[EltenaEffectCore] Sequence live-sound service unavailable: sequence="
                    + sequence.id()
                    + " stepIndex="
                    + stepIndex
                    + "/"
                    + stepCount
                    + " plugin="
                    + result.detail()
            );
            case LIVE_ASSET_NOT_FOUND -> plugin.getLogger().warning(
                "[EltenaEffectCore] Sequence live-sound not found: sequence="
                    + sequence.id()
                    + " stepIndex="
                    + stepIndex
                    + "/"
                    + stepCount
                    + " assetId="
                    + result.detail()
            );
            case SOUND_NOT_FOUND -> {
            }
        }
    }

    private void executeCameraStep(
        SequenceDefinition sequence,
        SequenceStep step,
        int stepIndex,
        int stepCount,
        PlaybackContext playbackContext
    ) {
        Collection<Player> audience = playbackContext.audience();
        if (audience == null) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Camera step '"
                    + step.id()
                    + "' in sequence '"
                    + sequence.id()
                    + "' skipped: no audience provided (location-only playback)"
            );
            return;
        }
        if (audience.isEmpty()) {
            return;
        }

        String resolvedPresetId = plugin.virtualCameraConfig().resolvePresetOrFallback(step.id());
        if (resolvedPresetId == null) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Sequence camera preset not found: sequence="
                    + sequence.id()
                    + " stepIndex="
                    + stepIndex
                    + "/"
                    + stepCount
                    + " presetId="
                    + step.id()
            );
            return;
        }

        Long autoDisableDelayTicks = resolveCameraAutoDisableDelayTicks(resolvedPresetId);
        if (autoDisableDelayTicks == null) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Sequence camera step skipped: preset="
                    + resolvedPresetId
                    + " sequence="
                    + sequence.id()
                    + " stepIndex="
                    + stepIndex
                    + "/"
                    + stepCount
                    + " reason=keyframed duration is not available in the current VirtualCameraPreset configuration"
            );
            return;
        }

        for (Player player : audience) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!virtualCameraSyncService.enablePlayer(player, resolvedPresetId)) {
                plugin.getLogger().warning(
                    "[EltenaEffectCore] Failed to enable sequence camera preset: sequence="
                        + sequence.id()
                        + " stepIndex="
                        + stepIndex
                        + "/"
                        + stepCount
                        + " presetId="
                        + resolvedPresetId
                        + " player="
                        + player.getName()
                );
                continue;
            }
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> virtualCameraSyncService.disablePlayer(player),
                autoDisableDelayTicks
            );
        }
    }

    private Long resolveCameraAutoDisableDelayTicks(String presetId) {
        VirtualCameraPreset preset = plugin.virtualCameraConfig().preset(presetId);
        if (preset == null) {
            return null;
        }
        if (!preset.isKeyframedMode()) {
            return null;
        }
        return Math.max(1L, Math.round(preset.totalDurationMs() / 50.0D));
    }

    private SequenceRuntimeContext buildRuntimeContext(SequenceDefinition sequence) {
        LinkedHashMap<String, AliasBinding> aliasBindings = new LinkedHashMap<>();
        for (SequenceStep step : sequence.steps()) {
            if (step.alias() == null) {
                continue;
            }
            aliasBindings.put(normalizeAlias(step.alias()), resolveAliasBinding(sequence, step));
        }
        return new SequenceRuntimeContext(Map.copyOf(aliasBindings));
    }

    private AliasBinding resolveAliasBinding(SequenceDefinition sequence, SequenceStep step) {
        return switch (step.type()) {
            case SOUND -> buildAliasBinding(
                sequence,
                step,
                soundApiBridge.resolveSoundDurationMs(step.id())
            );
            case LIVE_SOUND -> buildAliasBinding(
                sequence,
                step,
                soundApiBridge.resolveLiveAssetDurationMs(step.id())
            );
            case EFFECT -> new AliasBinding(step.alias(), step.type(), step.id(), null);
            case CAMERA -> new AliasBinding(step.alias(), step.type(), step.id(), null);
        };
    }

    private AliasBinding buildAliasBinding(
        SequenceDefinition sequence,
        SequenceStep step,
        EltenaSoundApiBridge.DurationResolveResult result
    ) {
        if (result.status() != EltenaSoundApiBridge.DurationResolveStatus.SUCCESS) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Failed to resolve synced duration: sequence="
                    + sequence.id()
                    + " alias="
                    + step.alias()
                    + " type="
                    + step.type().name()
                    + " id="
                    + step.id()
                    + " status="
                    + result.status().name()
                    + " detail="
                    + result.detail()
                    + " -> using effect default duration"
            );
            return new AliasBinding(step.alias(), step.type(), step.id(), null);
        }
        return new AliasBinding(step.alias(), step.type(), step.id(), result.durationMs());
    }

    private Long resolveEffectDurationOverride(
        SequenceDefinition sequence,
        SequenceStep step,
        SequenceRuntimeContext runtimeContext
    ) {
        if (!step.hasDurationSync()) {
            return null;
        }

        AliasBinding binding = runtimeContext.aliasBindings().get(normalizeAlias(step.syncDurationWith()));
        if (binding == null) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Missing sync-duration alias: sequence="
                    + sequence.id()
                    + " effect="
                    + step.id()
                    + " alias="
                    + step.syncDurationWith()
                    + " -> using effect default duration"
            );
            return null;
        }
        if (binding.durationMs() == null || binding.durationMs() <= 0L) {
            return null;
        }

        long resolvedDurationMs = Math.max(
            1L,
            Math.round(binding.durationMs() * step.syncDurationScale())
        );
        resolvedDurationMs = Math.max(resolvedDurationMs, step.syncDurationMinMs());
        resolvedDurationMs = Math.min(resolvedDurationMs, step.syncDurationMaxMs());
        return resolvedDurationMs;
    }

    private String normalizeAlias(String alias) {
        return alias.toLowerCase(Locale.ROOT);
    }

    private record SequenceRuntimeContext(
        Map<String, AliasBinding> aliasBindings
    ) {
    }

    private record PlaybackContext(
        Location sourceLocation,
        Collection<Player> audience,
        Entity sourceEntity
    ) {
        private PlaybackContext withSourceLocation(Location updatedSourceLocation) {
            return new PlaybackContext(updatedSourceLocation, audience, sourceEntity);
        }
    }

    private record AliasBinding(
        String alias,
        SequenceStepType type,
        String id,
        Long durationMs
    ) {
    }
}
