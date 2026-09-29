package com.eltena.addon.client.item;

import com.mojang.datafixers.util.Either;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.slf4j.Logger;

public final class CleanModTooltipSuppressor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DYNAMIC_LORE_KEY = "MMOITEMS_DYNAMIC_LORE";

    private CleanModTooltipSuppressor() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(CleanModTooltipSuppressor::onItemTooltip);
        eventBus.addListener(CleanModTooltipSuppressor::onGatherTooltipComponents);
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        boolean shouldSuppress = CleanModMaterialTags.shouldSuppressModTooltip(stack);
        List<Component> lines = event.getToolTip();
        int before = lines == null ? -1 : lines.size();
        if (CleanModMaterialTags.shouldLogCandidate(stack)) {
            LOGGER.info(
                "[EltenaAddon:clean-tooltip-debug] ItemTooltipEvent item='{}' registryId={} shouldSuppress={} customKeys={} cleanTag={} disableTag={} tooltipBefore={}",
                stack.getHoverName().getString(),
                CleanModMaterialTags.registryId(stack),
                shouldSuppress,
                CleanModMaterialTags.topLevelKeys(stack),
                CleanModMaterialTags.hasCleanTag(stack),
                CleanModMaterialTags.hasDisableModTooltipTag(stack),
                before
            );
        }

        if (!shouldSuppress) {
            return;
        }

        if (lines == null || lines.isEmpty()) {
            return;
        }

        List<Component> originalLines = new ArrayList<>(lines);
        Component title = originalLines.isEmpty() ? stack.getHoverName().copy() : originalLines.get(0).copy();
        List<String> desiredLore = readDynamicLoreLines(stack);

        lines.clear();
        lines.add(title);

        if (!desiredLore.isEmpty()) {
            reapplyDynamicLore(lines, originalLines, desiredLore);
        }

        trimDuplicateBlankLines(lines);

        if (CleanModMaterialTags.shouldLogCandidate(stack)) {
            List<String> remaining = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                remaining.add(index + ":" + normalizeForMatch(lines.get(index).getString()));
            }
            LOGGER.info(
                "[EltenaAddon:clean-tooltip-debug] ItemTooltipEvent item='{}' registryId={} tooltipAfter={} rebuiltFromDynamicLore={} remainingLines={}",
                stack.getHoverName().getString(),
                CleanModMaterialTags.registryId(stack),
                lines.size(),
                !desiredLore.isEmpty(),
                remaining
            );
        }
    }

    private static void reapplyDynamicLore(List<Component> targetLines, List<Component> originalLines, List<String> desiredLore) {
        Map<String, ArrayDeque<Component>> componentPool = new LinkedHashMap<>();
        for (int index = 1; index < originalLines.size(); index++) {
            Component line = originalLines.get(index);
            String key = normalizeVisibleText(line.getString());
            componentPool.computeIfAbsent(key, unused -> new ArrayDeque<>()).add(line.copy());
        }

        for (String rawLine : desiredLore) {
            String key = normalizeVisibleText(rawLine);
            Component matching = pollMatchingComponent(componentPool, key);
            if (matching != null) {
                targetLines.add(matching);
                continue;
            }
            if (key.isEmpty()) {
                targetLines.add(Component.empty());
                continue;
            }
            targetLines.add(Component.literal(stripLegacyFormatting(rawLine)));
        }
    }

    private static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        boolean shouldSuppress = CleanModMaterialTags.shouldSuppressModTooltip(stack);
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (CleanModMaterialTags.shouldLogCandidate(stack)) {
            LOGGER.info(
                "[EltenaAddon:clean-tooltip-debug] GatherComponents item='{}' registryId={} shouldSuppress={} before={}",
                stack.getHoverName().getString(),
                CleanModMaterialTags.registryId(stack),
                shouldSuppress,
                describeTooltipElements(elements)
            );
        }

        if (!shouldSuppress || elements == null || elements.isEmpty()) {
            return;
        }

        Map<String, Integer> allowedLineCounts = new HashMap<>();
        registerAllowedLine(allowedLineCounts, stack.getHoverName().getString());
        for (String rawLine : readDynamicLoreLines(stack)) {
            registerAllowedLine(allowedLineCounts, rawLine);
        }

        elements.removeIf(element -> !shouldKeepElement(element, allowedLineCounts));

        if (CleanModMaterialTags.shouldLogCandidate(stack)) {
            LOGGER.info(
                "[EltenaAddon:clean-tooltip-debug] GatherComponents item='{}' registryId={} after={}",
                stack.getHoverName().getString(),
                CleanModMaterialTags.registryId(stack),
                describeTooltipElements(elements)
            );
        }
    }

    private static boolean shouldKeepElement(Either<FormattedText, TooltipComponent> element, Map<String, Integer> allowedLineCounts) {
        if (element == null) {
            return false;
        }
        if (element.right().isPresent()) {
            return false;
        }
        FormattedText formattedText = element.left().orElse(null);
        if (formattedText == null) {
            return false;
        }
        String key = normalizeVisibleText(formattedText.getString());
        Integer remaining = allowedLineCounts.get(key);
        if (remaining == null || remaining <= 0) {
            return false;
        }
        allowedLineCounts.put(key, remaining - 1);
        return true;
    }

    private static void registerAllowedLine(Map<String, Integer> allowedLineCounts, String line) {
        String key = normalizeVisibleText(line);
        allowedLineCounts.merge(key, 1, Integer::sum);
    }

    private static String describeTooltipElements(List<Either<FormattedText, TooltipComponent>> elements) {
        List<String> descriptions = new ArrayList<>();
        if (elements == null) {
            return "<null>";
        }
        for (int index = 0; index < elements.size(); index++) {
            Either<FormattedText, TooltipComponent> element = elements.get(index);
            if (element == null) {
                descriptions.add(index + ":<null>");
                continue;
            }
            if (element.left().isPresent()) {
                descriptions.add(index + ":text=" + normalizeVisibleText(element.left().get().getString()));
                continue;
            }
            TooltipComponent component = element.right().orElse(null);
            descriptions.add(index + ":component=" + (component == null ? "<null>" : component.getClass().getName()));
        }
        return descriptions.toString();
    }

    private static Component pollMatchingComponent(Map<String, ArrayDeque<Component>> componentPool, String key) {
        ArrayDeque<Component> exact = componentPool.get(key);
        if (exact == null || exact.isEmpty()) {
            return null;
        }
        return exact.pollFirst();
    }

    private static List<String> readDynamicLoreLines(ItemStack stack) {
        CompoundTag tag = CleanModMaterialTags.readCustomData(stack);
        if (tag == null || !tag.contains(DYNAMIC_LORE_KEY)) {
            return List.of();
        }

        String raw = tag.getString(DYNAMIC_LORE_KEY);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
            List<String> lines = new ArrayList<>();
            for (JsonElement element : array) {
                lines.add(element.isJsonNull() ? "" : element.getAsString());
            }
            return lines;
        } catch (IllegalStateException | JsonSyntaxException exception) {
            LOGGER.warn(
                "[EltenaAddon:clean-tooltip-debug] Failed to parse MMOITEMS_DYNAMIC_LORE registryId={} message={}",
                CleanModMaterialTags.registryId(stack),
                exception.getMessage()
            );
            return List.of();
        }
    }

    private static String normalizeVisibleText(String input) {
        return normalizeForMatch(stripLegacyFormatting(input));
    }

    private static String normalizeForMatch(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String stripLegacyFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replaceAll("(?i)\u00A7[0-9A-FK-ORX]", "");
    }

    private static void trimDuplicateBlankLines(List<Component> lines) {
        boolean previousBlank = false;
        for (int index = lines.size() - 1; index >= 1; index--) {
            String text = normalizeForMatch(lines.get(index).getString());
            boolean blank = text.isEmpty();
            if (blank && previousBlank) {
                lines.remove(index);
                continue;
            }
            previousBlank = blank;
        }
        while (lines.size() > 1) {
            String tail = normalizeForMatch(lines.get(lines.size() - 1).getString());
            if (tail.isEmpty()) {
                lines.remove(lines.size() - 1);
                continue;
            }
            break;
        }
    }
}
