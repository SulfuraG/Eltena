package com.eltena.core.application.title;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerStatBonuses;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.title.TitleDefinition;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class TitleSystem {

    private final ServiceRegistry services;
    private final TitleCatalog catalog;
    private final TitleLoader loader;

    public TitleSystem(ServiceRegistry services, TitleCatalog catalog, TitleLoader loader) {
        this.services = services;
        this.catalog = catalog;
        this.loader = loader;
    }

    public int reloadTitles() {
        return loader.loadInto(catalog);
    }

    public List<TitleDefinition> listTitles() {
        return catalog.list();
    }

    public TitleDefinition requireTitle(String titleId) {
        TitleDefinition definition = catalog.find(titleId);
        if (definition == null) {
            throw new IllegalArgumentException("対象称号が見つかりません。");
        }
        return definition;
    }

    public PlayerProfile unlockTitle(UUID playerId, String playerName, String titleId) throws IOException {
        TitleDefinition definition = requireTitle(titleId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerProfile updated = profile.withUnlockedTitle(definition.id());
        services.playerProfiles().save(updated);
        return updated;
    }

    public PlayerProfile setActiveTitle(UUID playerId, String playerName, String titleId) throws IOException {
        TitleDefinition definition = requireTitle(titleId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        if (!profile.unlockedTitles().contains(definition.id())) {
            throw new IllegalArgumentException("その称号はまだ解放されていません。");
        }
        PlayerProfile updated = profile.withActiveTitle(definition.id());
        services.playerProfiles().save(updated);
        return updated;
    }

    public PlayerProfile grantTitleReward(PlayerProfile profile, String titleId) {
        if (titleId == null || titleId.isBlank()) {
            return profile;
        }

        TitleDefinition definition = catalog.find(titleId);
        if (definition == null) {
            services.plugin().getLogger().warning("称号報酬 " + titleId + " は titles.yml に存在しないためスキップしました。");
            return profile;
        }

        PlayerProfile updated = profile.withUnlockedTitle(definition.id());
        if ("none".equalsIgnoreCase(updated.activeTitleId())) {
            updated = updated.withActiveTitle(definition.id());
        }
        return updated;
    }

    public PlayerStatBonuses resolveStatBonuses(PlayerProfile profile) {
        if (profile == null || profile.activeTitleId() == null || profile.activeTitleId().isBlank()) {
            return PlayerStatBonuses.none();
        }
        TitleDefinition definition = catalog.find(profile.activeTitleId());
        if (definition == null) {
            return PlayerStatBonuses.none();
        }
        PlayerStatBonuses bonuses = PlayerStatBonuses.none();
        for (var entry : definition.effect("stats").entrySet()) {
            java.util.Map<String, Object> statConfig = asMap(entry.getValue());
            double value = asDouble(statConfig.get("value")) + asDouble(statConfig.get("flat"));
            bonuses = bonuses.add(PlayerStatBonuses.single(entry.getKey(), value));
        }
        return bonuses;
    }

    public String displayName(String titleId) {
        TitleDefinition definition = catalog.find(titleId);
        return definition == null ? titleId : definition.displayName();
    }

    private java.util.Map<String, Object> asMap(Object value) {
        if (!(value instanceof java.util.Map<?, ?> map)) {
            return java.util.Map.of();
        }
        java.util.LinkedHashMap<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }
}
