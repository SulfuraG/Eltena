package com.eltena.core.integrations.mod;

import java.time.Instant;
import java.util.Map;

public record ModSyncEnvelope(
    ModSyncChannel channel,
    int protocolVersion,
    Instant generatedAt,
    Map<String, Object> payload
) {
}
