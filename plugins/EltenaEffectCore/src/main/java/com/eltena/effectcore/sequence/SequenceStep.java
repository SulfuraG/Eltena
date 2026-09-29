package com.eltena.effectcore.sequence;

public record SequenceStep(
    SequenceStepType type,
    String id,
    String alias,
    long delayMs,
    String syncDurationWith,
    SyncDurationMode syncDurationMode,
    double syncDurationScale,
    long syncDurationMinMs,
    long syncDurationMaxMs
) {
    public SequenceStep {
        if (type == null) {
            throw new IllegalArgumentException("Sequence step type is required.");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Sequence step id is required.");
        }
        if (delayMs < 0L) {
            throw new IllegalArgumentException("Sequence step delay must be non-negative.");
        }
        id = id.trim();
        alias = trimToNull(alias);
        syncDurationWith = trimToNull(syncDurationWith);
        syncDurationMode = syncDurationMode == null ? SyncDurationMode.MATCH : syncDurationMode;
        syncDurationScale = syncDurationScale <= 0.0D ? 1.0D : syncDurationScale;
        syncDurationMinMs = Math.max(0L, syncDurationMinMs);
        syncDurationMaxMs = syncDurationMaxMs <= 0L ? Long.MAX_VALUE : syncDurationMaxMs;
        if (syncDurationMaxMs < syncDurationMinMs) {
            syncDurationMaxMs = syncDurationMinMs;
        }
    }

    public static SequenceStep effect(String effectId, long delayMs) {
        return effect(effectId, delayMs, null, SyncDurationMode.MATCH, 1.0D, 0L, Long.MAX_VALUE);
    }

    public static SequenceStep effect(
        String effectId,
        long delayMs,
        String syncDurationWith,
        SyncDurationMode syncDurationMode,
        double syncDurationScale,
        long syncDurationMinMs,
        long syncDurationMaxMs
    ) {
        return new SequenceStep(
            SequenceStepType.EFFECT,
            effectId,
            null,
            delayMs,
            syncDurationWith,
            syncDurationMode,
            syncDurationScale,
            syncDurationMinMs,
            syncDurationMaxMs
        );
    }

    public static SequenceStep sound(String soundId, long delayMs) {
        return sound(soundId, delayMs, null);
    }

    public static SequenceStep sound(String soundId, long delayMs, String alias) {
        return new SequenceStep(
            SequenceStepType.SOUND,
            soundId,
            alias,
            delayMs,
            null,
            SyncDurationMode.MATCH,
            1.0D,
            0L,
            Long.MAX_VALUE
        );
    }

    public static SequenceStep liveSound(String assetId, long delayMs, String alias) {
        return new SequenceStep(
            SequenceStepType.LIVE_SOUND,
            assetId,
            alias,
            delayMs,
            null,
            SyncDurationMode.MATCH,
            1.0D,
            0L,
            Long.MAX_VALUE
        );
    }

    public static SequenceStep camera(String presetId, long delayMs) {
        return new SequenceStep(
            SequenceStepType.CAMERA,
            presetId,
            null,
            delayMs,
            null,
            SyncDurationMode.MATCH,
            1.0D,
            0L,
            Long.MAX_VALUE
        );
    }

    public boolean hasDurationSync() {
        return type == SequenceStepType.EFFECT && syncDurationWith != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
