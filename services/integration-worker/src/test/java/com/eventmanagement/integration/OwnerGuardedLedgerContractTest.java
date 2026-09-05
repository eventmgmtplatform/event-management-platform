package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OwnerGuardedLedgerContractTest {

    @Test
    void shouldExposeSafeRecoveryDecision() {
        IntegrationCommandLedger.Claim claim =
                new IntegrationCommandLedger.Claim(
                        IntegrationCommandLedger.Decision.RECONCILE,
                        null,
                        "owner-recovery"
                );

        assertEquals(
                IntegrationCommandLedger.Decision.RECONCILE,
                claim.decision()
        );
        assertEquals("owner-recovery", claim.claimOwner());
        assertNull(claim.resultPayload());
    }
}
