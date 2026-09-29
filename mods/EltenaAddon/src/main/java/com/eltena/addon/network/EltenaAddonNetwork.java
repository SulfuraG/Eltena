package com.eltena.addon.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.eltena.addon.client.hud.EltenaNotificationOverlay;
import com.eltena.addon.client.screen.SkillTreeScreen;
import com.eltena.addon.network.model.AbilityRadialSlotPayload;
import com.eltena.addon.network.model.AbilityStatePayload;
import com.eltena.addon.network.model.AbilitySummaryPayload;
import com.eltena.addon.network.model.EquippedAbilitySlotPayload;
import com.eltena.addon.network.model.ModRequestEnvelope;
import com.eltena.addon.network.model.ModSyncEnvelope;
import com.eltena.addon.network.model.NotificationPayload;
import com.eltena.addon.network.model.PlayerStatePayload;
import com.eltena.addon.network.model.SkillNodePayload;
import com.eltena.addon.network.model.SkillTreePayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class EltenaAddonNetwork {
    private static final boolean SYNC_RECEIVE_ENABLED = true;
    private static final int REQUEST_PAYLOAD_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<AddonSyncChannel, SyncPayloadHandler<?>> HANDLERS = new EnumMap<>(AddonSyncChannel.class);

    private EltenaAddonNetwork() {
    }

    public static void bootstrap(IEventBus modBus) {
        register(AddonSyncChannel.PLAYER_STATE, payload -> {
        });
        register(AddonSyncChannel.ABILITY_STATE, payload -> {
        });
        register(AddonSyncChannel.SKILL_TREE, payload -> refreshOpenSkillTreeScreen());
        register(AddonSyncChannel.NOTIFICATION, payload -> {
            if (payload instanceof NotificationPayload notificationPayload) {
                EltenaNotificationOverlay.enqueue(notificationPayload);
            }
        });
        if (SYNC_RECEIVE_ENABLED) {
            modBus.addListener(EltenaAddonNetwork::onRegisterPayloadHandlers);
        } else {
            System.out.println("[EltenaAddon] Sync payload receiving is temporarily disabled.");
        }
    }

    public static <T extends SyncPayload> void register(AddonSyncChannel channel, SyncPayloadHandler<T> handler) {
        HANDLERS.put(channel, handler);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SyncPayload> void receive(AddonSyncEnvelope<T> envelope) {
        SyncPayloadHandler<T> handler = (SyncPayloadHandler<T>) HANDLERS.get(envelope.channel());
        if (handler != null) {
            handler.handle(envelope.payload());
        }
    }

    public static void sendSkillLearnRequest(String skillId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || skillId == null || skillId.isBlank()) {
            return;
        }
        sendRequest(ModRequestEnvelope.skillLearn(minecraft.player.getUUID(), skillId));
    }

    public static void sendAbilityUseRequest(String abilityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || abilityId == null || abilityId.isBlank()) {
            return;
        }
        sendRequest(ModRequestEnvelope.abilityUse(minecraft.player.getUUID(), abilityId));
    }

    public static void sendAbilityCastRequest(String abilityId) {
        sendAbilityUseRequest(abilityId);
    }

    public static void sendAbilityAssignRadialRequest(int slot, String abilityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || slot < 0 || slot >= 8 || abilityId == null || abilityId.isBlank()) {
            return;
        }
        sendRequest(ModRequestEnvelope.abilityAssignRadial(minecraft.player.getUUID(), slot, abilityId));
    }

    public static void sendAbilityClearRadialRequest(int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || slot < 0 || slot >= 8) {
            return;
        }
        sendRequest(ModRequestEnvelope.abilityClearRadial(minecraft.player.getUUID(), slot));
    }

    public static void sendInitialSyncRequest(String source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        sendRequest(ModRequestEnvelope.syncPull(minecraft.player.getUUID(), source));
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        if (isTransportDebugEnabled()) {
            System.out.println(
                "[EltenaAddon] Registering sync channels"
                    + " sync=" + SyncS2CPayload.ID
                    + " request=" + RequestC2SPayload.ID
            );
        }
        registrar.playToClient(SyncS2CPayload.TYPE, SyncS2CPayload.STREAM_CODEC, (payload, context) -> {
            try {
                if (isTransportDebugEnabled()) {
                    System.out.println(
                        "[EltenaAddon] Received payload"
                            + " transport=" + SyncS2CPayload.ID
                            + " payloadType=sync_s2c"
                            + " transportBytes=" + payload.byteLength()
                            + " jsonBytes=" + payload.jsonByteLength()
                            + " remainingReadableBytes=" + payload.remainingReadableBytes()
                    );
                }
                handleIncomingSync(payload.json());
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                ModSyncClient.markPlayerStateSyncError(message);
                ModSyncClient.markAbilityStateSyncError(message);
                ModSyncClient.markSkillTreeSyncError(message);
                System.err.println(
                    "[EltenaAddon] Failed to decode sync payload"
                        + " transport=" + SyncS2CPayload.ID
                        + " payloadType=sync_s2c"
                        + " transportBytes=" + payload.byteLength()
                        + " jsonBytes=" + payload.jsonByteLength()
                        + " reason=" + message
                );
            }
        });
        registrar.playToServer(RequestC2SPayload.TYPE, RequestC2SPayload.STREAM_CODEC, (payload, context) -> {
            // The addon only needs this registration so the client advertises request_c2s support.
        });
    }

    private static void sendRequest(ModRequestEnvelope envelope) {
        SerializedRequest serialized = serializeRequestEnvelope(envelope);
        RequestC2SPayload payload = new RequestC2SPayload(serialized.json());
        PacketDistributor.sendToServer(payload);
        if (isTransportDebugEnabled()) {
            System.out.println(
                "[EltenaAddon] Sent "
                    + envelope.channel()
                    + " request action=" + envelope.action()
                    + " requestId=" + serialized.requestId()
                    + " payloadBytes=" + payload.byteLength()
                    + " jsonBytes=" + payload.jsonByteLength()
            );
        }
    }

    private static SerializedRequest serializeRequestEnvelope(ModRequestEnvelope envelope) {
        JsonObject root = new JsonObject();
        String requestId = UUID.randomUUID().toString();
        root.addProperty("payload-version", REQUEST_PAYLOAD_VERSION);
        root.addProperty("request-id", requestId);
        root.addProperty("channel", envelope.channel());
        root.addProperty("action", envelope.action());
        root.addProperty("player", envelope.player().toString());
        root.add("data", GSON.toJsonTree(envelope.data()));
        return new SerializedRequest(requestId, GSON.toJson(root));
    }

    private static boolean isTransportDebugEnabled() {
        return Boolean.getBoolean("eltena.syncDebug") || Boolean.getBoolean("eltena.debug");
    }

    private static void handleIncomingSync(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        AddonSyncChannel channel = AddonSyncChannel.fromId(readString(root, "channel", ""));
        if (channel == null) {
            System.out.println("[EltenaAddon] Unknown sync channel: " + readString(root, "channel", ""));
            return;
        }
        JsonObject payloadObject = root.has("payload") && root.get("payload").isJsonObject()
            ? root.getAsJsonObject("payload")
            : new JsonObject();
        int protocolVersion = readInt(root, "protocol-version", 1);
        String generatedAt = readString(root, "generated-at", "");

        switch (channel) {
            case PLAYER_STATE -> {
                PlayerStatePayload payload = parsePlayerStatePayload(payloadObject);
                ModSyncEnvelope<PlayerStatePayload> envelope = new ModSyncEnvelope<>(channel, protocolVersion, generatedAt, payload);
                ModSyncClient.acceptEnvelope(envelope);
                receive(new AddonSyncEnvelope<>(channel, envelope.protocolVersion(), payload));
            }
            case ABILITY_STATE -> {
                AbilityStatePayload payload = parseAbilityStatePayload(payloadObject);
                ModSyncEnvelope<AbilityStatePayload> envelope = new ModSyncEnvelope<>(channel, protocolVersion, generatedAt, payload);
                ModSyncClient.acceptEnvelope(envelope);
                receive(new AddonSyncEnvelope<>(channel, envelope.protocolVersion(), payload));
            }
            case SKILL_TREE -> {
                SkillTreePayload payload = parseSkillTreePayload(payloadObject);
                ModSyncEnvelope<SkillTreePayload> envelope = new ModSyncEnvelope<>(channel, protocolVersion, generatedAt, payload);
                ModSyncClient.acceptEnvelope(envelope);
                receive(new AddonSyncEnvelope<>(channel, envelope.protocolVersion(), payload));
            }
            case NOTIFICATION -> {
                NotificationPayload payload = new NotificationPayload(
                    readString(payloadObject, "message", ""),
                    readString(payloadObject, "level", "info")
                );
                receive(new AddonSyncEnvelope<>(channel, protocolVersion, payload));
            }
            default -> System.out.println("[EltenaAddon] Ignored sync channel: " + channel.id());
        }
    }

    private static PlayerStatePayload parsePlayerStatePayload(JsonObject payloadObject) {
        return new PlayerStatePayload(
            readStringAny(payloadObject, "Player", "player-name", "playerName"),
            readStringAny(payloadObject, "", "player-uuid", "playerUuid"),
            readIntAny(payloadObject, 1, "level"),
            readLongAny(payloadObject, 0L, "exp", "experience"),
            Math.max(1L, readLongAny(payloadObject, 100L, "max-exp", "maxExp")),
            readStringAny(payloadObject, "novice", "current-job-id", "currentJobId"),
            readStringAny(payloadObject, "見習い", "current-job-name", "currentJobName"),
            readStringAny(payloadObject, "unranked", "world-rank-id", "worldRankId"),
            readStringAny(payloadObject, "未階位", "world-rank-name", "worldRankName"),
            readLongAny(payloadObject, 0L, "world-rank-exp", "worldRankExp"),
            readStringAny(payloadObject, "none", "current-title-id", "currentTitleId", "active-title-id"),
            readStringAny(payloadObject, "なし", "current-title-name", "currentTitleName", "active-title-name"),
            readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "hp", 20), "hp"),
            Math.max(1, readIntAny(payloadObject, 20, "max-hp", "maxHp")),
            readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "mp", 10), "mp"),
            Math.max(1, readIntAny(payloadObject, 20, "max-mp", "maxMp")),
            readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "attack", 5), "attack"),
            readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "base-attack", readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "attack", 5), "attack")), "base-attack", "baseAttack"),
            readDoubleAny(payloadObject, readNestedDouble(payloadObject, "stats", "weapon-attack", 0.0D), "weapon-attack", "weaponAttack"),
            readDoubleAny(payloadObject, readNestedDouble(payloadObject, "stats", "attack-power", Math.max(1.0D, readDoubleAny(payloadObject, readNestedDouble(payloadObject, "stats", "attack", 5.0D), "attack") + readDoubleAny(payloadObject, readNestedDouble(payloadObject, "stats", "weapon-attack", 0.0D), "weapon-attack", "weaponAttack"))), "attack-power", "attackPower"),
            readIntAny(payloadObject, readNestedInt(payloadObject, "stats", "defense", 0), "defense"),
            readStringAny(payloadObject, "", "weapon-type", "weaponType", "item-type", "itemType"),
            readStringAny(payloadObject, "", "weapon-category", "weaponCategory", "item-category", "itemCategory"),
            readIntAny(payloadObject, 0, "skill-point", "skillPoint")
        );
    }

    private static AbilityStatePayload parseAbilityStatePayload(JsonObject payloadObject) {
        List<AbilitySummaryPayload> unlocked = new ArrayList<>();
        for (JsonElement element : readArray(payloadObject, "unlocked-abilities")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject abilityObject = element.getAsJsonObject();
            unlocked.add(new AbilitySummaryPayload(
                readString(abilityObject, "id", ""),
                readStringAny(abilityObject, "", "display-name", "displayName"),
                readStringList(abilityObject, "description"),
                readString(abilityObject, "category", ""),
                readString(abilityObject, "icon", ""),
                readIntAny(abilityObject, 0, "cooldown"),
                readIntAny(abilityObject, 0, "mp-cost", "mpCost"),
                readDoubleAny(abilityObject, 0.0D, "cooldown-remaining", "cooldownRemaining")
            ));
        }
        List<EquippedAbilitySlotPayload> equipped = new ArrayList<>();
        for (JsonElement element : readArray(payloadObject, "equipped-slots")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject slotObject = element.getAsJsonObject();
            equipped.add(new EquippedAbilitySlotPayload(
                readInt(slotObject, "slot", 0),
                readStringAny(slotObject, "", "key-name", "keyName"),
                readStringAny(slotObject, "", "ability-id", "abilityId"),
                readStringAny(slotObject, "", "display-name", "displayName")
            ));
        }
        List<AbilityRadialSlotPayload> radialSlots = new ArrayList<>();
        for (JsonElement element : readArray(payloadObject, "radial-slots")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject slotObject = element.getAsJsonObject();
            radialSlots.add(new AbilityRadialSlotPayload(
                readInt(slotObject, "slot", 0),
                readStringAny(slotObject, "", "direction-label", "directionLabel"),
                readStringAny(slotObject, "", "ability-id", "abilityId"),
                readStringAny(slotObject, "", "display-name", "displayName"),
                readString(slotObject, "icon", ""),
                readString(slotObject, "category", ""),
                readInt(slotObject, "cooldown", 0),
                readDoubleAny(slotObject, 0.0D, "cooldown-remaining", "cooldownRemaining")
            ));
        }
        return new AbilityStatePayload(
            unlocked,
            equipped,
            radialSlots,
            readIntAny(payloadObject, unlocked.size(), "total-abilities", "totalAbilities")
        );
    }

    private static SkillTreePayload parseSkillTreePayload(JsonObject payloadObject) {
        List<SkillNodePayload> nodes = new ArrayList<>();
        JsonArray nodeArray = readArray(payloadObject, "nodes");
        if (nodeArray.isEmpty()) {
            nodeArray = readArray(payloadObject, "jobs");
        }
        for (JsonElement element : nodeArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject nodeObject = element.getAsJsonObject();
            nodes.add(new SkillNodePayload(
                readString(nodeObject, "id", ""),
                readString(nodeObject, "display-name", ""),
                readStringList(nodeObject, "description"),
                readString(nodeObject, "category", readString(nodeObject, "tier", "")),
                readString(nodeObject, "icon", ""),
                readInt(nodeObject, "current-rank", readBoolean(nodeObject, "active", false) ? 1 : 0),
                readInt(nodeObject, "max-rank", 1),
                readInt(nodeObject, "required-points", 0),
                readInt(nodeObject, "required-level", 0),
                readString(nodeObject, "state", readBoolean(nodeObject, "unlocked", false) ? "available" : "locked"),
                readStringList(nodeObject, "prerequisites"),
                readStringList(nodeObject, "requires-display"),
                readInt(nodeObject, "position-x", 0),
                readInt(nodeObject, "position-y", 0),
                readStringList(nodeObject, "effects-display"),
                readStringList(nodeObject, "unlock-abilities-display")
            ));
        }
        return new SkillTreePayload(
            readStringAny(payloadObject, "", "current-job-id", "currentJobId"),
            readStringAny(payloadObject, "", "current-job-name", "currentJobName"),
            readIntAny(payloadObject, 0, "skill-point", "skillPoint"),
            nodes,
            readObjectStringFieldList(payloadObject, "titles", "id"),
            readObjectStringFieldList(payloadObject, "titles", "display-name")
        );
    }

    private static void refreshOpenSkillTreeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SkillTreeScreen skillTreeScreen) {
            skillTreeScreen.refreshFromSync();
        }
    }

    private static JsonArray readArray(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonArray()) {
            return object.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private static List<String> readStringList(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : readArray(object, key)) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static List<String> readObjectStringFieldList(JsonObject object, String key, String field) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : readArray(object, key)) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            } else if (element.isJsonObject()) {
                values.add(readString(element.getAsJsonObject(), field, ""));
            }
        }
        return values;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static String readStringAny(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(key);
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
        }
        return fallback;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : fallback;
    }

    private static int readIntAny(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }
        }
        return fallback;
    }

    private static long readLongAny(JsonObject object, long fallback, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsLong();
            }
        }
        return fallback;
    }

    private static double readDoubleAny(JsonObject object, double fallback, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble();
            }
        }
        return fallback;
    }

    private static int readNestedInt(JsonObject object, String nestedKey, String key, int fallback) {
        if (!object.has(nestedKey) || !object.get(nestedKey).isJsonObject()) {
            return fallback;
        }
        return readInt(object.getAsJsonObject(nestedKey), key, fallback);
    }

    private static double readNestedDouble(JsonObject object, String nestedKey, String key, double fallback) {
        if (!object.has(nestedKey) || !object.get(nestedKey).isJsonObject()) {
            return fallback;
        }
        return readDoubleAny(object.getAsJsonObject(nestedKey), fallback, key);
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
    }

    private record SerializedRequest(String requestId, String json) {
    }
}
