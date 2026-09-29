package com.eltena.addon.client.ui;

import com.eltena.addon.EltenaAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class AddonUiFont {
    public static final ResourceLocation UI_FONT = ResourceLocation.fromNamespaceAndPath(EltenaAddon.MOD_ID, "ui");

    private AddonUiFont() {
    }

    public static MutableComponent text(String text) {
        return apply(Component.literal(text));
    }

    public static MutableComponent translatable(String key, Object... args) {
        return apply(Component.translatable(key, args));
    }

    public static MutableComponent apply(Component component) {
        return component.copy().withStyle(style -> style.withFont(UI_FONT));
    }
}
