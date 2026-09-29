package com.eltena.addon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncS2CPayload(String json, int byteLength, int jsonByteLength, int remainingReadableBytes) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("eltena", "sync_s2c");
    public static final Type<SyncS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncS2CPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> EltenaSyncPayload.writeRawUtf8(buffer, payload.json()),
        buffer -> {
            EltenaSyncPayload.DecodedUtf8 decoded = EltenaSyncPayload.readRawUtf8(buffer);
            return new SyncS2CPayload(
                decoded.value(),
                decoded.transportByteLength(),
                decoded.utf8Length(),
                decoded.remainingReadableBytes()
            );
        }
    );

    public SyncS2CPayload(String json) {
        this(json, EltenaSyncPayload.encodedByteLength(json), EltenaSyncPayload.utf8Length(json), 0);
    }

    @Override
    public Type<SyncS2CPayload> type() {
        return TYPE;
    }
}
