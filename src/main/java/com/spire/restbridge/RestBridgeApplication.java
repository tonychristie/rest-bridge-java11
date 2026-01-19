package com.spire.restbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * REST Bridge Application.
 *
 * Provides a REST API that proxies to Documentum REST Services,
 * exposing a unified API for the dctm-vscode extension.
 *
 * Unlike dfc-bridge which requires DFC JARs, this service only uses
 * standard HTTP calls to Documentum REST Services endpoints.
 */
@SpringBootApplication
@EnableScheduling
public class RestBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestBridgeApplication.class, args);
    }
}
