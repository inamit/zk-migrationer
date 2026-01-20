package com.zkmigration.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableSubstitutor {
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    public static String replace(String input, Map<String, String> variables) {
        if (input == null || variables == null || variables.isEmpty()) {
            return input;
        }

        Matcher matcher = PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                // If variable not found, leave as is or throw?
                // Standard behavior is often to leave it or replace with empty.
                // Given the context of migration paths, leaving it might be safer to detect errors,
                // but usually unresolved vars are bugs.
                // For now, I'll leave it as is if not found, to match standard shell expansion behavior (sometimes).
                // Actually, if I can't resolve ${env}, it's likely a problem.
                // But let's stick to simple replacement. If not found, keep original token.
                matcher.appendReplacement(sb, Matcher.quoteReplacement("${" + key + "}"));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
