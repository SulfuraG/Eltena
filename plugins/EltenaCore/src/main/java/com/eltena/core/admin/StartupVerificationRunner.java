package com.eltena.core.admin;

import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

public final class StartupVerificationRunner {

    private final ServiceRegistry services;

    public StartupVerificationRunner(ServiceRegistry services) {
        this.services = services;
    }

    public void scheduleIfEnabled() {
        if (!services.plugin().getConfig().getBoolean("verification.startup-self-test.enabled", true)) {
            return;
        }

        String targetPlayer = services.plugin().getConfig().getString("verification.startup-self-test.target-player-name", "EltenaCoreTest");
        Bukkit.getScheduler().runTaskLater(services.plugin(), () -> runChecks(targetPlayer), 40L);
    }

    private void runChecks(String targetPlayer) {
        ConsoleCommandSender console = quietConsole();
        services.plugin().getLogger().info("[EltenaCore] startup self-test begin: " + targetPlayer);

        List<String> commands = List.of(
            "plugins",
            "eltenacore reload",
            "eltenacore debug growth",
            "eltenacore mod reload",
            "eltenacore mod debug",
            "eltenacore mod status",
            "eltenacore menu " + targetPlayer,
            "EltenaCore settings " + targetPlayer,
            "eltenacore settings " + targetPlayer,
            "ec settings " + targetPlayer,
            "eltenacore status " + targetPlayer,
            "eltenacore jobs " + targetPlayer,
            "eltenacore titles " + targetPlayer,
            "eltenacore rank status " + targetPlayer,
            "eltenacore profile " + targetPlayer,
            "eltenacore stats " + targetPlayer
        );

        for (String command : commands) {
            services.plugin().getLogger().info("[EltenaCore] startup self-test command: " + command);
            boolean result = Bukkit.dispatchCommand(console, command);
            services.plugin().getLogger().info("[EltenaCore] startup self-test result: " + command + " -> " + result);
        }

        try {
            for (String message : new MenuProtectionVerifier(services).verify(targetPlayer)) {
                services.plugin().getLogger().info(message);
            }
        } catch (IOException exception) {
            services.plugin().getLogger().warning("[EltenaCore] GUI verification read failed: " + exception.getMessage());
        } catch (IllegalStateException exception) {
            services.plugin().getLogger().severe("[EltenaCore] GUI verification failed: " + exception.getMessage());
        }

        probeMmoItem("SWORD", "ELTENA_VANILLA_TEST");
        probeMmoItem("ARMOR", "ELTENA_DEFENSE_TEST");
        probeMmoItem("SHIELD", "ELTENA_SHIELD_ATTRIBUTE_TEST");
        probeTypeIcon("SHIELD");

        services.plugin().getLogger().info("[EltenaCore] startup self-test done");
    }

    private void probeMmoItem(String typeId, String itemId) {
        var mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null || !mmoItems.isEnabled()) {
            services.plugin().getLogger().warning("[EltenaCore] MMOItems startup probe skipped: plugin unavailable");
            return;
        }
        try {
            ItemStack built = (ItemStack) mmoItems.getClass()
                .getMethod("getItem", String.class, String.class)
                .invoke(mmoItems, typeId, itemId);
            if (built == null || built.getType().isAir()) {
                services.plugin().getLogger().warning("[EltenaCore] MMOItems startup probe empty: type=" + typeId + " id=" + itemId);
                return;
            }
            var bonuses = services.mmoItemsEquipmentStatsProvider().resolve(built);
            services.plugin().getLogger().info(
                "[EltenaCore] MMOItems startup probe: type=" + typeId
                    + " id=" + itemId
                    + " materialId=" + services.mmoItemsEquipmentStatsProvider().resolveMaterialId(built)
                    + " itemType=" + safeText(services.mmoItemsEquipmentStatsProvider().resolveItemType(built))
                    + " itemIdTag=" + safeText(services.mmoItemsEquipmentStatsProvider().resolveItemId(built))
                    + " attackDamage=" + bonuses.weaponDamage()
                    + " defense=" + bonuses.defense()
                    + " maxHealth=" + bonuses.maxHealthBonus()
                    + " bukkitMaterial=" + built.getType().getKey()
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] MMOItems startup probe failed: type=" + typeId
                    + " id=" + itemId
                    + " reason=" + exception.getClass().getSimpleName()
                    + " message=" + safeText(exception.getMessage())
            );
        }
    }

    private void probeTypeIcon(String typeId) {
        var mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null || !mmoItems.isEnabled()) {
            return;
        }
        try {
            Object types = mmoItems.getClass().getMethod("getTypes").invoke(mmoItems);
            Object type = types.getClass().getMethod("get", String.class).invoke(types, typeId);
            if (type == null) {
                services.plugin().getLogger().warning("[EltenaCore] MMOItems type icon probe missing type: " + typeId);
                return;
            }
            ItemStack icon = (ItemStack) type.getClass().getMethod("getItem").invoke(type);
            if (icon == null) {
                services.plugin().getLogger().warning("[EltenaCore] MMOItems type icon probe null icon: " + typeId);
                return;
            }
            services.plugin().getLogger().info(
                "[EltenaCore] MMOItems type icon probe: type=" + typeId
                    + " material=" + icon.getType().getKey()
                    + " amount=" + icon.getAmount()
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] MMOItems type icon probe failed: type=" + typeId
                    + " reason=" + exception.getClass().getSimpleName()
                    + " message=" + safeText(exception.getMessage())
            );
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private ConsoleCommandSender quietConsole() {
        ConsoleCommandSender delegate = Bukkit.getConsoleSender();
        return (ConsoleCommandSender) Proxy.newProxyInstance(
            ConsoleCommandSender.class.getClassLoader(),
            new Class[]{ConsoleCommandSender.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("sendMessage".equals(name)) {
                    return null;
                }
                if ("getName".equals(name)) {
                    return "EltenaCoreVerificationConsole";
                }
                if ("getServer".equals(name)) {
                    return Bukkit.getServer();
                }
                if ("spigot".equals(name)) {
                    return delegate.spigot();
                }
                if ("name".equals(name)) {
                    return delegate.name();
                }
                try {
                    return method.invoke(delegate, args);
                } catch (ReflectiveOperationException exception) {
                    throw exception.getCause() == null ? exception : exception.getCause();
                }
            }
        );
    }
}
