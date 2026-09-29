package com.eltena.core.application.display;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.ability.AbilityDefinition;
import com.eltena.core.domain.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DisplayNameResolver {

    private final ServiceRegistry services;

    public DisplayNameResolver(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public String skillName(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "未設定";
        }
        SkillDefinition definition = services.skillCatalog().find(skillId);
        return definition != null ? definition.displayName() : prettify(skillId);
    }

    public String abilityName(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return "未設定";
        }
        AbilityDefinition definition = services.abilityCatalog().find(abilityId);
        return definition != null ? definition.displayName() : prettify(abilityId);
    }

    public String tagName(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return "未設定";
        }
        return prettify(tagId);
    }

    public String formatSkillList(Collection<String> ids) {
        return formatNamedList(ids, this::skillName);
    }

    public String formatAbilityList(Collection<String> ids) {
        return formatNamedList(ids, this::abilityName);
    }

    public String formatTagList(Collection<String> tags) {
        return formatNamedList(tags, this::tagName);
    }

    public String formatBulletList(String heading, Collection<String> ids, java.util.function.Function<String, String> resolver) {
        if (ids == null || ids.isEmpty()) {
            return heading + " なし";
        }
        List<String> entries = new ArrayList<>();
        for (String id : ids) {
            entries.add("・" + resolver.apply(id));
        }
        return heading + " " + String.join(" ", entries);
    }

    private String formatNamedList(Collection<String> ids, java.util.function.Function<String, String> resolver) {
        if (ids == null || ids.isEmpty()) {
            return "なし";
        }
        List<String> resolved = new ArrayList<>();
        for (String id : ids) {
            resolved.add(resolver.apply(id));
        }
        return String.join(" / ", resolved);
    }

    private String prettify(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            return "未設定";
        }
        String[] tokens = normalized.replace('.', '_').replace('-', '_').split("_+");
        List<String> words = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            words.add(capitalize(token.toLowerCase(Locale.ROOT)));
        }
        return words.isEmpty() ? normalized : String.join(" ", words);
    }

    private String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
