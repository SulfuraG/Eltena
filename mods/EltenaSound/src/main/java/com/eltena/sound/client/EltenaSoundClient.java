package com.eltena.sound.client;

import com.eltena.sound.network.EltenaSoundNetwork;
import com.eltena.sound.sound.SoundPlaybackManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

public final class EltenaSoundClient {
    private EltenaSoundClient() {
    }

    public static void bootstrap(ModContainer container) {
        IEventBus modBus = container.getEventBus();
        SoundPlaybackManager.register(NeoForge.EVENT_BUS);
        EltenaSoundNetwork.bootstrap(modBus, NeoForge.EVENT_BUS);
    }
}
