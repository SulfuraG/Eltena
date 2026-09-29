package com.eltena.addon;

import com.eltena.addon.client.EltenaAddonClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(EltenaAddon.MOD_ID)
public final class EltenaAddon {
    public static final String MOD_ID = "eltenaaddon";

    public EltenaAddon(ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EltenaAddonClient.bootstrap(container);
        }
    }
}
