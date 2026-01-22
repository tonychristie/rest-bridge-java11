package com.spire.restbridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a Documentum type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeInfo {

    private String name;
    private String superType;
    private boolean systemType;
    private String category;
    private List<AttributeInfo> attributes;

    /**
     * Information about a type attribute.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeInfo {
        private String name;
        private String dataType;
        private int length;
        @JsonProperty("isRepeating")
        private boolean repeating;
        private boolean required;
        private String defaultValue;
        @JsonProperty("isInherited")
        private boolean inherited;
    }
}
