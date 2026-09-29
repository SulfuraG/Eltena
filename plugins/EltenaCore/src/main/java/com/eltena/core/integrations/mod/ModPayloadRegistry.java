package com.eltena.core.integrations.mod;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class ModPayloadRegistry {

    private final Map<ModSyncChannel, Function<ModSyncContext, Map<String, Object>>> builders = new EnumMap<>(ModSyncChannel.class);

    public void register(ModSyncChannel channel, Function<ModSyncContext, Map<String, Object>> builder) {
        builders.put(Objects.requireNonNull(channel, "channel"), Objects.requireNonNull(builder, "builder"));
    }

    public Map<String, Object> build(ModSyncChannel channel, ModSyncContext context) {
        Function<ModSyncContext, Map<String, Object>> builder = builders.get(channel);
        if (builder == null) {
            throw new IllegalStateException("No mod payload builder is registered for channel: " + channel.id());
        }
        return builder.apply(context);
    }

    public Collection<ModSyncChannel> channels() {
        return builders.keySet();
    }
}
