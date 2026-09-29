package com.eltena.core.integrations.placeholder;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class EltenaCorePlaceholderExpansion extends PlaceholderExpansion {

    private final ServiceRegistry services;

    public EltenaCorePlaceholderExpansion(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eltenacore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Eltena contributors";
    }

    @Override
    public @NotNull String getVersion() {
        return services.plugin().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(
                player.getUniqueId(),
                player.getName() == null ? player.getUniqueId().toString() : player.getName()
            );
            PlayerStats stats = services.playerStats().loadOrCreate(player.getUniqueId());
            String normalized = params.toLowerCase();

            return switch (normalized) {
                case "level" -> Integer.toString(profile.level());
                case "exp", "experience" -> Long.toString(profile.experience());
                case "hp" -> Integer.toString(stats.hp());
                case "mp" -> Integer.toString(stats.mp());
                case "attack" -> Integer.toString(stats.attack());
                case "defense" -> Integer.toString(stats.defense());
                case "skill_point", "skill-point" -> Integer.toString(services.skillSystem().availableSkillPoints(profile));
                case "job" -> profile.currentJobId();
                case "job_name" -> services.jobSystem().displayName(profile.currentJobId());
                case "title" -> profile.activeTitleId();
                case "title_name" -> services.titleSystem().displayName(profile.activeTitleId());
                case "rank", "world_rank" -> profile.worldRankId();
                case "world_rank_name" -> services.worldRankSystem().displayName(profile.worldRankId());
                default -> null;
            };
        } catch (IOException exception) {
            return "";
        }
    }
}
