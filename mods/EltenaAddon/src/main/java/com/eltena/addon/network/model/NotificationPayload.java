package com.eltena.addon.network.model;

import com.eltena.addon.network.SyncPayload;

public record NotificationPayload(
    String message,
    String level
) implements SyncPayload {
}
