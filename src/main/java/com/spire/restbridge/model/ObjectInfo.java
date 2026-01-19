package com.spire.restbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Information about a Documentum object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectInfo {

    private String objectId;
    private String type;
    private String name;
    private Map<String, Object> attributes;
}
