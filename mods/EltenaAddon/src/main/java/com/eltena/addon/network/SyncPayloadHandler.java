package com.eltena.addon.network;

@FunctionalInterface
public interface SyncPayloadHandler<T extends SyncPayload> {
    void handle(T payload);
}
