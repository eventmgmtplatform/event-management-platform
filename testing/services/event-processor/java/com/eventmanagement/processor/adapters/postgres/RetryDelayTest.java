package com.eventmanagement.processor.adapters.postgres;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetryDelayTest {
    @Test void exponentialRetryIsBoundedWithoutOverflow() {
        assertEquals(1000, OutboxDispatcher.retryDelayMs(0, 1000, 30000));
        assertEquals(2000, OutboxDispatcher.retryDelayMs(1, 1000, 30000));
        assertEquals(30000, OutboxDispatcher.retryDelayMs(20, 1000, 30000));
        assertEquals(Long.MAX_VALUE, OutboxDispatcher.retryDelayMs(Long.MAX_VALUE, 1, Long.MAX_VALUE));
    }
    @Test void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> OutboxDispatcher.retryDelayMs(-1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> OutboxDispatcher.retryDelayMs(1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> OutboxDispatcher.retryDelayMs(1, 3, 2));
    }
}
