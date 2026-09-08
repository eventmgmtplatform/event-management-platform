package com.eventmanagement.integration;

public interface ServiceNowHttpInvoker {

    HttpResult invoke(String requestBody) throws Exception;

    default HttpResult updateTicket(String sysId, String requestBody) throws Exception {
        throw new UnsupportedOperationException("ServiceNow ticket updates are not configured");
    }

    record HttpResult(
            int httpStatus,
            String responseBody
    ) {
    }
}
