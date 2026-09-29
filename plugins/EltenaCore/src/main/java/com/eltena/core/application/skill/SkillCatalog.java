package com.eltena.core.application.skill;

import com.eltena.core.domain.skill.SkillDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillCatalog {

    private final Map<String, SkillDefinition> definitions = new LinkedHashMap<>();

    public synchronized void replaceAll(Map<String, SkillDefinition> updated) {
        definitions.clear();
        definitions.putAll(updated);
    }

    public synchronized SkillDefinition find(String id) {
        return definitions.get(id);
    }

    public synchronized List<SkillDefinition> list() {
        return List.copyOf(definitions.values());
    }
}
