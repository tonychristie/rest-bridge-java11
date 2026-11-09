package com.spire.restbridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application context and embedded server, as {@code java -jar} does.
 * The controller tests mock every service, so a bean that cannot be created only fails here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestBridgeApplicationTest {

    @Test
    void contextLoads() {
    }
}
