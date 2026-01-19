package com.spire.restbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about a Documentum repository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryInfo {

    private String name;
    private String id;
    private String serverVersion;
    private String endpoint;
}
