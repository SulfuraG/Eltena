package com.eltena.core.domain.player;

public record PlayerOptionSettings(
    boolean floatingDamage,
    boolean chatDamage,
    boolean expChat,
    boolean dpsChat
) {

    public static PlayerOptionSettings defaults() {
        return new PlayerOptionSettings(true, false, true, false);
    }

    public PlayerOptionSettings withFloatingDamage(boolean value) {
        return new PlayerOptionSettings(value, chatDamage, expChat, dpsChat);
    }

    public PlayerOptionSettings withChatDamage(boolean value) {
        return new PlayerOptionSettings(floatingDamage, value, expChat, dpsChat);
    }

    public PlayerOptionSettings withExpChat(boolean value) {
        return new PlayerOptionSettings(floatingDamage, chatDamage, value, dpsChat);
    }

    public PlayerOptionSettings withDpsChat(boolean value) {
        return new PlayerOptionSettings(floatingDamage, chatDamage, expChat, value);
    }
}
