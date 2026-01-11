package com.zkmigration.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DiffGenerator {

    public static String generateDiff(byte[] oldValue, byte[] newValue) {
        if (oldValue == null) oldValue = new byte[0];
        if (newValue == null) newValue = new byte[0];

        if (Arrays.equals(oldValue, newValue)) {
            return "";
        }

        boolean oldIsBinary = isBinary(oldValue);
        boolean newIsBinary = isBinary(newValue);

        if (oldIsBinary || newIsBinary) {
            return "Binary data differs (cannot show text diff)";
        }

        String oldStr = new String(oldValue, StandardCharsets.UTF_8);
        String newStr = new String(newValue, StandardCharsets.UTF_8);

        return generateTextDiff(oldStr, newStr);
    }

    private static boolean isBinary(byte[] data) {
        if (data == null || data.length == 0) return false;
        // Simple heuristic: look for null bytes or high ratio of non-printable chars
        // We'll just check for null bytes for now as ZK often stores config data
        int nullCount = 0;
        int checkLen = Math.min(data.length, 1024);
        for (int i = 0; i < checkLen; i++) {
            if (data[i] == 0) return true;
        }
        return false;
    }

    private static String generateTextDiff(String oldText, String newText) {
        List<String> oldLines = oldText.isEmpty() ? Collections.emptyList() : Arrays.asList(oldText.split("\\r?\\n"));
        List<String> newLines = newText.isEmpty() ? Collections.emptyList() : Arrays.asList(newText.split("\\r?\\n"));

        StringBuilder diff = new StringBuilder();

        // Very naive line-by-line diff
        int i = 0, j = 0;
        while (i < oldLines.size() || j < newLines.size()) {
            if (i < oldLines.size() && j < newLines.size() && oldLines.get(i).equals(newLines.get(j))) {
                // Identical lines, context?
                // For brevity, maybe skip or show as context if strictly required.
                // User asked for "specific row/words change".
                // Let's just output changed lines for now.
                i++;
                j++;
            } else {
                if (i < oldLines.size()) {
                    diff.append("- ").append(oldLines.get(i)).append("\n");
                    i++;
                }
                if (j < newLines.size()) {
                    diff.append("+ ").append(newLines.get(j)).append("\n");
                    j++;
                }
            }
        }
        return diff.toString().trim();
    }
}
