package com.eltena.addon.network.model;

import com.eltena.addon.network.AddonSyncChannel;
import com.eltena.addon.network.SyncPayload;

/**
 * Addon-side DTO for the future EltenaCore -> EltenaAddon sync protocol.
 * This stays separate from the current placeholder network classes so the UI
 * can migrate from mock data to typed payloads without moving authority into the mod.
 */
public record ModSyncEnvelope<T extends SyncPayload>(
    AddonSyncChannel channel,
    int protocolVersion,
    String generatedAt,
    T payload
) {
}
