package com.eltena.core.application.job;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.job.JobDefinition;
import com.eltena.core.domain.player.PlayerProfile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class JobSystem {

    private final ServiceRegistry services;
    private final JobCatalog catalog;

    public JobSystem(ServiceRegistry services, JobCatalog catalog) {
        this.services = services;
        this.catalog = catalog;
    }

    public List<JobDefinition> listJobs() {
        return catalog.list();
    }

    public JobDefinition requireJob(String jobId) {
        JobDefinition definition = catalog.find(jobId);
        if (definition == null) {
            throw new IllegalArgumentException("対象ジョブが見つかりません。");
        }
        return definition;
    }

    public PlayerProfile unlockJob(UUID playerId, String playerName, String jobId) throws IOException {
        JobDefinition definition = requireJob(jobId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerProfile updated = profile.withUnlockedJob(definition.id());
        services.playerProfiles().save(updated);
        return updated;
    }

    public PlayerProfile setCurrentJob(UUID playerId, String playerName, String jobId) throws IOException {
        JobDefinition definition = requireJob(jobId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        if (!profile.unlockedJobs().contains(definition.id())) {
            throw new IllegalArgumentException("そのジョブはまだ解放されていません。");
        }
        PlayerProfile updated = profile.withCurrentJob(definition.id());
        services.playerProfiles().save(updated);
        return updated;
    }

    public String displayName(String jobId) {
        JobDefinition definition = catalog.find(jobId);
        return definition == null ? jobId : definition.displayName();
    }
}
