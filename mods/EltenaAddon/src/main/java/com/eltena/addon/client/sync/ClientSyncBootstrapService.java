package com.eltena.addon.client.sync;

import com.eltena.addon.network.EltenaAddonNetwork;
import com.eltena.addon.network.ModSyncClient;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ClientSyncBootstrapService {
    private static final int INITIAL_DELAY_TICKS = 20;
    private static final int RETRY_INTERVAL_TICKS = 40;
    private static final int MAX_RETRY_COUNT = 6;
    private static final String DEBUG_PROPERTY = "eltena.syncDebug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";

    private static UUID currentPlayerId;
    private static int joinedTicks;
    private static int retryCount;
    private static int lastRequestTick = Integer.MIN_VALUE;

    private ClientSyncBootstrapService() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(ClientSyncBootstrapService::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            if (currentPlayerId != null) {
                ModSyncClient.resetAllSyncState("client-disconnect");
                if (isDebugEnabled()) {
                    System.out.println("[EltenaAddon] Live初期同期をリセット: reason=client-disconnect");
                }
            }
            currentPlayerId = null;
            joinedTicks = 0;
            retryCount = 0;
            lastRequestTick = Integer.MIN_VALUE;
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        if (!playerId.equals(currentPlayerId)) {
            currentPlayerId = playerId;
            joinedTicks = 0;
            retryCount = 0;
            lastRequestTick = Integer.MIN_VALUE;
            ModSyncClient.resetAllSyncState("player-session-changed");
            if (isDebugEnabled()) {
                System.out.println("[EltenaAddon] Live初期同期を開始: player=" + minecraft.player.getName().getString());
            }
        }

        joinedTicks++;
        if (ModSyncClient.hasLivePlayerState()) {
            return;
        }
        if (joinedTicks < INITIAL_DELAY_TICKS) {
            return;
        }
        if (retryCount >= MAX_RETRY_COUNT) {
            return;
        }
        if (joinedTicks - lastRequestTick < RETRY_INTERVAL_TICKS) {
            return;
        }

        EltenaAddonNetwork.sendInitialSyncRequest("client-bootstrap");
        retryCount++;
        lastRequestTick = joinedTicks;
        if (isDebugEnabled()) {
            System.out.println(
                "[EltenaAddon] 初期sync_request送信:"
                    + " player=" + minecraft.player.getName().getString()
                    + " retry=" + retryCount + "/" + MAX_RETRY_COUNT
                    + " joinedTicks=" + joinedTicks
            );
        }
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }
}
