package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an object is not found.
 */
public class ObjectNotFoundException extends RestBridgeException {

    public ObjectNotFoundException(String message) {
        super("OBJECT_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
