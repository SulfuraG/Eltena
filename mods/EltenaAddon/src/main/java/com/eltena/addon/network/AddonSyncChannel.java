package com.eltena.addon.network;

public enum AddonSyncChannel {
    PLAYER_STATE("player_state"),
    ABILITY_STATE("ability_state"),
    SKILL_TREE("skill_tree"),
    NOTIFICATION("notification");

    private final String id;

    AddonSyncChannel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AddonSyncChannel fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (AddonSyncChannel channel : values()) {
            if (channel.id.equalsIgnoreCase(id)) {
                return channel;
            }
        }
        return null;
    }
}
