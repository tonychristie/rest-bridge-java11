package com.spire.restbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an attribute value with type metadata.
 * Matches the format used by dfc-bridge for consistency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValue {

    /**
     * The data type of the attribute.
     * Values: boolean, integer, string, id, time, double
     */
    private String type;

    /**
     * The attribute value - scalar or List for repeating attributes.
     */
    private Object value;

    /**
     * Whether this is a repeating attribute.
     */
    private boolean repeating;
}
