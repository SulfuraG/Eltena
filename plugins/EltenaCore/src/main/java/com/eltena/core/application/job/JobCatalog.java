package com.eltena.core.application.job;

import com.eltena.core.domain.job.JobDefinition;
import com.eltena.core.domain.job.JobTier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobCatalog {

    private final Map<String, JobDefinition> jobs = new LinkedHashMap<>();

    public JobCatalog() {
        register(new JobDefinition("novice", "見習い", "まだ道を選び切っていない初歩のジョブ。", JobTier.BASIC));
        register(new JobDefinition("swordsman", "剣士", "近接戦闘を主軸とする武技の担い手。", JobTier.BASIC));
        register(new JobDefinition("mage", "魔導師", "魔力操作と遠距離火力を得意とする術者。", JobTier.BASIC));
        register(new JobDefinition("priest", "聖職者", "加護と支援を司る祈りの使い手。", JobTier.BASIC));
        register(new JobDefinition("thief", "盗賊", "機動力と奇襲に優れた潜行者。", JobTier.BASIC));
        register(new JobDefinition("hunter", "狩人", "探索と索敵に長けた追跡者。", JobTier.BASIC));
    }

    public JobDefinition find(String jobId) {
        return jobs.get(jobId);
    }

    public List<JobDefinition> list() {
        return List.copyOf(jobs.values());
    }

    private void register(JobDefinition definition) {
        jobs.put(definition.id(), definition);
    }
}
