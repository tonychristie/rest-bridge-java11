package com.spire.restbridge.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorResponsesCustomizer.
 */
class ErrorResponsesCustomizerTest {

    private static final Set<String> COMMON = Set.of("200", "406", "500");
    private static final Set<String> COMMON_AND_SESSION = Set.of(
            "200", "400", "401", "403", "404", "406", "409", "500", "502", "503", "504");

    private final ErrorResponsesCustomizer customizer = new ErrorResponsesCustomizer();

    @Test
    void customize_annotatedClass_addsCommonSet() throws Exception {
        Operation operation = customizer.customize(operation(), handler(new CommonController()));

        assertEquals(COMMON, operation.getResponses().keySet());
        assertEquals("#/components/schemas/ErrorResponse",
                operation.getResponses().get("500").getContent().get("application/json").getSchema().get$ref());
    }

    @Test
    void customize_sessionClass_addsCommonAndSessionSets() throws Exception {
        Operation operation = customizer.customize(operation(), handler(new SessionController()));

        assertEquals(COMMON_AND_SESSION, operation.getResponses().keySet());
    }

    @Test
    void customize_annotatedMethod_addsItsSets() throws Exception {
        Operation operation = customizer.customize(operation(),
                new HandlerMethod(new PlainController(), PlainController.class.getMethod("annotated")));

        assertEquals(COMMON_AND_SESSION, operation.getResponses().keySet());
    }

    @Test
    void customize_existingEntry_keepsItsDescription() throws Exception {
        Operation operation = operation();
        operation.getResponses().addApiResponse("404", new ApiResponse().description("Object or session not found"));

        customizer.customize(operation, handler(new SessionController()));

        assertEquals("Object or session not found", operation.getResponses().get("404").getDescription());
    }

    @Test
    void customize_requestBody_adds415OnlyWithBody() throws Exception {
        Operation withBody = operation().requestBody(new RequestBody());

        customizer.customize(withBody, handler(new CommonController()));
        Operation withoutBody = customizer.customize(operation(), handler(new CommonController()));

        assertTrue(withBody.getResponses().containsKey("415"));
        assertFalse(withoutBody.getResponses().containsKey("415"));
    }

    @Test
    void customize_noAnnotation_leavesOperationUnchanged() throws Exception {
        Operation operation = customizer.customize(operation(), handler(new PlainController()));

        assertEquals(Set.of("200"), operation.getResponses().keySet());
    }

    private static Operation operation() {
        return new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
    }

    private static HandlerMethod handler(Object controller) throws NoSuchMethodException {
        return new HandlerMethod(controller, controller.getClass().getMethod("get"));
    }

    @ErrorResponses
    static class CommonController {
        public void get() {
        }
    }

    @ErrorResponses(session = true)
    static class SessionController {
        public void get() {
        }
    }

    static class PlainController {
        public void get() {
        }

        @ErrorResponses(session = true)
        public void annotated() {
        }
    }
}
