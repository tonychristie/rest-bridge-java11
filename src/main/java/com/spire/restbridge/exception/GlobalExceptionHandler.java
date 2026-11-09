package com.spire.restbridge.exception;

import com.spire.restbridge.dto.ErrorResponse;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for the REST Bridge.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RestBridgeException.class)
    public ResponseEntity<ErrorResponse> handleRestBridgeException(RestBridgeException e) {
        HttpStatus status = e.getStatus();
        if (status.is5xxServerError()) {
            log.error("{} {}: {}", status.value(), e.getCode(), e.getMessage(), e);
        } else {
            log.warn("{} {}: {}", status.value(), e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Missing request parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid value for parameter " + e.getName() + ": " + e.getValue());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                  HttpServletRequest request) {
        return requestError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Method " + e.getMethod() + " is not supported for " + request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e,
                                                                     HttpServletRequest request) {
        MediaType contentType = e.getContentType();
        String type = contentType != null
                ? contentType.getType() + "/" + contentType.getSubtype()
                : request.getContentType();
        return requestError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content type " + type + " is not supported");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException e) {
        return requestError(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No endpoint " + e.getHttpMethod() + " " + e.getRequestURL());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred: " + e.getMessage()));
    }

    private ResponseEntity<ErrorResponse> requestError(HttpStatus status, String code, String message) {
        log.warn("{} {}: {}", status.value(), code, message);
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }
}
