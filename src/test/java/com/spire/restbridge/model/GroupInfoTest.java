package com.spire.restbridge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroupInfo JSON serialization.
 */
class GroupInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize_flagsTrue_writesIsPrivateIsDynamicAndStringFields() {
        GroupInfo group = GroupInfo.builder()
                .isPrivate(true)
                .isDynamic(true)
                .ownerName("dmadmin")
                .groupAddress("group@example.com")
                .groupSource("LDAP")
                .build();

        JsonNode json = objectMapper.valueToTree(group);

        assertTrue(json.get("isPrivate").asBoolean());
        assertTrue(json.get("isDynamic").asBoolean());
        assertEquals("dmadmin", json.get("ownerName").asText());
        assertEquals("group@example.com", json.get("groupAddress").asText());
        assertEquals("LDAP", json.get("groupSource").asText());
        assertFalse(json.has("private"));
        assertFalse(json.has("dynamic"));
    }

    @Test
    void serialize_flagsFalse_writesIsPrivateAndIsDynamicFalse() {
        GroupInfo group = GroupInfo.builder()
                .isPrivate(false)
                .isDynamic(false)
                .build();

        JsonNode json = objectMapper.valueToTree(group);

        assertFalse(json.get("isPrivate").asBoolean());
        assertFalse(json.get("isDynamic").asBoolean());
    }
}
