package com.eltena.addon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestC2SPayload(String json, int byteLength) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("eltena", "request_c2s");
    public static final Type<RequestC2SPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestC2SPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> EltenaSyncPayload.writeRawUtf8(buffer, payload.json()),
        buffer -> {
            EltenaSyncPayload.DecodedUtf8 decoded = EltenaSyncPayload.readRawUtf8(buffer);
            return new RequestC2SPayload(decoded.value(), decoded.transportByteLength());
        }
    );

    public RequestC2SPayload(String json) {
        this(json, EltenaSyncPayload.encodedByteLength(json));
    }

    @Override
    public Type<RequestC2SPayload> type() {
        return TYPE;
    }

    public int jsonByteLength() {
        return EltenaSyncPayload.utf8Length(json);
    }
}
