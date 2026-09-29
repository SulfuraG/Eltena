package com.eltena.core.integrations.mod;

public enum ModSyncChannel {
    PLAYER_STATE("player_state", "Player State"),
    ABILITY_STATE("ability_state", "Ability State"),
    SKILL_TREE("skill_tree", "Skill Tree"),
    NOTIFICATION("notification", "Notification");

    private final String id;
    private final String displayName;

    ModSyncChannel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
