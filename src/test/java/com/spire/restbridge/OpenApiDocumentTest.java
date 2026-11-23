package com.spire.restbridge;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reads the generated OpenAPI document from the running application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocumentTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void connect_listsEveryStatus() throws Exception {
        assertEquals(Set.of("200", "400", "401", "404", "406", "415", "500", "502", "503", "504"),
                responses("/api/v1/connect", "post").keySet());
    }

    @Test
    void disconnect_listsEveryStatusAndNo404() throws Exception {
        assertEquals(Set.of("204", "400", "406", "415", "500"),
                responses("/api/v1/disconnect", "post").keySet());
    }

    @Test
    void dql_listsEveryStatusWithDqlDescriptions() throws Exception {
        Map<String, Map<String, Object>> responses = responses("/api/v1/dql", "post");

        assertEquals(Set.of("200", "400", "401", "403", "404", "406", "409", "415", "500", "502", "503", "504"),
                responses.keySet());
        assertEquals("Invalid query, or an aggregate query, which REST Services does not support",
                responses.get("400").get("description"));
        assertEquals("DQL is not available for this user or server, or REST Services cannot be reached",
                responses.get("503").get("description"));
    }

    @Test
    void health_listsCommonStatuses() throws Exception {
        assertEquals(Set.of("200", "406", "500"), responses("/health", "get").keySet());
    }

    @Test
    void components_holdErrorResponseSchema() throws Exception {
        Map<String, Object> schemas = JsonPath.read(get("/api-docs").body(), "$.components.schemas");

        assertTrue(schemas.containsKey("ErrorResponse"));
    }

    @Test
    void missingSwaggerFile_returnsNotFound() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/missing.css");

        assertEquals(404, response.statusCode());
    }

    private Map<String, Map<String, Object>> responses(String path, String method) throws Exception {
        return JsonPath.read(get("/api-docs").body(), "$.paths['" + path + "']." + method + ".responses");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
