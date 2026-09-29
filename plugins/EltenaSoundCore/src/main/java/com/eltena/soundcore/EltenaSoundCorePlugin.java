package com.eltena.soundcore;

import com.eltena.soundcore.api.EltenaSoundApi;
import com.eltena.soundcore.api.EltenaSoundApiService;
import com.eltena.soundcore.command.EltenaSoundCommand;
import com.eltena.soundcore.config.LanguageService;
import com.eltena.soundcore.config.LiveAssetRegistry;
import com.eltena.soundcore.config.SoundRegistry;
import com.eltena.soundcore.config.VanillaMusicControlSettings;
import com.eltena.soundcore.listener.SoundPlayerSyncListener;
import com.eltena.soundcore.playback.LiveAssetPlaybackService;
import com.eltena.soundcore.playback.SoundPlaybackService;
import com.eltena.soundcore.ready.SoundClientReadyRegistry;
import com.eltena.soundcore.ready.SoundClientReadyState;
import com.eltena.soundcore.transport.SoundPluginMessenger;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class EltenaSoundCorePlugin extends JavaPlugin {
    private final DecimalFormat decimalFormat = new DecimalFormat(
        "0.##",
        DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private LanguageService languageService;
    private SoundRegistry soundRegistry;
    private LiveAssetRegistry liveAssetRegistry;
    private SoundPluginMessenger messenger;
    private SoundPlaybackService playbackService;
    private LiveAssetPlaybackService liveAssetPlaybackService;
    private VanillaMusicControlSettings vanillaMusicControlSettings;
    private EltenaSoundApiService soundApiService;
    private SoundClientReadyRegistry readyRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("language/ja_jp.yml");
        saveBundledResource("language/en_us.yml");
        saveBundledResource("sounds/se/dragon_roar.yml");
        saveBundledResource("live-assets/voice/dragon_roar_live.yml");
        saveBundledResource("live-assets/files/voice/dragon_roar_live.ogg");

        this.languageService = new LanguageService(this);
        this.soundRegistry = new SoundRegistry(this);
        this.liveAssetRegistry = new LiveAssetRegistry(this);
        this.readyRegistry = new SoundClientReadyRegistry();
        this.messenger = new SoundPluginMessenger(this);
        this.messenger.registerChannel();
        this.soundApiService = new EltenaSoundApiService(this);

        reloadPluginState();
        registerApiService();
        registerCommand();
        registerListeners();
        logInfo("log.enable", runtimeStatePlaceholders());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (messenger != null) {
            messenger.unregisterChannel();
        }
        if (languageService != null) {
            getLogger().info(languageService.plain("log.disable", Map.of()));
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        vanillaMusicControlSettings = VanillaMusicControlSettings.fromConfig(getConfig());
        languageService.load();
        soundRegistry.load();
        liveAssetRegistry.load();
        playbackService = new SoundPlaybackService(this, soundRegistry, messenger);
        liveAssetPlaybackService = new LiveAssetPlaybackService(this, liveAssetRegistry, messenger);
        playbackService.syncSoundConfig();
    }

    public LanguageService language() {
        return languageService;
    }

    public SoundRegistry soundRegistry() {
        return soundRegistry;
    }

    public SoundPlaybackService playbackService() {
        return playbackService;
    }

    public LiveAssetRegistry liveAssetRegistry() {
        return liveAssetRegistry;
    }

    public LiveAssetPlaybackService liveAssetPlaybackService() {
        return liveAssetPlaybackService;
    }

    public SoundClientReadyRegistry readyRegistry() {
        return readyRegistry;
    }

    public boolean isPlayerReady(Player player) {
        return readyRegistry != null && readyRegistry.isReady(player);
    }

    public SoundClientReadyState readyState(Player player) {
        return readyRegistry == null ? null : readyRegistry.state(player);
    }

    public void handleClientReady(Player player, String protocolVersion, boolean supportsLiveAssets) {
        if (player == null || !player.isOnline() || readyRegistry == null) {
            return;
        }
        if (isDebugLogEnabled()) {
            logInfo(
                "log.sound-client-ready-received",
                Map.of(
                    "player", player.getName(),
                    "protocolVersion", protocolVersion == null || protocolVersion.isBlank() ? "unknown" : protocolVersion,
                    "supportsLiveAssets", Boolean.toString(supportsLiveAssets)
                )
            );
        }
        readyRegistry.register(player, protocolVersion, supportsLiveAssets);
        if (isDebugLogEnabled()) {
            logInfo(
                "log.player-ready-registered",
                Map.of(
                    "player", player.getName(),
                    "protocolVersion", readyState(player).protocolVersion(),
                    "supportsLiveAssets", Boolean.toString(readyState(player).supportsLiveAssets())
                )
            );
        }
        if (playbackService != null && playbackService.syncSoundConfig(player) && isDebugLogEnabled()) {
            logInfo(
                "log.sound-config-sent-after-ready",
                Map.of(
                    "player", player.getName(),
                    "protocolVersion", readyState(player).protocolVersion()
                )
            );
        }
    }

    public void removeReadyPlayer(Player player) {
        if (player == null || readyRegistry == null) {
            return;
        }
        SoundClientReadyState removed = readyRegistry.remove(player.getUniqueId());
        if (removed != null && isDebugLogEnabled()) {
            logInfo(
                "log.player-ready-removed",
                Map.of(
                    "player", player.getName(),
                    "protocolVersion", removed.protocolVersion(),
                    "supportsLiveAssets", Boolean.toString(removed.supportsLiveAssets())
                )
            );
        }
    }

    public boolean isDebugLogEnabled() {
        return getConfig().getBoolean("debug-log", false);
    }

    public VanillaMusicControlSettings vanillaMusicControlSettings() {
        return vanillaMusicControlSettings;
    }

    public void sendPrefixed(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(languageService.prefixed(key, placeholders));
    }

    public void logInfo(String key, Map<String, String> placeholders) {
        getLogger().info(languageService.plain(key, placeholders));
    }

    public void logWarning(String key, Map<String, String> placeholders) {
        getLogger().warning(languageService.plain(key, placeholders));
    }

    public void log(Level level, String key, Map<String, String> placeholders, Throwable exception) {
        getLogger().log(level, languageService.plain(key, placeholders), exception);
    }

    public String formatDecimal(double value) {
        synchronized (decimalFormat) {
            return decimalFormat.format(value);
        }
    }

    public Map<String, String> runtimeStatePlaceholders() {
        LinkedHashMap<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sounds", Integer.toString(soundRegistry.count()));
        placeholders.put("liveAssets", Integer.toString(liveAssetRegistry.count()));
        placeholders.put("locale", languageService.locale());
        return placeholders;
    }

    private void saveBundledResource(String path) {
        File destination = new File(getDataFolder(), path);
        if (!destination.exists()) {
            saveResource(path, false);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("eltenasound");
        if (command == null) {
            throw new IllegalStateException("Command eltenasound is missing from plugin.yml");
        }
        EltenaSoundCommand executor = new EltenaSoundCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SoundPlayerSyncListener(this), this);
    }

    private void registerApiService() {
        if (soundApiService == null) {
            return;
        }
        getServer().getServicesManager().register(
            EltenaSoundApi.class,
            soundApiService,
            this,
            ServicePriority.Normal
        );
    }
}
