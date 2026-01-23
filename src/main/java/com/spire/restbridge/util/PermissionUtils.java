package com.spire.restbridge.util;

/**
 * Utility class for permission conversions.
 */
public final class PermissionUtils {

    private PermissionUtils() {
        // Utility class
    }

    /**
     * Converts a Documentum permission level to its label.
     *
     * @param permit the permission level (1-7)
     * @return the permission label
     */
    public static String permitToLabel(int permit) {
        switch (permit) {
            case 1:
                return "NONE";
            case 2:
                return "BROWSE";
            case 3:
                return "READ";
            case 4:
                return "RELATE";
            case 5:
                return "VERSION";
            case 6:
                return "WRITE";
            case 7:
                return "DELETE";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Converts a Documentum permission label to its numeric level.
     * The REST API returns permission as a string label.
     *
     * @param label the permission label (case-insensitive)
     * @return the permission level (1-7), or -1 if unknown
     */
    public static int labelToPermit(String label) {
        if (label == null || label.isEmpty()) {
            return -1;
        }
        switch (label.toUpperCase()) {
            case "NONE":
                return 1;
            case "BROWSE":
                return 2;
            case "READ":
                return 3;
            case "RELATE":
                return 4;
            case "VERSION":
                return 5;
            case "WRITE":
                return 6;
            case "DELETE":
                return 7;
            default:
                return -1;
        }
    }
}
