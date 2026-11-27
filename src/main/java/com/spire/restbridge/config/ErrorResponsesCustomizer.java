package com.spire.restbridge.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds the shared error responses to each operation marked with {@link ErrorResponses}.
 * An entry the operation already has for a status is kept.
 */
@Component
public class ErrorResponsesCustomizer implements OperationCustomizer {

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

    private static final Map<String, String> SESSION_RESPONSES = new LinkedHashMap<>();

    static {
        SESSION_RESPONSES.put("400",
                "Invalid request: a missing or invalid parameter or body, or a request REST Services rejects");
        SESSION_RESPONSES.put("401", "REST Services rejected the credentials of the session");
        SESSION_RESPONSES.put("403", "No permission in the repository");
        SESSION_RESPONSES.put("404",
                "Session not found or expired, or a name or id that REST Services was asked for does not exist");
        SESSION_RESPONSES.put("409", "REST Services refused the change, for example on an immutable version");
        SESSION_RESPONSES.put("502", "REST Services error, or no valid response from REST Services");
        SESSION_RESPONSES.put("503", "REST Services cannot be reached");
        SESSION_RESPONSES.put("504", "REST Services did not answer within the timeout");
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ErrorResponses annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), ErrorResponses.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ErrorResponses.class);
        }
        if (annotation == null) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        addIfAbsent(responses, "406", "The Accept header does not allow application/json");
        if (operation.getRequestBody() != null) {
            addIfAbsent(responses, "415", "The request body is not application/json");
        }
        addIfAbsent(responses, "500", "Unexpected bridge error");
        if (annotation.session()) {
            for (Map.Entry<String, String> response : SESSION_RESPONSES.entrySet()) {
                addIfAbsent(responses, response.getKey(), response.getValue());
            }
        }
        return operation;
    }

    private static void addIfAbsent(ApiResponses responses, String status, String description) {
        responses.putIfAbsent(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA)))));
    }
}
