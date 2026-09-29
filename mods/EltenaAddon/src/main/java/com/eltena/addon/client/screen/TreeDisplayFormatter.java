package com.eltena.addon.client.screen;

import com.eltena.addon.client.ui.AddonUiFont;
import com.eltena.addon.network.model.SkillNodePayload;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class TreeDisplayFormatter {
    private TreeDisplayFormatter() {
    }

    static String formatSkillDescription(SkillNodePayload payload, String fallbackDescription) {
        if (payload != null && payload.description() != null && !payload.description().isEmpty()) {
            return payload.description().stream()
                .map(TreeDisplayFormatter::sanitizeRewardDisplay)
                .collect(Collectors.joining(" "));
        }
        if (fallbackDescription != null && !fallbackDescription.isBlank()) {
            return sanitizeRewardDisplay(fallbackDescription);
        }
        if (payload != null && payload.category() != null && !payload.category().isBlank()) {
            return AddonUiFont.translatable(
                "screen.eltenaaddon.skill_tree.description.fallback",
                localizeToken(payload.category())
            ).getString();
        }
        return AddonUiFont.translatable("screen.eltenaaddon.skill_tree.description.none").getString();
    }

    static String formatSkillEffects(List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return AddonUiFont.translatable("screen.eltenaaddon.skill_tree.effects.none").getString();
        }
        return effects.stream()
            .map(TreeDisplayFormatter::sanitizeRewardDisplay)
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(" / "));
    }

    static String formatRequirementValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return sanitizeRewardDisplay(raw);
    }

    static String sanitizeRewardDisplay(String raw) {
        if (raw == null || raw.isBlank()) {
            return AddonUiFont.translatable("screen.eltenaaddon.skill_tree.effects.none").getString();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("knowledge:{") && trimmed.endsWith("}")) {
            String body = trimmed.substring("knowledge:{".length(), trimmed.length() - 1);
            return formatKnowledgeReward(parseIntMap(body));
        }
        return localizeInlineTokens(trimmed);
    }

    static String localizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw.trim().toLowerCase()) {
            case "fire" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.fire").getString();
            case "water" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.water").getString();
            case "wind" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.wind").getString();
            case "earth" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.earth").getString();
            case "holy" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.holy").getString();
            case "forbidden" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.forbidden").getString();
            case "enhancement" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.enhancement").getString();
            case "knowledge" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.knowledge").getString();
            case "elemental" -> AddonUiFont.translatable("screen.eltenaaddon.tree.token.elemental").getString();
            default -> raw;
        };
    }

    private static String formatKnowledgeReward(Map<String, Integer> rewardMap) {
        return rewardMap.entrySet().stream()
            .map(entry -> localizeToken(entry.getKey()) + " +" + entry.getValue())
            .collect(Collectors.joining(" / "));
    }

    private static String localizeInlineTokens(String raw) {
        return raw
            .replace("knowledge", localizeToken("knowledge"))
            .replace("fire", localizeToken("fire"))
            .replace("water", localizeToken("water"))
            .replace("wind", localizeToken("wind"))
            .replace("earth", localizeToken("earth"))
            .replace("holy", localizeToken("holy"))
            .replace("forbidden", localizeToken("forbidden"))
            .replace("enhancement", localizeToken("enhancement"));
    }

    private static Map<String, Integer> parseIntMap(String raw) {
        java.util.LinkedHashMap<String, Integer> values = new java.util.LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String[] entry = part.trim().split("=", 2);
            if (entry.length != 2) {
                continue;
            }
            try {
                values.put(entry[0].trim(), Integer.parseInt(entry[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }
}
