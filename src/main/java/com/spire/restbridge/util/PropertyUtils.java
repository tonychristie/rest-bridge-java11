package com.spire.restbridge.util;

import java.util.Map;

/**
 * Utility class for reading REST Services properties.
 */
public final class PropertyUtils {

    private PropertyUtils() {
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
