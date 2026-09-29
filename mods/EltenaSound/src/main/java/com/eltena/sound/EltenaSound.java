package com.eltena.sound;

import com.eltena.sound.client.EltenaSoundClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(EltenaSound.MOD_ID)
public final class EltenaSound {
    public static final String MOD_ID = "eltenasound";

    public EltenaSound(ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EltenaSoundClient.bootstrap(container);
        }
    }
}
