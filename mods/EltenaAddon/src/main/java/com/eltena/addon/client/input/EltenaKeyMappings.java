package com.eltena.addon.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class EltenaKeyMappings {
    public static final String CATEGORY = "key.categories.eltenaaddon";

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.eltenaaddon.open_menu",
        InputConstants.KEY_M,
        CATEGORY
    );

    public static final KeyMapping OPEN_ABILITY_RADIAL = new KeyMapping(
        "key.eltenaaddon.open_ability_radial",
        InputConstants.KEY_B,
        CATEGORY
    );

    public static final KeyMapping CAST_SELECTED_ABILITY = new KeyMapping(
        "key.eltenaaddon.cast_selected_ability",
        InputConstants.KEY_N,
        CATEGORY
    );

    private EltenaKeyMappings() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EltenaKeyMappings::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(OPEN_ABILITY_RADIAL);
        event.register(CAST_SELECTED_ABILITY);
    }
}
