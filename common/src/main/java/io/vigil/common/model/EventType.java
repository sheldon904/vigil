package io.vigil.common.model;

/**
 * The kind of activity a security event describes. Detection rules key off
 * these values, so the set is deliberately small and explicit.
 */
public enum EventType {
    AUTH_SUCCESS,
    AUTH_FAILURE,
    NETWORK_CONNECTION,
    PROCESS_START,
    FILE_ACCESS
}
