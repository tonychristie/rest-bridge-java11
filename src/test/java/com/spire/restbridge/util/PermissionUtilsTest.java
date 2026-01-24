package com.spire.restbridge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PermissionUtils.
 */
class PermissionUtilsTest {

    @Test
    void permitToLabel_none() {
        assertEquals("NONE", PermissionUtils.permitToLabel(1));
    }

    @Test
    void permitToLabel_browse() {
        assertEquals("BROWSE", PermissionUtils.permitToLabel(2));
    }

    @Test
    void permitToLabel_read() {
        assertEquals("READ", PermissionUtils.permitToLabel(3));
    }

    @Test
    void permitToLabel_relate() {
        assertEquals("RELATE", PermissionUtils.permitToLabel(4));
    }

    @Test
    void permitToLabel_version() {
        assertEquals("VERSION", PermissionUtils.permitToLabel(5));
    }

    @Test
    void permitToLabel_write() {
        assertEquals("WRITE", PermissionUtils.permitToLabel(6));
    }

    @Test
    void permitToLabel_delete() {
        assertEquals("DELETE", PermissionUtils.permitToLabel(7));
    }

    @Test
    void permitToLabel_unknown() {
        assertEquals("UNKNOWN", PermissionUtils.permitToLabel(0));
        assertEquals("UNKNOWN", PermissionUtils.permitToLabel(8));
        assertEquals("UNKNOWN", PermissionUtils.permitToLabel(-1));
    }

    // Tests for labelToPermit

    @Test
    void labelToPermit_none() {
        assertEquals(1, PermissionUtils.labelToPermit("None"));
        assertEquals(1, PermissionUtils.labelToPermit("NONE"));
        assertEquals(1, PermissionUtils.labelToPermit("none"));
    }

    @Test
    void labelToPermit_browse() {
        assertEquals(2, PermissionUtils.labelToPermit("Browse"));
        assertEquals(2, PermissionUtils.labelToPermit("BROWSE"));
    }

    @Test
    void labelToPermit_read() {
        assertEquals(3, PermissionUtils.labelToPermit("Read"));
        assertEquals(3, PermissionUtils.labelToPermit("READ"));
    }

    @Test
    void labelToPermit_relate() {
        assertEquals(4, PermissionUtils.labelToPermit("Relate"));
        assertEquals(4, PermissionUtils.labelToPermit("RELATE"));
    }

    @Test
    void labelToPermit_version() {
        assertEquals(5, PermissionUtils.labelToPermit("Version"));
        assertEquals(5, PermissionUtils.labelToPermit("VERSION"));
    }

    @Test
    void labelToPermit_write() {
        assertEquals(6, PermissionUtils.labelToPermit("Write"));
        assertEquals(6, PermissionUtils.labelToPermit("WRITE"));
    }

    @Test
    void labelToPermit_delete() {
        assertEquals(7, PermissionUtils.labelToPermit("Delete"));
        assertEquals(7, PermissionUtils.labelToPermit("DELETE"));
    }

    @Test
    void labelToPermit_unknown() {
        assertEquals(-1, PermissionUtils.labelToPermit("Unknown"));
        assertEquals(-1, PermissionUtils.labelToPermit("Invalid"));
        assertEquals(-1, PermissionUtils.labelToPermit(null));
        assertEquals(-1, PermissionUtils.labelToPermit(""));
    }
}
