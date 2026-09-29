package com.eltena.core.integrations.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModPayloadSerializer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public String serialize(ModSyncEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        JsonObject root = new JsonObject();
        root.addProperty("channel", envelope.channel().id());
        root.addProperty("channel-display-name", envelope.channel().displayName());
        root.addProperty("protocol-version", envelope.protocolVersion());
        root.addProperty("generated-at", envelope.generatedAt().toString());
        root.add("payload", writeObject(envelope.payload()));
        return GSON.toJson(root);
    }

    private JsonObject writeObject(Map<String, Object> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            object.add(entry.getKey(), toJson(entry.getValue()));
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    private JsonElement toJson(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return GSON.toJsonTree(value);
        }
        if (value instanceof Map<?, ?> map) {
            return writeObject((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object entry : list) {
                array.add(toJson(entry));
            }
            return array;
        }
        return GSON.toJsonTree(value.toString());
    }
}
