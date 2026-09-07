package com.eventmanagement.integration;

/**
 * Provider-scoped HTTP transport contract for GNM / Everbridge.
 *
 * <p>This interface intentionally does not reuse ServiceNowHttpInvoker.HttpResult.
 * Provider transports remain isolated until a genuinely provider-neutral contract
 * is justified by multiple implementations.</p>
 */
public interface GnmHttpInvoker {

    /**
     * Launches an Everbridge incident.
     *
     * @param organizationId Everbridge organization identifier used in the URI
     * @param requestBody serialized provider request
     * @return raw provider HTTP result
     * @throws Exception transport-level failure
     */
    HttpResult launch(String organizationId, String requestBody) throws Exception;

    /**
     * Retrieves the authoritative state of an Everbridge incident.
     *
     * <p>This operation is read-only and is the provider authority used
     * to confirm lifecycle state and reconcile ambiguous mutations.</p>
     *
     * @param organizationId Everbridge organization identifier
     * @param incidentId provider incident identifier returned by Launch
     * @return raw provider HTTP result
     * @throws Exception transport-level failure
     */
    HttpResult getIncident(
            String organizationId,
            String incidentId) throws Exception;

    record HttpResult(
            int httpStatus,
            String responseBody
    ) {
    }

    /**
     * Executes Everbridge CloseWithNotification for an existing incident.
     *
     * organizationId and incidentId are URI identities.
     */
    default HttpResult closeIncident(
            String organizationId,
            String incidentId,
            String requestBody
    ) throws Exception {
        throw new UnsupportedOperationException(
                "GNM Close transport is not implemented by this invoker"
        );
    }

}
