package com.eltena.soundcore.ready;

public record SoundClientReadyState(
    String protocolVersion,
    boolean supportsLiveAssets
) {
    public SoundClientReadyState {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank()
            ? "unknown"
            : protocolVersion.trim();
    }
}
