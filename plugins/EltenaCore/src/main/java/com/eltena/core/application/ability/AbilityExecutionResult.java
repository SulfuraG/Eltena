package com.eltena.core.application.ability;

import com.eltena.core.domain.player.PlayerProfile;

import java.util.List;

public record AbilityExecutionResult(
    PlayerProfile profile,
    boolean executed,
    String abilityId,
    String denialReason,
    List<String> messages
) {
    public static final String INVALID_WEAPON = "INVALID_WEAPON";
    public static final String MYTHIC_EXECUTE_FAILED = "MYTHIC_EXECUTE_FAILED";
    public static final String MYTHIC_API_NOT_IMPLEMENTED = "MYTHIC_API_NOT_IMPLEMENTED";
    public static final String MYTHIC_PLUGIN_MISSING = "plugin-missing";
    public static final String MYTHIC_PLUGIN_DISABLED = "plugin-disabled";
    public static final String MYTHIC_API_UNAVAILABLE = "api-unavailable";
    public static final String MYTHIC_HELPER_UNAVAILABLE = "helper-unavailable";
    public static final String BUILTIN_EXECUTE_FAILED = "BUILTIN_EXECUTE_FAILED";

    public boolean denied() {
        return denialReason != null && !denialReason.isBlank();
    }
}
