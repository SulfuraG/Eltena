package com.eltena.core.integrations.mobhp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EltenaMobHpBridge {

    private static final String PLUGIN_NAME = "EltenaMobHP";
    private static final String API_CLASS_NAME = "com.eltena.mobhp.api.EltenaMobHpApi";
    private static final Pattern FALLBACK_SUFFIX_PATTERN = Pattern.compile("\\s*[❤♥]\\s*[0-9,]+$");

    private final Logger logger;

    private ClassLoader cachedLoader;
    private Method cachedStripMethod;
    private boolean stripFailureLogged;

    public EltenaMobHpBridge(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public String stripHpSuffix(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }

        Method stripMethod = resolveStripMethod();
        if (stripMethod != null) {
            try {
                Object value = stripMethod.invoke(null, displayName);
                if (value instanceof String stripped) {
                    return stripped;
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                cachedLoader = null;
                cachedStripMethod = null;
                if (!stripFailureLogged) {
                    logger.warning("[EltenaCore] EltenaMobHP API 呼び出しに失敗したため正規表現 fallback を使用します: " + exception.getMessage());
                    stripFailureLogged = true;
                }
            }
        }

        return fallbackStripHpSuffix(displayName);
    }

    private Method resolveStripMethod() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        ClassLoader loader = plugin.getClass().getClassLoader();
        if (cachedStripMethod != null && cachedLoader == loader) {
            return cachedStripMethod;
        }

        try {
            Class<?> apiClass = loader.loadClass(API_CLASS_NAME);
            Method stripMethod = apiClass.getMethod("stripHpSuffix", String.class);
            cachedLoader = loader;
            cachedStripMethod = stripMethod;
            stripFailureLogged = false;
            return stripMethod;
        } catch (ReflectiveOperationException exception) {
            if (!stripFailureLogged) {
                logger.warning("[EltenaCore] EltenaMobHP API を解決できなかったため正規表現 fallback を使用します: " + exception.getMessage());
                stripFailureLogged = true;
            }
            return null;
        }
    }

    private String fallbackStripHpSuffix(String displayName) {
        LegacyTextMapping mapping = LegacyTextMapping.from(displayName);
        if (mapping.plainText().isEmpty()) {
            return displayName;
        }

        Matcher matcher = FALLBACK_SUFFIX_PATTERN.matcher(mapping.plainText());
        int start = -1;
        int end = -1;
        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
        }
        if (start < 0 || end != mapping.plainText().length()) {
            return displayName;
        }
        return displayName.substring(0, mapping.rawIndexAt(start));
    }

    private record LegacyTextMapping(
        String plainText,
        int[] rawIndexes
    ) {
        private static LegacyTextMapping from(String rawText) {
            StringBuilder plain = new StringBuilder();
            List<Integer> rawIndexes = new ArrayList<>();
            int index = 0;
            while (index < rawText.length()) {
                char current = rawText.charAt(index);
                if (isLegacyCodePrefix(current) && index + 1 < rawText.length() && isLegacyCode(rawText.charAt(index + 1))) {
                    index += 2;
                    continue;
                }
                plain.append(current);
                rawIndexes.add(index);
                index++;
            }
            int[] rawIndexArray = new int[rawIndexes.size()];
            for (int i = 0; i < rawIndexes.size(); i++) {
                rawIndexArray[i] = rawIndexes.get(i);
            }
            return new LegacyTextMapping(plain.toString(), rawIndexArray);
        }

        private int rawIndexAt(int plainIndex) {
            if (plainIndex <= 0) {
                return 0;
            }
            if (plainIndex >= rawIndexes.length) {
                return rawIndexes.length == 0 ? 0 : rawIndexes[rawIndexes.length - 1] + 1;
            }
            return rawIndexes[plainIndex];
        }

        private static boolean isLegacyCodePrefix(char character) {
            return character == ChatColor.COLOR_CHAR || character == '&';
        }

        private static boolean isLegacyCode(char character) {
            char lower = Character.toLowerCase(character);
            return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r'
                || lower == 'x';
        }
    }
}
