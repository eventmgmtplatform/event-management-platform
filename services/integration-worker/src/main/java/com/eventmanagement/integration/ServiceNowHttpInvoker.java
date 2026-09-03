package com.eventmanagement.integration;

public interface ServiceNowHttpInvoker {

    HttpResult invoke(String requestBody) throws Exception;

    record HttpResult(
            int httpStatus,
            String responseBody
    ) {
    }
}
