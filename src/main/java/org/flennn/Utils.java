package org.flennn;

public final class Utils {
    private Utils() {
    }

    public static String limit(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String escapeMarkdown(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("```", "'''").replace("`", "'");
    }
}
