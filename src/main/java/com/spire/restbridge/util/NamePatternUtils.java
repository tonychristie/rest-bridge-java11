package com.spire.restbridge.util;

import java.util.regex.Pattern;

/**
 * Name patterns where * matches any run of characters and every other character matches itself.
 */
public final class NamePatternUtils {

    private NamePatternUtils() {
        // Utility class
    }

    /**
     * Builds the REST Services filter that narrows a listing to the names a pattern can match.
     *
     * @param attribute the attribute the filter tests
     * @param pattern the name pattern
     * @return an exact match without *, a starts-with on the text before the first *,
     *         or null when the pattern starts with *
     */
    public static String toFilter(String attribute, String pattern) {
        int wildcard = pattern.indexOf('*');
        if (wildcard < 0) {
            return attribute + "='" + quoteLiteral(pattern) + "'";
        }
        if (wildcard == 0) {
            return null;
        }
        return "starts-with(" + attribute + ",'" + quoteLiteral(pattern.substring(0, wildcard)) + "')";
    }

    /**
     * Tests whether a name fully matches a pattern, case-sensitively.
     *
     * @param pattern the name pattern
     * @param name the name to test
     * @return true when the whole name matches
     */
    public static boolean matches(String pattern, String name) {
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            if (!parts[i].isEmpty()) {
                regex.append(Pattern.quote(parts[i]));
            }
        }
        return Pattern.matches(regex.toString(), name);
    }

    private static String quoteLiteral(String text) {
        return text.replace("'", "''");
    }
}
