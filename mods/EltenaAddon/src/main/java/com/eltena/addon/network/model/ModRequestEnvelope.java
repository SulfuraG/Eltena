package com.eltena.addon.network.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ModRequestEnvelope(
    String channel,
    String action,
    UUID player,
    Map<String, Object> data
) {
    public static final String SKILL_REQUEST_CHANNEL = "skill_request";
    public static final String ABILITY_REQUEST_CHANNEL = "ability_request";
    public static final String SYNC_REQUEST_CHANNEL = "sync_request";
    public static final String SKILL_LEARN_ACTION = "learn";
    public static final String ABILITY_CAST_ACTION = "cast";
    public static final String ABILITY_ASSIGN_RADIAL_ACTION = "assign_radial";
    public static final String ABILITY_CLEAR_RADIAL_ACTION = "clear_radial";
    public static final String SYNC_PULL_ACTION = "pull_state";

    public ModRequestEnvelope {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static ModRequestEnvelope skillLearn(UUID playerId, String skillId) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("skillId", skillId);
        return new ModRequestEnvelope(SKILL_REQUEST_CHANNEL, SKILL_LEARN_ACTION, playerId, data);
    }

    public static ModRequestEnvelope abilityUse(UUID playerId, String abilityId) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("abilityId", abilityId);
        return new ModRequestEnvelope(ABILITY_REQUEST_CHANNEL, ABILITY_CAST_ACTION, playerId, data);
    }

    public static ModRequestEnvelope abilityAssignRadial(UUID playerId, int slot, String abilityId) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("slot", slot);
        data.put("abilityId", abilityId);
        return new ModRequestEnvelope(ABILITY_REQUEST_CHANNEL, ABILITY_ASSIGN_RADIAL_ACTION, playerId, data);
    }

    public static ModRequestEnvelope abilityClearRadial(UUID playerId, int slot) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("slot", slot);
        return new ModRequestEnvelope(ABILITY_REQUEST_CHANNEL, ABILITY_CLEAR_RADIAL_ACTION, playerId, data);
    }

    public static ModRequestEnvelope syncPull(UUID playerId, String source) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("source", source == null ? "" : source);
        return new ModRequestEnvelope(SYNC_REQUEST_CHANNEL, SYNC_PULL_ACTION, playerId, data);
    }
}
