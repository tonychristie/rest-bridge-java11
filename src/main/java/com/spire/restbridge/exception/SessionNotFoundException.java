package com.spire.restbridge.exception;

/**
 * Exception thrown when a session is not found.
 */
public class SessionNotFoundException extends RestBridgeException {

    public SessionNotFoundException(String sessionId) {
        super("SESSION_NOT_FOUND", "Session not found: " + sessionId);
    }
}
