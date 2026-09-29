package com.eltena.effect.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EffectS2CPayload(
    String json,
    int byteLength,
    int jsonByteLength,
    int remainingReadableBytes
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("eltena", "effect");
    public static final Type<EffectS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectS2CPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> EffectRawPayload.writeRawUtf8(buffer, payload.json()),
            buffer -> {
                EffectRawPayload.DecodedUtf8 decoded = EffectRawPayload.readRawUtf8(buffer);
                return new EffectS2CPayload(
                    decoded.value(),
                    decoded.transportByteLength(),
                    decoded.utf8Length(),
                    decoded.remainingReadableBytes()
                );
            }
        );

    @Override
    public Type<EffectS2CPayload> type() {
        return TYPE;
    }
}
