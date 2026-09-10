package com.eventmanagement.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import javax.sql.DataSource;

/** One transaction: request ledger, current aggregate and immutable transition history. */
@ApplicationScoped
public class StateTransitionRepository {
    @Inject DataSource dataSource;
    @Inject EventStateRepository states;
    @Inject ObjectMapper mapper;

    public record Applied(ConsolidatedEventState state, String disposition) {}

    @Transactional(rollbackOn=Exception.class)
    public Applied apply(StateRequest request) throws Exception {
        try (var c=dataSource.getConnection()) {
            try (var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                lock.setString(1,request.eventKey());lock.execute();
            }
            boolean inserted;
            try (var claim=c.prepareStatement("INSERT INTO event_management.ess_state_request(message_id,event_key,tenant,payload,disposition) VALUES (?,?,?,?::jsonb,'APPLIED') ON CONFLICT(message_id) DO NOTHING")) {
                claim.setString(1,request.messageId());claim.setString(2,request.eventKey());claim.setString(3,request.tenant());
                claim.setString(4,request.payload().toString());inserted=claim.executeUpdate()==1;
            }
            if (!inserted) {
                try (var select=c.prepareStatement("SELECT payload FROM event_management.ess_state_request WHERE message_id=?")) {
                    select.setString(1,request.messageId());
                    try (var rows=select.executeQuery()) {
                        if (!rows.next() || !mapper.readTree(rows.getString(1)).equals(request.payload()))
                            throw new RejectedIntegrationResult("STATE_MESSAGE_ID_COLLISION");
                    }
                }
                var current=states.findForUpdate(c,request.eventKey());
                if (current==null) throw new IllegalStateException("STATE_LEDGER_WITHOUT_AGGREGATE");
                return new Applied(current,"DUPLICATE");
            }
            var current=states.findForUpdate(c,request.eventKey());
            if (current!=null && !request.tenant().equals(current.tenant))
                throw new RejectedIntegrationResult("EVENT_TENANT_COLLISION");
            if (current!=null && current.lastStateAt!=null && !request.occurredAt().isAfter(current.lastStateAt)) {
                try (var stale=c.prepareStatement("UPDATE event_management.ess_state_request SET disposition='STALE' WHERE message_id=?")) {
                    stale.setString(1,request.messageId());stale.executeUpdate();
                }
                return new Applied(current,"STALE");
            }
            String from=current==null || current.lastStateAt==null ? null : current.lifecycleStatus;
            String to=request.transition().equals("CLOSE") ? "CLOSED" : "OPEN";
            String kind="OPEN".equals(to) && "CLOSED".equals(from) ? "REOPEN" : request.transition();
            long version=current==null ? 1 : current.version+1;
            long tally=(current==null ? 0 : current.tally)+(to.equals("OPEN") ? 1 : 0);
            // Recovery preserves the previous source significance when one has been recorded.
            int source=to.equals("CLOSED") && current!=null && current.sourceSeverity!=null
                    ? current.sourceSeverity : request.sourceSeverity();
            String eventId=current==null ? request.eventId() : current.eventId;
            try (var update=c.prepareStatement("""
                    INSERT INTO event_management.event_state(event_key,event_id,tenant,lifecycle_status,
                        source_severity,effective_severity,tally,last_state_at,state_payload,version)
                    VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)
                    ON CONFLICT(event_key) DO UPDATE SET lifecycle_status=EXCLUDED.lifecycle_status,
                        source_severity=EXCLUDED.source_severity,effective_severity=EXCLUDED.effective_severity,
                        tally=EXCLUDED.tally,last_state_at=EXCLUDED.last_state_at,state_payload=EXCLUDED.state_payload,
                        version=EXCLUDED.version,last_updated_at=CURRENT_TIMESTAMP
                    """)) {
                update.setString(1,request.eventKey());update.setString(2,eventId);update.setString(3,request.tenant());update.setString(4,to);
                update.setInt(5,source);update.setInt(6,request.effectiveSeverity());update.setLong(7,tally);
                update.setObject(8,request.occurredAt());update.setString(9,request.payload().toString());update.setLong(10,version);
                update.executeUpdate();
            }
            try (var history=c.prepareStatement("INSERT INTO event_management.ess_event_transition(message_id,event_key,tenant,from_status,to_status,transition_type,aggregate_version,occurred_at,payload) VALUES (?,?,?,?,?,?,?,?,?::jsonb)")) {
                history.setString(1,request.messageId());history.setString(2,request.eventKey());history.setString(3,request.tenant());
                history.setString(4,from);history.setString(5,to);history.setString(6,kind);history.setLong(7,version);
                history.setObject(8,request.occurredAt());history.setString(9,request.payload().toString());history.executeUpdate();
            }
            return new Applied(states.findForUpdate(c,request.eventKey()),"APPLIED");
        }
    }
}
