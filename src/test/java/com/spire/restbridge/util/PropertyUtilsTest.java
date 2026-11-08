package com.spire.restbridge.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PropertyUtils.
 */
class PropertyUtilsTest {

    @Test
    void str_missingKey_returnsEmpty() {
        assertEquals("", PropertyUtils.str(new HashMap<>(), "object_name"));
    }

    @Test
    void str_nullValue_returnsEmpty() {
        Map<String, Object> map = new HashMap<>();
        map.put("object_name", null);
        assertEquals("", PropertyUtils.str(map, "object_name"));
    }

    @Test
    void str_string_returnsString() {
        assertEquals("report", PropertyUtils.str(Map.of("object_name", "report"), "object_name"));
    }

    @Test
    void str_number_returnsNumberAsString() {
        assertEquals("16", PropertyUtils.str(Map.of("user_privileges", 16), "user_privileges"));
    }
}
