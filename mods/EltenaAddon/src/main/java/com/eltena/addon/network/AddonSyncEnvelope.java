package com.eltena.addon.network;

public record AddonSyncEnvelope<T extends SyncPayload>(
    AddonSyncChannel channel,
    int protocolVersion,
    T payload
) {
}
