package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationOperationalStateTest {

    @Test
    void shouldOpenAdmissionOnlyInActiveMode() {
        IntegrationOperationalState state = new IntegrationOperationalState();
        state.active();
        assertEquals(IntegrationOperatingMode.ACTIVE, state.mode());
        assertTrue(state.admissionOpen());
        assertTrue(state.recoveryComplete());

        state.standby();
        assertEquals(IntegrationOperatingMode.STANDBY, state.mode());
        assertFalse(state.admissionOpen());
        assertFalse(state.recoveryComplete());
    }

    @Test
    void shouldCloseAdmissionDuringPullRestart() {
        IntegrationOperationalState state = new IntegrationOperationalState();
        state.active();
        state.pullRestartPending();
        assertEquals(IntegrationOperatingMode.PULL_RESTART, state.mode());
        assertFalse(state.admissionOpen());
        assertFalse(state.recoveryComplete());
    }

    @Test
    void shouldOpenAdmissionAfterPullRestartCompletes() {
        IntegrationOperationalState state = new IntegrationOperationalState();
        state.pullRestartPending();
        state.pullRestartComplete();

        assertEquals(IntegrationOperatingMode.PULL_RESTART, state.mode());
        assertTrue(state.admissionOpen());
        assertTrue(state.recoveryComplete());
    }

    @Test
    void shouldParseModesCaseInsensitively() {
        assertEquals(
                IntegrationOperatingMode.PULL_RESTART,
                IntegrationOperatingMode.parse("pull_restart")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationOperatingMode.parse("invalid")
        );
    }

    @Test
    void shouldCompareControlTokenWithoutPlainEquality() {
        assertTrue(
                IntegrationOperationalControlResource.constantTimeEquals(
                        "secret",
                        "secret"
                )
        );
        assertFalse(
                IntegrationOperationalControlResource.constantTimeEquals(
                        "secret",
                        "wrong"
                )
        );
    }
}
