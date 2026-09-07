#!/usr/bin/env bash
set -euo pipefail

cd /opt/event-management-platform || exit 1

ROOT="services/integration-worker"
JAVA="${ROOT}/src/main/java/com/eventmanagement/integration"
TEST="${ROOT}/src/test/java/com/eventmanagement/integration"
ROUTE="${ROOT}/src/main/resources/routes/integration-worker.xml"
PROPS="${ROOT}/src/main/resources/application.properties"
COMPOSE="infrastructure/docker-compose.yml"
MAPPINGS="infrastructure/mock-integrations/servicenow/mappings"

echo "===== SN-02.9F SERVICENOW LOOKUP INSTALLATION ====="

[[ "$(git branch --show-current)" == "feature/os-01-02-servicenow-core-foundation" ]] || {
  echo "ASSERTION=FAIL | Unexpected branch"
  exit 1
}

grep -q "RECONCILE" "${JAVA}/IntegrationCommandLedger.java" || {
  echo "ASSERTION=FAIL | SN-02.9E is not installed"
  exit 1
}

install -d "${JAVA}" "${TEST}" "${MAPPINGS}"

tee "${JAVA}/ServiceNowLookupClient.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface ServiceNowLookupClient {

    enum Status {
        FOUND,
        NOT_FOUND
    }

    record LookupResult(
            Status status,
            JsonNode ticket
    ) {
    }

    LookupResult findByEventId(String eventId) throws Exception;
}
JAVAEOF

tee "${JAVA}/CamelServiceNowLookupClient.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class CamelServiceNowLookupClient
        implements ServiceNowLookupClient {

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;
    private final String endpointUri;

    @Inject
    public CamelServiceNowLookupClient(
            ProducerTemplate producerTemplate,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "integration.servicenow.base-url")
            String baseUrl,
            @ConfigProperty(name = "integration.servicenow.lookup-ticket-path")
            String lookupTicketPath,
            @ConfigProperty(name = "integration.servicenow.timeout-ms")
            long timeoutMs
    ) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
        this.endpointUri =
                baseUrl + lookupTicketPath +
                "?throwExceptionOnFailure=true" +
                "&automaticRetriesDisabled=true" +
                "&connectTimeout=" + timeoutMs +
                "&responseTimeout=" + timeoutMs;
    }

    @Override
    public LookupResult findByEventId(String eventId)
            throws Exception {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "eventId is required for ServiceNow reconciliation"
            );
        }

        String serviceNowQuery = "u_event_id=" + eventId.trim();
        String httpQuery =
                "sysparm_query=" + URLEncoder.encode(
                        serviceNowQuery,
                        StandardCharsets.UTF_8
                ) + "&sysparm_limit=2";

        Exchange response = producerTemplate.request(
                endpointUri,
                request -> {
                    request.getMessage().setHeader(
                            Exchange.HTTP_METHOD,
                            "GET"
                    );
                    request.getMessage().setHeader(
                            Exchange.HTTP_QUERY,
                            httpQuery
                    );
                }
        );

        Exception exception = response.getException();
        if (exception == null) {
            exception = response.getProperty(
                    Exchange.EXCEPTION_CAUGHT,
                    Exception.class
            );
        }
        if (exception != null) {
            throw exception;
        }

        int httpStatus = response.getMessage().getHeader(
                Exchange.HTTP_RESPONSE_CODE,
                0,
                Integer.class
        );
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalStateException(
                    "ServiceNow lookup returned HTTP " + httpStatus
            );
        }

        String responseBody =
                response.getMessage().getBody(String.class);
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root == null ? null : root.get("result");

        if (results == null || !results.isArray()) {
            throw new IllegalStateException(
                    "ServiceNow lookup response has no result array"
            );
        }
        if (results.isEmpty()) {
            return new LookupResult(Status.NOT_FOUND, null);
        }
        if (results.size() != 1) {
            throw new IllegalStateException(
                    "ServiceNow lookup is ambiguous for eventId: " + eventId
            );
        }

        JsonNode ticket = results.get(0);
        requireTicketField(ticket, "sys_id", eventId);
        requireTicketField(ticket, "number", eventId);
        return new LookupResult(Status.FOUND, ticket.deepCopy());
    }

    private void requireTicketField(
            JsonNode ticket,
            String field,
            String eventId
    ) {
        if (ticket == null || !ticket.isObject() ||
                ticket.path(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "ServiceNow lookup ticket has no " + field +
                    " for eventId: " + eventId
            );
        }
    }
}
JAVAEOF

tee "${JAVA}/ServiceNowReconciliationProcessor.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Named("serviceNowReconciliationProcessor")
@ApplicationScoped
public class ServiceNowReconciliationProcessor
        implements Processor {

    public static final String OUTCOME_PROPERTY =
            "serviceNowReconciliationOutcome";

    private static final Logger LOG = Logger.getLogger(
            ServiceNowReconciliationProcessor.class
    );

    private final ServiceNowLookupClient lookupClient;
    private final ObjectMapper objectMapper;
    private final int notFoundConfirmations;
    private final long confirmationDelayMs;

    @Inject
    public ServiceNowReconciliationProcessor(
            ServiceNowLookupClient lookupClient,
            ObjectMapper objectMapper,
            @ConfigProperty(
                    name = "integration.servicenow.reconciliation.not-found-confirmations",
                    defaultValue = "3"
            ) int notFoundConfirmations,
            @ConfigProperty(
                    name = "integration.servicenow.reconciliation.confirmation-delay-ms",
                    defaultValue = "1000"
            ) long confirmationDelayMs
    ) {
        if (notFoundConfirmations < 2) {
            throw new IllegalArgumentException(
                    "At least two NOT_FOUND confirmations are required"
            );
        }
        if (confirmationDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Confirmation delay cannot be negative"
            );
        }
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
        this.notFoundConfirmations = notFoundConfirmations;
        this.confirmationDelayMs = confirmationDelayMs;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.removeProperty(OUTCOME_PROPERTY);
        String commandId = exchange.getProperty("commandId", String.class);
        String eventId = exchange.getProperty("eventId", String.class);

        for (int confirmation = 1;
             confirmation <= notFoundConfirmations;
             confirmation++) {
            ServiceNowLookupClient.LookupResult result;
            try {
                result = lookupClient.findByEventId(eventId);
            } catch (Exception exception) {
                exchange.setProperty(OUTCOME_PROPERTY, "RETRY_LATER");
                LOG.warnv(
                        "ServiceNow reconciliation deferred: " +
                        "commandId={0}, error={1}",
                        commandId,
                        exception.getClass().getSimpleName()
                );
                return;
            }

            if (result.status() == ServiceNowLookupClient.Status.FOUND) {
                ObjectNode response = objectMapper.createObjectNode();
                response.set("result", result.ticket());
                exchange.getMessage().setBody(
                        objectMapper.writeValueAsString(response)
                );
                exchange.getMessage().setHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        200
                );
                exchange.setProperty("integrationAttempt", 1);
                exchange.setProperty(OUTCOME_PROPERTY, "FOUND");
                LOG.infov(
                        "ServiceNow reconciliation found ticket: " +
                        "commandId={0}, eventId={1}",
                        commandId,
                        eventId
                );
                return;
            }

            LOG.infov(
                    "ServiceNow reconciliation NOT_FOUND confirmation: " +
                    "commandId={0}, confirmation={1}/{2}",
                    commandId,
                    confirmation,
                    notFoundConfirmations
            );

            if (confirmation < notFoundConfirmations &&
                    confirmationDelayMs > 0) {
                Thread.sleep(confirmationDelayMs);
            }
        }

        exchange.setProperty(OUTCOME_PROPERTY, "NOT_FOUND_CONFIRMED");
        LOG.warnv(
                "ServiceNow reconciliation confirmed no ticket: " +
                "commandId={0}, confirmations={1}",
                commandId,
                notFoundConfirmations
        );
    }
}
JAVAEOF

python3 - "${ROUTE}" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
old = '''                <!--
                    Ownership of an expired claim was acquired. Until the
                    lookup/reconciliation client is installed, stop here.
                    A blind CREATE could duplicate a ServiceNow ticket.
                -->
                <when>
                    <simple>${exchangeProperty.integrationIdempotencyDecision} == 'RECONCILE'</simple>

                    <log loggingLevel="WARN"
                         message="Reconciliación pendiente; CREATE bloqueado: commandId=${exchangeProperty.commandId}"/>

                    <stop/>
                </when>
'''
new = '''                <!--
                    An expired claim is never blindly created again. First
                    reconcile by the durable ServiceNow event identity.
                -->
                <when>
                    <simple>${exchangeProperty.integrationIdempotencyDecision} == 'RECONCILE'</simple>

                    <process ref="serviceNowReconciliationProcessor"/>

                    <choice>
                        <when>
                            <simple>${exchangeProperty.serviceNowReconciliationOutcome} == 'FOUND'</simple>

                            <process ref="integrationResultProcessor"/>
                        </when>
                        <when>
                            <simple>${exchangeProperty.serviceNowReconciliationOutcome} == 'NOT_FOUND_CONFIRMED'</simple>

                            <removeHeaders pattern="*"/>

                            <setHeader name="Content-Type">
                                <constant>application/json</constant>
                            </setHeader>

                            <setHeader name="CamelHttpMethod">
                                <constant>POST</constant>
                            </setHeader>

                            <log loggingLevel="WARN"
                                 message="Reconciliación confirmó ausencia; creando ticket: commandId=${exchangeProperty.commandId}"/>

                            <process ref="serviceNowRetryExecutor"/>
                            <process ref="integrationResultProcessor"/>
                        </when>
                        <when>
                            <simple>${exchangeProperty.serviceNowReconciliationOutcome} == 'RETRY_LATER'</simple>

                            <log loggingLevel="WARN"
                                 message="Reconciliación diferida sin CREATE ni resultado terminal: commandId=${exchangeProperty.commandId}"/>

                            <stop/>
                        </when>
                        <otherwise>
                            <log loggingLevel="ERROR"
                                 message="Resultado de reconciliación no resuelto; flujo detenido: commandId=${exchangeProperty.commandId}"/>

                            <stop/>
                        </otherwise>
                    </choice>
                </when>
'''
if old not in s:
    raise SystemExit("ROUTE_LOOKUP_PATCH_STATUS=MARKER_NOT_FOUND")
p.write_text(s.replace(old, new, 1))
print("ROUTE_LOOKUP_PATCH_STATUS=APPLIED")
PY

python3 - "${PROPS}" "${COMPOSE}" <<'PY'
from pathlib import Path
import sys
props = Path(sys.argv[1])
compose = Path(sys.argv[2])
s = props.read_text()
marker = 'integration.servicenow.create-ticket-path=${SERVICENOW_CREATE_TICKET_PATH:/api/now/table/incident}\n'
addition = (
    'integration.servicenow.lookup-ticket-path=${SERVICENOW_LOOKUP_TICKET_PATH:/api/now/table/incident}\n'
    'integration.servicenow.reconciliation.not-found-confirmations=${SERVICENOW_RECONCILIATION_NOT_FOUND_CONFIRMATIONS:3}\n'
    'integration.servicenow.reconciliation.confirmation-delay-ms=${SERVICENOW_RECONCILIATION_CONFIRMATION_DELAY_MS:1000}\n'
)
if 'integration.servicenow.lookup-ticket-path=' not in s:
    if marker not in s:
        raise SystemExit('PROPERTIES_LOOKUP_PATCH_STATUS=MARKER_NOT_FOUND')
    props.write_text(s.replace(marker, marker + addition, 1))

s = compose.read_text()
marker = '      SERVICENOW_CREATE_TICKET_PATH: /api/now/table/incident\n'
addition = (
    '      SERVICENOW_LOOKUP_TICKET_PATH: /api/now/table/incident\n'
    '      SERVICENOW_RECONCILIATION_NOT_FOUND_CONFIRMATIONS: 3\n'
    '      SERVICENOW_RECONCILIATION_CONFIRMATION_DELAY_MS: 1000\n'
)
if '      SERVICENOW_LOOKUP_TICKET_PATH:' not in s:
    if marker not in s:
        raise SystemExit('COMPOSE_LOOKUP_PATCH_STATUS=MARKER_NOT_FOUND')
    compose.write_text(s.replace(marker, marker + addition, 1))
print('LOOKUP_CONFIGURATION_PATCH_STATUS=APPLIED')
PY

tee "${MAPPINGS}/lookup-incident-not-found.json" >/dev/null <<'JSONEOF'
{
  "priority": 10,
  "request": {
    "method": "GET",
    "urlPath": "/api/now/table/incident",
    "queryParameters": {
      "sysparm_query": {
        "matches": "u_event_id=.*"
      },
      "sysparm_limit": {
        "equalTo": "2"
      }
    }
  },
  "response": {
    "status": 200,
    "jsonBody": {
      "result": []
    },
    "headers": {
      "Content-Type": "application/json"
    }
  }
}
JSONEOF

tee "${TEST}/ServiceNowReconciliationProcessorTest.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNowReconciliationProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecoverExistingTicketWithoutCreate()
            throws Exception {
        JsonNode ticket = objectMapper.readTree(
                """
                {"sys_id":"snow-001","number":"INC001"}
                """
        );
        StubLookup lookup = new StubLookup(
                new ServiceNowLookupClient.LookupResult(
                        ServiceNowLookupClient.Status.FOUND,
                        ticket
                )
        );
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);

        assertEquals(
                "FOUND",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
        assertEquals(1, lookup.calls);
        assertEquals(
                "INC001",
                objectMapper.readTree(
                        exchange.getMessage().getBody(String.class)
                ).path("result").path("number").asText()
        );
    }

    @Test
    void shouldRequireAllNotFoundConfirmations()
            throws Exception {
        ServiceNowLookupClient.LookupResult notFound =
                new ServiceNowLookupClient.LookupResult(
                        ServiceNowLookupClient.Status.NOT_FOUND,
                        null
                );
        StubLookup lookup = new StubLookup(
                notFound,
                notFound,
                notFound
        );
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);

        assertEquals(
                "NOT_FOUND_CONFIRMED",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
        assertEquals(3, lookup.calls);
    }

    @Test
    void shouldDeferWithoutCreateWhenLookupFails()
            throws Exception {
        ServiceNowLookupClient lookup = eventId -> {
            throw new IllegalStateException("lookup unavailable");
        };
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);
        assertEquals(
                "RETRY_LATER",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
    }

    private Exchange exchange() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("commandId", "cmd-reconcile-001");
        exchange.setProperty("eventId", "evt-reconcile-001");
        return exchange;
    }

    private static final class StubLookup
            implements ServiceNowLookupClient {
        private final Deque<LookupResult> results;
        private int calls;

        private StubLookup(LookupResult... results) {
            this.results = new ArrayDeque<>(Arrays.asList(results));
        }

        @Override
        public LookupResult findByEventId(String eventId) {
            calls++;
            return results.removeFirst();
        }
    }
}
JAVAEOF

echo
echo "===== BUILD AND TEST ====="
(cd "${ROOT}" && mvn clean test)

echo
echo "===== COMPOSE VALIDATION ====="
docker compose --env-file .env -f "${COMPOSE}" config --quiet
echo "ASSERTION=PASS | Compose configuration valid"

echo
echo "===== LOOKUP SAFETY CONTRACT ====="
grep -q 'sysparm_limit=2' "${JAVA}/CamelServiceNowLookupClient.java"
grep -q 'results.size() != 1' "${JAVA}/CamelServiceNowLookupClient.java"
grep -q 'NOT_FOUND_CONFIRMED' "${JAVA}/ServiceNowReconciliationProcessor.java"
grep -q 'RETRY_LATER' "${JAVA}/ServiceNowReconciliationProcessor.java"
grep -q 'serviceNowReconciliationProcessor' "${ROUTE}"
echo "ASSERTION=PASS | Lookup requires a unique ticket"
echo "ASSERTION=PASS | NOT_FOUND requires repeated confirmation"
echo "ASSERTION=PASS | Lookup failure cannot enter CREATE branch"

echo
echo "===== CHANGESET ====="
git status --short
git diff --stat

if ! git diff --cached --quiet; then
  echo "ASSERTION=FAIL | Staging area must remain empty"
  exit 1
fi

echo
echo "===== FINAL RESULT ====="
echo "SERVICENOW_LOOKUP=IMPLEMENTED"
echo "LOOKUP_IDENTITY=u_event_id"
echo "LOOKUP_MAX_MATCHES=1"
echo "NOT_FOUND_CONFIRMATIONS=3"
echo "AMBIGUOUS_LOOKUP=BLOCK_CREATE"
echo "LOOKUP_FAILURE=BLOCK_CREATE"
echo "TECHNICAL_DEBT_SN_006=IN_PROGRESS"
echo "SN_02_9F_SERVICENOW_LOOKUP_SOURCE=PASS"
