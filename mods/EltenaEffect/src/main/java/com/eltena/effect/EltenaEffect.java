package com.eltena.effect;

import com.eltena.effect.client.EltenaEffectClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(EltenaEffect.MOD_ID)
public final class EltenaEffect {
    public static final String MOD_ID = "eltenaeffect";

    public EltenaEffect(ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EltenaEffectClient.bootstrap(container);
        }
    }
}
