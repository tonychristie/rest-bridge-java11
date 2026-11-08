package com.spire.restbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RestServicesClientTest {

    private static final String NEXT_LINK = "\"links\": [{\"rel\": \"next\", \"href\": \"next-page\"}]";

    private final RestServicesClient client = new RestServicesClient();

    @Test
    void getAllPages_readsUntilPageWithoutNextLinkAndReturnsAllEntries() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": [{\"id\": \"a\"}], " + NEXT_LINK + "}"));
            server.enqueue(jsonResponse("{\"entries\": [{\"id\": \"b\"}], " + NEXT_LINK + "}"));
            server.enqueue(jsonResponse("{\"entries\": [{\"id\": \"c\"}]}"));
            server.start();

            List<Map<String, Object>> entries = client.getAllPages(session(server, 30),
                    "/repositories/{repo}/cabinets", Collections.emptyMap(), Map.of("repo", "testrepo"));

            assertEquals(List.of("a", "b", "c"),
                    entries.stream().map(entry -> entry.get("id")).collect(Collectors.toList()));
            assertEquals(3, server.getRequestCount());
            for (int page = 1; page <= 3; page++) {
                RecordedRequest request = server.takeRequest();
                assertEquals("/repositories/testrepo/cabinets", request.getRequestUrl().encodedPath());
                assertEquals("100", request.getRequestUrl().queryParameter("items-per-page"));
                assertEquals(String.valueOf(page), request.getRequestUrl().queryParameter("page"));
            }
        }
    }

    @Test
    void getAllPages_filterUriVariable_isSentFullyEncoded() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": []}"));
            server.start();

            client.getAllPages(session(server, 30), "/repositories/{repo}/users",
                    Map.of("filter", "{filter}"),
                    Map.of("repo", "testrepo", "filter", "starts-with(name,'o''b') and x='a,b'"));

            assertEquals("/repositories/testrepo/users"
                            + "?filter=starts-with%28name%2C%27o%27%27b%27%29%20and%20x%3D%27a%2Cb%27"
                            + "&items-per-page=100&page=1",
                    server.takeRequest().getPath());
        }
    }

    @Test
    void get_withAcceptType_sendsThatAcceptHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.start();

            client.get(session(server, 30), MediaType.parseMediaType("application/vnd.emc.documentum+json"),
                    uriBuilder -> uriBuilder.path("/repositories/{repo}").build("testrepo"));

            assertEquals("application/vnd.emc.documentum+json", server.takeRequest().getHeader("Accept"));
        }
    }

    @Test
    void requests_carryBasicAuthorizationFromSessionCredentials() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.enqueue(jsonResponse("{}"));
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();
            RestSessionHolder session = session(server, 30);

            client.get(session, "/repositories/{repo}", "testrepo");
            client.post(session, "/repositories/{repo}/batches", Map.of(), "testrepo");
            client.delete(session, "/repositories/{repo}/objects/{id}", "testrepo", "0901234567890001");

            for (int i = 0; i < 3; i++) {
                assertEquals("Basic dGVzdDAxOnNlY3JldA==", server.takeRequest().getHeader("Authorization"));
            }
        }
    }

    @Test
    void call_responseSlowerThanTimeout_throwsGatewayTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}").setBodyDelay(2, TimeUnit.SECONDS));
            server.start();
            RestSessionHolder session = session(server, 1);

            RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                    client.call("Failed to get user", null, () -> client.get(session, "/repositories/testrepo")));
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        }
    }

    @Test
    void call_notFoundWithMessage_throwsObjectNotFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            RestSessionHolder session = session(server, 30);

            ObjectNotFoundException ex = assertThrows(ObjectNotFoundException.class, () ->
                    client.call("Failed to get user", "User not found: test01",
                            () -> client.get(session, "/repositories/testrepo/users/test01")));
            assertEquals("OBJECT_NOT_FOUND", ex.getCode());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }
    }

    @Test
    void call_notFoundWithoutMessage_throwsRestErrorWithNotFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            RestSessionHolder session = session(server, 30);

            RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                    client.call("Failed to get cabinets", null,
                            () -> client.get(session, "/repositories/testrepo/cabinets")));
            assertFalse(ex instanceof ObjectNotFoundException);
            assertEquals("REST_ERROR", ex.getCode());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }
    }

    @Test
    void call_bridgeExceptionInside_comesOutUnchanged() {
        RestBridgeException thrown = new RestBridgeException("REST_ERROR", "No response from update",
                HttpStatus.BAD_GATEWAY);

        RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                client.call("Failed to update object", "Object not found: 0901234567890001", () -> {
                    throw thrown;
                }));
        assertSame(thrown, ex);
    }

    @Test
    @SuppressWarnings("unchecked")
    void batch_sendsSequentialAndContentTypeOnlyWithEntity_returnsResultsById() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"operations\": ["
                    + "{\"id\": \"updateObject\", \"response\": {\"status\": 200, \"entity\": \"{}\"}},"
                    + "{\"id\": \"getPermissions\", \"response\": {\"status\": 403, \"entity\": \"denied\"}}]}"));
            server.start();

            Map<String, RestServicesClient.BatchResult> results = client.batch(session(server, 30), true, List.of(
                    new RestServicesClient.BatchOperation("updateObject", "POST",
                            "/repositories/testrepo/objects/0901234567890001", "{\"properties\": {}}"),
                    new RestServicesClient.BatchOperation("getPermissions", "GET",
                            "/repositories/testrepo/objects/0901234567890001/permissions", null)));

            RecordedRequest request = server.takeRequest();
            assertEquals("/repositories/testrepo/batches", request.getPath());
            Map<String, Object> body = new ObjectMapper().readValue(request.getBody().readUtf8(), Map.class);
            assertEquals(true, body.get("sequential"));
            List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("operations");
            Map<String, Object> update = (Map<String, Object>) operations.get(0).get("request");
            Map<String, Object> permissions = (Map<String, Object>) operations.get(1).get("request");
            assertEquals(List.of(Map.of("name", "Content-Type", "value", "application/vnd.emc.documentum+json")),
                    update.get("headers"));
            assertFalse(permissions.containsKey("headers"));

            assertEquals(200, results.get("updateObject").getStatus());
            assertEquals("{}", results.get("updateObject").getEntity());
            assertEquals(403, results.get("getPermissions").getStatus());
            assertEquals("denied", results.get("getPermissions").getEntity());
        }
    }

    private static RestSessionHolder session(MockWebServer server, int timeoutSeconds) {
        RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                "test01", "secret", timeoutSeconds);
        session.setRepository("testrepo");
        return session;
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
