package com.eltena.core.bootstrap;

import com.eltena.core.admin.StartupVerificationRunner;
import com.eltena.core.api.player.PlayerProfileRepository;
import com.eltena.core.api.stats.PlayerStatsRepository;
import com.eltena.core.application.ability.AbilityCatalog;
import com.eltena.core.application.ability.AbilityLoader;
import com.eltena.core.application.ability.AbilitySystem;
import com.eltena.core.application.command.EltenaCoreCommand;
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
import com.eltena.core.application.listener.CombatDamageListener;
import com.eltena.core.application.listener.CombatGrowthListener;
import com.eltena.core.application.listener.MobDropControlListener;
import com.eltena.core.application.listener.ModSyncJoinListener;
import com.eltena.core.application.listener.PlayerDerivedStatsListener;
import com.eltena.core.application.listener.ServerRuleListener;
import com.eltena.core.application.listener.VanillaExperienceGuardListener;
import com.eltena.core.application.player.FinalPlayerStatsCalculator;
import com.eltena.core.application.player.PlayerDerivedStatsService;
import com.eltena.core.application.player.PlayerManaRegenerationService;
import com.eltena.core.application.player.PlayerManaService;
import com.eltena.core.application.player.PlayerResetService;
import com.eltena.core.application.progression.LevelSystem;
import com.eltena.core.application.progression.MobKillExperienceService;
import com.eltena.core.application.progression.MythicMobDropInspector;
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
import com.eltena.core.infrastructure.cache.PlayerProfileCache;
import com.eltena.core.infrastructure.cache.PlayerStatsCache;
import com.eltena.core.infrastructure.persistence.player.YamlPlayerProfileRepository;
import com.eltena.core.infrastructure.persistence.stats.YamlPlayerStatsRepository;
import com.eltena.core.i18n.MessageService;
import com.eltena.core.integrations.IntegrationCatalog;
import com.eltena.core.integrations.mod.ModIntegrationService;
import com.eltena.core.integrations.mod.ModIntegrationSettings;
import com.eltena.core.integrations.mod.ModPayloadRegistry;
import com.eltena.core.integrations.mod.ModPayloadSerializer;
import com.eltena.core.integrations.mod.ModRequestListener;
import com.eltena.core.integrations.mod.ModSyncPluginMessenger;
import com.eltena.core.integrations.placeholder.EltenaCorePlaceholderBridge;
import com.eltena.core.resources.ResourceCatalog;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;

public final class ModuleBootstrap {

    private final ServiceRegistry services;
    private final List<BootstrapModule> modules;

    public ModuleBootstrap(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
        this.modules = List.of(
            new KernelModule(),
            new IntegrationModule(),
            new ListenerModule(),
            new CommandModule()
        );
    }

    public void load() {
        modules.forEach(module -> module.load(services));
    }

    public void enable() {
        modules.forEach(module -> module.enable(services));
    }

    public void disable() {
        for (int index = modules.size() - 1; index >= 0; index--) {
            modules.get(index).disable(services);
        }
    }

    private static final class KernelModule implements BootstrapModule {

        @Override
        public String name() {
            return "kernel";
        }

        @Override
        public void load(ServiceRegistry services) {
            services.plugin().saveDefaultConfig();
            services.plugin().getConfig().options().copyDefaults(true);
            services.plugin().saveConfig();

            PlayerProfileCache profileCache = new PlayerProfileCache();
            PlayerStatsCache statsCache = new PlayerStatsCache();
            MessageService messageService = new MessageService(services.plugin());
            messageService.reload();
            JobCatalog jobCatalog = new JobCatalog();
            TitleCatalog titleCatalog = new TitleCatalog();
            TitleLoader titleLoader = new TitleLoader(services.plugin(), messageService);
            GrowthSettings growthSettings = new GrowthSettings(services.plugin());
            WorldRankCatalog worldRankCatalog = new WorldRankCatalog();
            DiscoveryCatalog discoveryCatalog = new DiscoveryCatalog();
            SkillCatalog skillCatalog = new SkillCatalog();
            SkillLoader skillLoader = new SkillLoader(services.plugin());
            EquipmentCatalog equipmentCatalog = new EquipmentCatalog();
            EquipmentLoader equipmentLoader = new EquipmentLoader(services.plugin());
            MmoItemsDefinitionMappingRepository mmoItemsDefinitionMappingRepository =
                new MmoItemsDefinitionMappingRepository(services.plugin());
            AbilityCatalog abilityCatalog = new AbilityCatalog();
            AbilityLoader abilityLoader = new AbilityLoader(services.plugin());
            ModIntegrationSettings modIntegrationSettings = new ModIntegrationSettings(services.plugin());
            ModPayloadRegistry modPayloadRegistry = new ModPayloadRegistry();
            ModPayloadSerializer modPayloadSerializer = new ModPayloadSerializer();

            services.register(PlayerProfileCache.class, profileCache);
            services.register(PlayerStatsCache.class, statsCache);
            services.register(MessageService.class, messageService);
            services.register(PlayerProfileRepository.class, new YamlPlayerProfileRepository(services.plugin(), profileCache, growthSettings));
            services.register(PlayerStatsRepository.class, new YamlPlayerStatsRepository(services.plugin(), statsCache, growthSettings));
            services.register(LevelSystem.class, new LevelSystem(services));
            services.register(StatGrowthSystem.class, new StatGrowthSystem(services));
            services.register(WeaponMasterySystem.class, new WeaponMasterySystem(services));
            services.register(GrowthSettings.class, growthSettings);
            services.register(MobKillExperienceService.class, new MobKillExperienceService(services, growthSettings));
            services.register(MythicMobDropInspector.class, new MythicMobDropInspector(services));
            services.register(JobCatalog.class, jobCatalog);
            services.register(TitleCatalog.class, titleCatalog);
            services.register(TitleLoader.class, titleLoader);
            services.register(JobSystem.class, new JobSystem(services, jobCatalog));

            TitleSystem titleSystem = new TitleSystem(services, titleCatalog, titleLoader);
            titleSystem.reloadTitles();
            services.register(TitleSystem.class, titleSystem);

            services.register(WorldRankCatalog.class, worldRankCatalog);
            services.register(WorldRankSystem.class, new WorldRankSystem(services, worldRankCatalog));
            services.register(DiscoveryCatalog.class, discoveryCatalog);
            DiscoveryService discoveryService = new DiscoveryService(services, discoveryCatalog);
            services.register(DiscoveryService.class, discoveryService);
            WorldFirstService worldFirstService = new WorldFirstService(services);
            services.register(WorldFirstService.class, worldFirstService);
            WorldUniqueService worldUniqueService = new WorldUniqueService(services);
            services.register(WorldUniqueService.class, worldUniqueService);

            services.register(SkillCatalog.class, skillCatalog);
            services.register(SkillLoader.class, skillLoader);
            services.register(SkillSystem.class, new SkillSystem(services, skillCatalog, skillLoader));
            services.register(EquipmentCatalog.class, equipmentCatalog);
            services.register(EquipmentLoader.class, equipmentLoader);
            services.register(EquipmentSystem.class, new EquipmentSystem(equipmentCatalog, equipmentLoader));
            services.register(MmoItemsDefinitionMappingRepository.class, mmoItemsDefinitionMappingRepository);
            services.register(
                MmoItemsDefinitionStatsProvider.class,
                new MmoItemsDefinitionStatsProvider(services.plugin(), mmoItemsDefinitionMappingRepository)
            );
            MmoItemsEquipmentStatsProvider mmoItemsEquipmentStatsProvider = new MmoItemsEquipmentStatsProvider(services.plugin());
            services.register(MmoItemsEquipmentStatsProvider.class, mmoItemsEquipmentStatsProvider);
            services.register(WeaponTypeResolver.class, new WeaponTypeResolver(mmoItemsEquipmentStatsProvider));
            services.register(WeaponAttackSnapshotResolver.class, new WeaponAttackSnapshotResolver(services));
            services.register(AbilityCatalog.class, abilityCatalog);
            services.register(AbilityLoader.class, abilityLoader);
            services.register(PlayerManaService.class, new PlayerManaService(services));
            services.register(PlayerManaRegenerationService.class, new PlayerManaRegenerationService(services));
            services.register(AbilitySystem.class, new AbilitySystem(services, abilityCatalog, abilityLoader));
            services.register(FinalPlayerStatsCalculator.class, new FinalPlayerStatsCalculator(services));
            services.register(PlayerDerivedStatsService.class, new PlayerDerivedStatsService(services));
            services.register(PlayerResetService.class, new PlayerResetService(services));
            PlayerOptionSettingsService playerOptionSettingsService = new PlayerOptionSettingsService(services.plugin(), messageService);
            services.register(PlayerOptionSettingsService.class, playerOptionSettingsService);
            PlayerSettingsMenu playerSettingsMenu = new PlayerSettingsMenu(services);
            services.register(PlayerSettingsMenu.class, playerSettingsMenu);
            services.register(PlayerSettingsMenuListener.class, new PlayerSettingsMenuListener(services, playerSettingsMenu));
            services.register(PlayerDamageFeedbackService.class, new PlayerDamageFeedbackService(services));
            services.register(DisplayNameResolver.class, new DisplayNameResolver(services));
            services.register(ModIntegrationSettings.class, modIntegrationSettings);
            services.register(ModPayloadRegistry.class, modPayloadRegistry);
            services.register(ModPayloadSerializer.class, modPayloadSerializer);
            ModIntegrationService modIntegrationService = new ModIntegrationService(services, modIntegrationSettings, modPayloadRegistry, modPayloadSerializer);
            services.register(ModIntegrationService.class, modIntegrationService);
            ModSyncPluginMessenger modSyncPluginMessenger = new ModSyncPluginMessenger(services.plugin(), modIntegrationService);
            services.register(ModSyncPluginMessenger.class, modSyncPluginMessenger);
            services.register(ModRequestListener.class, new ModRequestListener(services, modSyncPluginMessenger));

            MenuSystem menuSystem = new MenuSystem(services, services.require(JobSystem.class), titleSystem);
            services.register(MenuSystem.class, menuSystem);
            services.register(MenuProtectionListener.class, new MenuProtectionListener(menuSystem));
            services.register(StartupVerificationRunner.class, new StartupVerificationRunner(services));
            services.register(ResourceCatalog.class, new ResourceCatalog());

            worldFirstService.reload();
            worldUniqueService.reload();
            discoveryService.reload();
            services.skillSystem().reload();
            services.equipmentSystem().reload();
            services.mmoItemsDefinitionMappingRepository().reload();
            services.abilitySystem().reload();
        }
    }

    private static final class IntegrationModule implements BootstrapModule {

        @Override
        public String name() {
            return "integrations";
        }

        @Override
        public void load(ServiceRegistry services) {
            services.register(IntegrationCatalog.class, IntegrationCatalog.detect(services.plugin().getServer().getPluginManager()));
            services.register(EltenaCorePlaceholderBridge.class, new EltenaCorePlaceholderBridge(services));
        }

        @Override
        public void enable(ServiceRegistry services) {
            services.modSyncPluginMessenger().registerChannels(services.modRequestListener());
            services.require(IntegrationCatalog.class).refresh(services.plugin().getServer().getPluginManager());
            services.plugin().getLogger().info(
                "[EltenaCore] MMOItems definition provider: " + services.mmoItemsDefinitionStatsProvider().statusLine()
            );
            services.plugin().getLogger().info(
                "[EltenaCore] MMOItems equipment provider: " + services.mmoItemsEquipmentStatsProvider().statusLine()
            );
            int migratedPlayers = services.playerManaService().migrateAllPlayers();
            services.plugin().getLogger().info(
                "[EltenaCore] MP migration completed: " + migratedPlayers + " player(s)"
            );
            services.playerManaRegenerationService().start();
            EltenaCorePlaceholderBridge bridge = services.placeholderBridge();
            if (bridge != null) {
                bridge.registerIfAvailable();
            }
            services.plugin().getServer().getScheduler().runTask(services.plugin(), services.abilitySystem()::logMythicAvailabilityOnStartup);
        }

        @Override
        public void disable(ServiceRegistry services) {
            services.playerManaRegenerationService().stop();
            services.modSyncPluginMessenger().unregisterChannels();
            EltenaCorePlaceholderBridge bridge = services.placeholderBridge();
            if (bridge != null) {
                bridge.unregisterIfRegistered();
            }
        }
    }

    private static final class ListenerModule implements BootstrapModule {

        @Override
        public String name() {
            return "listeners";
        }

        @Override
        public void enable(ServiceRegistry services) {
            register(services, new CombatDamageListener(services, services.growthSettings()));
            register(services, new CombatGrowthListener(services, services.growthSettings()));
            register(services, new VanillaExperienceGuardListener(services, services.growthSettings()));
            register(services, new ServerRuleListener(services, services.growthSettings()));
            register(services, new MobDropControlListener(services.require(MythicMobDropInspector.class)));
            register(services, new ModSyncJoinListener(services.plugin(), services.modSyncPluginMessenger()));
            register(services, new PlayerDerivedStatsListener(services, services.playerDerivedStatsService()));
            register(services, services.menuProtectionListener());
            register(services, services.playerSettingsMenuListener());
            services.plugin().getLogger().info(services.messages().get("plugin.listeners-registered"));
        }

        private void register(ServiceRegistry services, Listener listener) {
            services.plugin().getServer().getPluginManager().registerEvents(listener, services.plugin());
        }
    }

    private static final class CommandModule implements BootstrapModule {

        @Override
        public String name() {
            return "commands";
        }

        @Override
        public void enable(ServiceRegistry services) {
            PluginCommand command = services.plugin().getCommand("eltenacore");
            if (command == null) {
                throw new IllegalStateException("Command 'eltenacore' is missing from plugin.yml");
            }

            EltenaCoreCommand executor = new EltenaCoreCommand(services);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            services.require(StartupVerificationRunner.class).scheduleIfEnabled();
        }
    }
}
