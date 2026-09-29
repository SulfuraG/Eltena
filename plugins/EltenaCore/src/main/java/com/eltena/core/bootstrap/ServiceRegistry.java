package com.eltena.core.bootstrap;

import com.eltena.core.api.player.PlayerProfileRepository;
import com.eltena.core.api.stats.PlayerStatsRepository;
import com.eltena.core.application.ability.AbilityCatalog;
import com.eltena.core.application.ability.AbilityLoader;
import com.eltena.core.application.ability.AbilitySystem;
import com.eltena.core.application.display.DisplayNameResolver;
import com.eltena.core.application.equipment.EquipmentCatalog;
import com.eltena.core.application.equipment.EquipmentLoader;
import com.eltena.core.application.equipment.EquipmentSystem;
import com.eltena.core.application.equipment.MmoItemsDefinitionMappingRepository;
import com.eltena.core.application.equipment.MmoItemsDefinitionStatsProvider;
import com.eltena.core.application.equipment.MmoItemsEquipmentStatsProvider;
import com.eltena.core.application.equipment.WeaponAttackSnapshotResolver;
import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.application.growth.WeaponTypeResolver;
import com.eltena.core.application.job.JobCatalog;
import com.eltena.core.application.job.JobSystem;
import com.eltena.core.application.player.FinalPlayerStatsCalculator;
import com.eltena.core.application.player.PlayerDerivedStatsService;
import com.eltena.core.application.player.PlayerManaRegenerationService;
import com.eltena.core.application.player.PlayerManaService;
import com.eltena.core.application.player.PlayerResetService;
import com.eltena.core.application.progression.LevelSystem;
import com.eltena.core.application.progression.MobKillExperienceService;
import com.eltena.core.application.progression.StatGrowthSystem;
import com.eltena.core.application.progression.WeaponMasterySystem;
import com.eltena.core.application.settings.PlayerDamageFeedbackService;
import com.eltena.core.application.settings.PlayerOptionSettingsService;
import com.eltena.core.application.settings.PlayerSettingsMenu;
import com.eltena.core.application.settings.PlayerSettingsMenuListener;
import com.eltena.core.application.skill.SkillCatalog;
import com.eltena.core.application.skill.SkillLoader;
import com.eltena.core.application.skill.SkillSystem;
import com.eltena.core.application.title.TitleCatalog;
import com.eltena.core.application.title.TitleLoader;
import com.eltena.core.application.title.TitleSystem;
import com.eltena.core.application.ui.MenuProtectionListener;
import com.eltena.core.application.ui.MenuSystem;
import com.eltena.core.application.world.DiscoveryCatalog;
import com.eltena.core.application.world.DiscoveryService;
import com.eltena.core.application.world.WorldFirstService;
import com.eltena.core.application.world.WorldRankCatalog;
import com.eltena.core.application.world.WorldRankSystem;
import com.eltena.core.application.world.WorldUniqueService;
import com.eltena.core.i18n.MessageService;
import com.eltena.core.infrastructure.cache.PlayerProfileCache;
import com.eltena.core.infrastructure.cache.PlayerStatsCache;
import com.eltena.core.integrations.mod.ModIntegrationService;
import com.eltena.core.integrations.mod.ModIntegrationSettings;
import com.eltena.core.integrations.mod.ModPayloadRegistry;
import com.eltena.core.integrations.mod.ModPayloadSerializer;
import com.eltena.core.integrations.mod.ModRequestListener;
import com.eltena.core.integrations.mod.ModSyncPluginMessenger;
import com.eltena.core.integrations.placeholder.EltenaCorePlaceholderBridge;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ServiceRegistry {

    private final JavaPlugin plugin;
    private final Map<Class<?>, Object> services = new HashMap<>();

    public ServiceRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        services.put(type, instance);
    }

    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object instance = services.get(type);
        if (instance == null) {
            throw new IllegalStateException("Required service is not registered: " + type.getName());
        }
        return type.cast(instance);
    }

    public <T> T find(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object instance = services.get(type);
        return instance == null ? null : type.cast(instance);
    }

    public PlayerProfileRepository playerProfiles() {
        return require(PlayerProfileRepository.class);
    }

    public PlayerStatsRepository playerStats() {
        return require(PlayerStatsRepository.class);
    }

    public PlayerProfileCache profileCache() {
        return require(PlayerProfileCache.class);
    }

    public PlayerStatsCache statsCache() {
        return require(PlayerStatsCache.class);
    }

    public LevelSystem levelSystem() {
        return require(LevelSystem.class);
    }

    public MobKillExperienceService mobKillExperienceService() {
        return require(MobKillExperienceService.class);
    }

    public StatGrowthSystem statGrowthSystem() {
        return require(StatGrowthSystem.class);
    }

    public WeaponMasterySystem weaponMasterySystem() {
        return require(WeaponMasterySystem.class);
    }

    public GrowthSettings growthSettings() {
        return require(GrowthSettings.class);
    }

    public WeaponTypeResolver weaponTypeResolver() {
        return require(WeaponTypeResolver.class);
    }

    public EltenaCorePlaceholderBridge placeholderBridge() {
        return find(EltenaCorePlaceholderBridge.class);
    }

    public MessageService messages() {
        return require(MessageService.class);
    }

    public JobCatalog jobCatalog() {
        return require(JobCatalog.class);
    }

    public JobSystem jobSystem() {
        return require(JobSystem.class);
    }

    public TitleCatalog titleCatalog() {
        return require(TitleCatalog.class);
    }

    public TitleLoader titleLoader() {
        return require(TitleLoader.class);
    }

    public TitleSystem titleSystem() {
        return require(TitleSystem.class);
    }

    public MenuSystem menuSystem() {
        return require(MenuSystem.class);
    }

    public MenuProtectionListener menuProtectionListener() {
        return require(MenuProtectionListener.class);
    }

    public WorldRankCatalog worldRankCatalog() {
        return require(WorldRankCatalog.class);
    }

    public WorldRankSystem worldRankSystem() {
        return require(WorldRankSystem.class);
    }

    public WorldFirstService worldFirstService() {
        return require(WorldFirstService.class);
    }

    public WorldUniqueService worldUniqueService() {
        return require(WorldUniqueService.class);
    }

    public DiscoveryCatalog discoveryCatalog() {
        return require(DiscoveryCatalog.class);
    }

    public DiscoveryService discoveryService() {
        return require(DiscoveryService.class);
    }

    public SkillCatalog skillCatalog() {
        return require(SkillCatalog.class);
    }

    public SkillLoader skillLoader() {
        return require(SkillLoader.class);
    }

    public SkillSystem skillSystem() {
        return require(SkillSystem.class);
    }

    public EquipmentCatalog equipmentCatalog() {
        return require(EquipmentCatalog.class);
    }

    public EquipmentLoader equipmentLoader() {
        return require(EquipmentLoader.class);
    }

    public EquipmentSystem equipmentSystem() {
        return require(EquipmentSystem.class);
    }

    public MmoItemsDefinitionMappingRepository mmoItemsDefinitionMappingRepository() {
        return require(MmoItemsDefinitionMappingRepository.class);
    }

    public MmoItemsDefinitionStatsProvider mmoItemsDefinitionStatsProvider() {
        return require(MmoItemsDefinitionStatsProvider.class);
    }

    public MmoItemsEquipmentStatsProvider mmoItemsEquipmentStatsProvider() {
        return require(MmoItemsEquipmentStatsProvider.class);
    }

    public WeaponAttackSnapshotResolver weaponAttackSnapshotResolver() {
        return require(WeaponAttackSnapshotResolver.class);
    }

    public AbilityCatalog abilityCatalog() {
        return require(AbilityCatalog.class);
    }

    public AbilityLoader abilityLoader() {
        return require(AbilityLoader.class);
    }

    public AbilitySystem abilitySystem() {
        return require(AbilitySystem.class);
    }

    public FinalPlayerStatsCalculator finalPlayerStatsCalculator() {
        return require(FinalPlayerStatsCalculator.class);
    }

    public PlayerDerivedStatsService playerDerivedStatsService() {
        return require(PlayerDerivedStatsService.class);
    }

    public PlayerOptionSettingsService playerOptionSettingsService() {
        return require(PlayerOptionSettingsService.class);
    }

    public PlayerSettingsMenu playerSettingsMenu() {
        return require(PlayerSettingsMenu.class);
    }

    public PlayerSettingsMenuListener playerSettingsMenuListener() {
        return require(PlayerSettingsMenuListener.class);
    }

    public PlayerDamageFeedbackService playerDamageFeedbackService() {
        return require(PlayerDamageFeedbackService.class);
    }

    public PlayerManaService playerManaService() {
        return require(PlayerManaService.class);
    }

    public PlayerResetService playerResetService() {
        return require(PlayerResetService.class);
    }

    public PlayerManaRegenerationService playerManaRegenerationService() {
        return require(PlayerManaRegenerationService.class);
    }

    public DisplayNameResolver displayNameResolver() {
        return require(DisplayNameResolver.class);
    }

    public ModIntegrationSettings modIntegrationSettings() {
        return require(ModIntegrationSettings.class);
    }

    public ModPayloadRegistry modPayloadRegistry() {
        return require(ModPayloadRegistry.class);
    }

    public ModPayloadSerializer modPayloadSerializer() {
        return require(ModPayloadSerializer.class);
    }

    public ModIntegrationService modIntegrationService() {
        return require(ModIntegrationService.class);
    }

    public ModSyncPluginMessenger modSyncPluginMessenger() {
        return require(ModSyncPluginMessenger.class);
    }

    public ModRequestListener modRequestListener() {
        return require(ModRequestListener.class);
    }
}
