package com.eltena.addon.client;

import com.eltena.addon.client.hud.AbilityRadialOverlay;
import com.eltena.addon.client.hud.EltenaInventoryStatusPanel;
import com.eltena.addon.client.hud.EltenaNotificationOverlay;
import com.eltena.addon.client.hud.EltenaStatusHudOverlay;
import com.eltena.addon.client.input.EltenaKeyMappings;
import com.eltena.addon.client.input.KeyInputHandler;
import com.eltena.addon.client.item.CleanModTooltipSuppressor;
import com.eltena.addon.client.sync.ClientSyncBootstrapService;
import com.eltena.addon.network.EltenaAddonNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

public final class EltenaAddonClient {
    private EltenaAddonClient() {
    }

    public static void bootstrap(ModContainer container) {
        IEventBus modBus = container.getEventBus();
        EltenaKeyMappings.register(modBus);
        EltenaStatusHudOverlay.register(NeoForge.EVENT_BUS);
        EltenaInventoryStatusPanel.register(NeoForge.EVENT_BUS);
        EltenaNotificationOverlay.register(NeoForge.EVENT_BUS);
        AbilityRadialOverlay.register(NeoForge.EVENT_BUS);
        CleanModTooltipSuppressor.register(NeoForge.EVENT_BUS);
        KeyInputHandler.register(NeoForge.EVENT_BUS);
        ClientSyncBootstrapService.register(NeoForge.EVENT_BUS);
        EltenaAddonNetwork.bootstrap(modBus);
    }
}
