package com.spire.restbridge.exception;

/**
 * Exception thrown when an object is not found.
 */
public class ObjectNotFoundException extends RestBridgeException {

    public ObjectNotFoundException(String objectId) {
        super("OBJECT_NOT_FOUND", "Object not found: " + objectId);
    }
}
