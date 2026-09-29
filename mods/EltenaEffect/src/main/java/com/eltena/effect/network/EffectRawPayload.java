package com.eltena.effect.network;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class EffectRawPayload {
    private EffectRawPayload() {
    }

    static DecodedUtf8 readRawUtf8(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length <= 0) {
            return new DecodedUtf8("", 0, 0, 0);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new DecodedUtf8(
            new String(bytes, StandardCharsets.UTF_8),
            bytes.length,
            bytes.length,
            buffer.readableBytes()
        );
    }

    static int writeRawUtf8(RegistryFriendlyByteBuf buffer, String json) {
        byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        buffer.writeBytes(bytes);
        return bytes.length;
    }

    record DecodedUtf8(
        String value,
        int utf8Length,
        int transportByteLength,
        int remainingReadableBytes
    ) {
    }
}
