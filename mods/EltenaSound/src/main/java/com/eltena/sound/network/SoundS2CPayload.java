package com.eltena.sound.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SoundS2CPayload(
    String json,
    int byteLength,
    int jsonByteLength,
    int remainingReadableBytes
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("eltena", "sound");
    public static final Type<SoundS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SoundS2CPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> SoundRawPayload.writeRawUtf8(buffer, payload.json()),
            buffer -> {
                SoundRawPayload.DecodedUtf8 decoded = SoundRawPayload.readRawUtf8(buffer);
                return new SoundS2CPayload(
                    decoded.value(),
                    decoded.transportByteLength(),
                    decoded.utf8Length(),
                    decoded.remainingReadableBytes()
                );
            }
        );

    public static SoundS2CPayload ofJson(String json) {
        byte[] bytes = json == null ? new byte[0] : json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new SoundS2CPayload(
            json == null ? "" : json,
            bytes.length,
            bytes.length,
            0
        );
    }

    @Override
    public Type<SoundS2CPayload> type() {
        return TYPE;
    }
}
