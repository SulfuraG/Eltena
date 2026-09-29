package com.eltena.effect.client;

import com.eltena.effect.client.virtualcamera.VirtualCameraController;
import com.eltena.effect.effect.EarthquakeEffectManager;
import com.eltena.effect.effect.ScreenEffectManager;
import com.eltena.effect.effect.ScreenEffectShaderManager;
import com.eltena.effect.network.EltenaEffectNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

public final class EltenaEffectClient {
    private EltenaEffectClient() {
    }

    public static void bootstrap(ModContainer container) {
        IEventBus modBus = container.getEventBus();
        EarthquakeEffectManager.register(NeoForge.EVENT_BUS);
        ScreenEffectManager.register(NeoForge.EVENT_BUS);
        VirtualCameraController.register(NeoForge.EVENT_BUS);
        ScreenEffectShaderManager.register(modBus);
        EltenaEffectNetwork.bootstrap(modBus);
    }
}
