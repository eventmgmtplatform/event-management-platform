package com.eventmanagement.integration;

/**
 * Indicates that an existing commandId was reused with command
 * content different from the command originally persisted.
 *
 * This is a permanent idempotency contract violation. Retrying the
 * same colliding command cannot resolve it.
 */
public class CommandIdCollisionException
        extends IllegalStateException {

    public CommandIdCollisionException(String commandId) {
        super(
                "commandId collision with different payload: " +
                commandId
        );
    }
}
