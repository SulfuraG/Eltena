package com.eltena.core.integrations.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.eltena.core.application.ability.AbilityExecutionResult;
import com.eltena.core.application.ability.AbilitySystem;
import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.application.skill.SkillProgressResult;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ModRequestListener implements PluginMessageListener {
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_:\\-]{1,80}");

    private final ServiceRegistry services;
    private final ModSyncPluginMessenger messenger;

    public ModRequestListener(ServiceRegistry services, ModSyncPluginMessenger messenger) {
        this.services = Objects.requireNonNull(services, "services");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ModSyncPluginMessenger.REQUEST_C2S_CHANNEL_NAME.equals(channel) || player == null || message == null || message.length == 0) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(new String(message, StandardCharsets.UTF_8)).getAsJsonObject();
            String requestChannel = readString(root, "channel");
            String action = readString(root, "action");
            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : new JsonObject();
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Addon request received:"
                        + " player=" + player.getName()
                        + " channel=" + requestChannel
                        + " action=" + action
                        + " abilityId=" + readString(data, "abilityId")
                        + " skillId=" + readString(data, "skillId")
                        + " slot=" + readInt(data, "slot")
                );
            }

            switch (requestChannel) {
                case "sync_request" -> handleSyncRequest(player, action, data);
                case "skill_request" -> handleSkillRequest(player, action, data);
                case "ability_request" -> handleAbilityRequest(player, action, data);
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to decode addon request from " + player.getName() + ": " + exception.getMessage());
            resendAuthoritativeState(player);
        }
    }

    private void handleSyncRequest(Player player, String action, JsonObject data) {
        if (!"pull_state".equalsIgnoreCase(action)) {
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().warning(
                    "[EltenaCore] Ignored sync_request:"
                        + " player=" + player.getName()
                        + " action=" + action
                );
            }
            return;
        }
        if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
            services.plugin().getLogger().info(
                "[EltenaCore] Accepted sync_request:"
                    + " player=" + player.getName()
                    + " source=" + readString(data, "source")
            );
        }
        messenger.sendInitialSyncBundle(player);
    }

    private void handleSkillRequest(Player player, String action, JsonObject data) {
        if (!"learn".equalsIgnoreCase(action)) {
            denySkill(player, readString(data, "skillId"), "unsupported-action");
            return;
        }

        String skillId = readString(data, "skillId");
        if (skillId.isBlank() || !SAFE_ID.matcher(skillId).matches()) {
            denySkill(player, skillId, "invalid-id");
            return;
        }

        if (services.skillCatalog().find(skillId) == null) {
            denySkill(player, skillId, "unknown-id");
            return;
        }

        try {
            SkillProgressResult result = services.skillSystem().learn(player.getUniqueId(), player.getName(), skillId);
            if (result.denied() || !result.progressed()) {
                denySkill(player, skillId, result.denialReason().isBlank() ? "denied" : result.denialReason());
                return;
            }
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Skill learned: "
                        + player.getName()
                        + " -> " + skillId
                        + " rank=" + result.rank() + "/" + result.maxRank()
                );
            }
        } catch (IOException | RuntimeException exception) {
            denySkill(player, skillId, "error:" + exception.getMessage());
            return;
        }

        resendAuthoritativeState(player);
    }

    private void handleAbilityRequest(Player player, String action, JsonObject data) {
        if (!player.hasPermission("eltenacore.ability.use")) {
            sendErrorMessage(player, "command.error.permission-denied");
            denyAbility(player, readString(data, "abilityId"), "permission-denied");
            return;
        }
        switch (action.toLowerCase()) {
            case "use", "cast" -> handleAbilityUseRequest(player, data);
            case "use_slot" -> handleAbilityUseSlotRequest(player, data);
            case "equip" -> handleAbilityEquipRequest(player, data);
            case "assign_radial" -> handleAbilityAssignRadialRequest(player, data);
            case "clear_radial" -> handleAbilityClearRadialRequest(player, data);
            default -> denyAbility(player, readString(data, "abilityId"), "unsupported-action");
        }
    }

    private void handleAbilityUseRequest(Player player, JsonObject data) {
        String abilityId = readString(data, "abilityId");
        if (abilityId.isBlank() || !SAFE_ID.matcher(abilityId).matches()) {
            denyAbility(player, abilityId, "invalid-id");
            return;
        }
        if (services.abilityCatalog().find(abilityId) == null) {
            denyAbility(player, abilityId, "unknown-id");
            return;
        }
        try {
            AbilityExecutionResult result = services.abilitySystem().useAbility(player, abilityId);
            if (!result.executed()) {
                denyAbility(player, abilityId, result.denialReason().isBlank() ? "denied" : result.denialReason());
                return;
            }
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info("[EltenaCore] Ability used: " + player.getName() + " -> " + abilityId);
            }
        } catch (IOException | RuntimeException exception) {
            denyAbility(player, abilityId, "error:" + exception.getMessage());
            return;
        }
        resendAbilityState(player);
    }

    private void handleAbilityUseSlotRequest(Player player, JsonObject data) {
        int slot = readInt(data, "slot");
        if (slot < 1 || slot > 4) {
            denyAbilitySlot(player, slot, "INVALID_SLOT");
            return;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            String abilityId = services.abilitySystem().equippedAbilityId(profile, slot);
            if (abilityId.isBlank()) {
                denyAbilitySlot(player, slot, "EMPTY_SLOT");
                return;
            }
            AbilityExecutionResult result = services.abilitySystem().useAbility(player, abilityId);
            if (!result.executed()) {
                denyAbilitySlot(player, slot, result.denialReason().isBlank() ? "denied" : result.denialReason());
                return;
            }
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info("[EltenaCore] Ability slot used: " + player.getName() + " slot=" + slot + " ability=" + abilityId);
            }
        } catch (IOException | RuntimeException exception) {
            denyAbilitySlot(player, slot, "error:" + exception.getMessage());
            return;
        }
        resendAbilityState(player);
    }

    private void handleAbilityEquipRequest(Player player, JsonObject data) {
        int slot = readInt(data, "slot");
        String abilityId = readString(data, "abilityId");
        if (slot < 1 || slot > 4) {
            denyAbility(player, abilityId, "invalid-slot");
            return;
        }
        if (abilityId.isBlank() || !SAFE_ID.matcher(abilityId).matches()) {
            denyAbility(player, abilityId, "invalid-id");
            return;
        }
        if (services.abilityCatalog().find(abilityId) == null) {
            denyAbility(player, abilityId, "unknown-id");
            return;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            if (!services.abilitySystem().isUnlocked(profile, abilityId)) {
                denyAbility(player, abilityId, "locked");
                return;
            }
            PlayerProfile updated = services.abilitySystem().equipAbility(profile, services.abilitySystem().slotKey(slot), abilityId);
            services.playerProfiles().save(updated);
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info("[EltenaCore] Ability equipped: " + player.getName() + " slot=" + slot + " ability=" + abilityId);
            }
        } catch (IOException | RuntimeException exception) {
            denyAbility(player, abilityId, "error:" + exception.getMessage());
            return;
        }
        resendAbilityState(player);
    }

    private void handleAbilityAssignRadialRequest(Player player, JsonObject data) {
        int slot = readInt(data, "slot");
        String abilityId = readString(data, "abilityId");
        if (slot < 0 || slot >= AbilitySystem.MAX_RADIAL_SLOTS) {
            denyAbility(player, abilityId, "invalid-slot");
            return;
        }
        if (abilityId.isBlank() || !SAFE_ID.matcher(abilityId).matches()) {
            denyAbility(player, abilityId, "invalid-id");
            return;
        }
        if (services.abilityCatalog().find(abilityId) == null) {
            denyAbility(player, abilityId, "unknown-id");
            return;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            if (!services.abilitySystem().isUnlocked(profile, abilityId)) {
                denyAbility(player, abilityId, "locked");
                return;
            }
            PlayerProfile updated = services.abilitySystem().equipAbility(profile, services.abilitySystem().radialSlotKey(slot), abilityId);
            services.playerProfiles().save(updated);
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Ability radial assigned:"
                        + " player=" + player.getName()
                        + " slot=" + slot
                        + " ability=" + abilityId
                );
            }
        } catch (IOException | RuntimeException exception) {
            denyAbility(player, abilityId, "error:" + exception.getMessage());
            return;
        }
        resendAbilityState(player);
    }

    private void handleAbilityClearRadialRequest(Player player, JsonObject data) {
        int slot = readInt(data, "slot");
        if (slot < 0 || slot >= AbilitySystem.MAX_RADIAL_SLOTS) {
            denyAbilitySlot(player, slot, "INVALID_SLOT");
            return;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            PlayerProfile updated = services.abilitySystem().unequipAbility(profile, services.abilitySystem().radialSlotKey(slot));
            services.playerProfiles().save(updated);
            if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Ability radial cleared:"
                        + " player=" + player.getName()
                        + " slot=" + slot
                );
            }
        } catch (IOException | RuntimeException exception) {
            denyAbilitySlot(player, slot, "error:" + exception.getMessage());
            return;
        }
        resendAbilityState(player);
    }

    private void denySkill(Player player, String skillId, String reason) {
        services.plugin().getLogger().warning(
            "[EltenaCore] Skill request denied: "
                + player.getName()
                + " -> " + (skillId == null || skillId.isBlank() ? "-" : skillId)
                + " reason=" + reason
        );
        resendAuthoritativeState(player);
    }

    private void denyAbility(Player player, String abilityId, String reason) {
        if ("permission-denied".equalsIgnoreCase(reason)) {
            sendErrorMessage(player, "command.error.permission-denied");
        }
        services.plugin().getLogger().warning(
            "[EltenaCore] Ability denied: "
                + player.getName()
                + " -> " + (abilityId == null || abilityId.isBlank() ? "-" : abilityId)
                + " reason=" + reason
        );
        resendAbilityState(player);
    }

    private void resendAuthoritativeState(Player player) {
        messenger.sendInitialSyncBundle(player);
    }

    private void resendAbilityState(Player player) {
        messenger.sendPlayerState(player);
        messenger.sendSkillTree(player);
        messenger.sendAbilityState(player);
    }

    private void denyAbilitySlot(Player player, int slot, String reason) {
        if (player != null) {
            if ("EMPTY_SLOT".equalsIgnoreCase(reason)) {
                sendErrorMessage(player, "ability.denied.empty-slot");
            } else if ("INVALID_SLOT".equalsIgnoreCase(reason)) {
                sendErrorMessage(player, "ability.denied.invalid-slot");
            }
        }
        services.plugin().getLogger().warning(
            "[EltenaCore] Ability denied: "
                + player.getName()
                + " slot=" + slot
                + " reason=" + reason
        );
        resendAbilityState(player);
    }

    private void sendErrorMessage(Player player, String key, Object... pairs) {
        if (player == null) {
            return;
        }
        messenger.sendNotification(player, services.messages().get(key, pairs), "warning");
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int readInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()
            ? object.get(key).getAsInt()
            : 0;
    }
}
