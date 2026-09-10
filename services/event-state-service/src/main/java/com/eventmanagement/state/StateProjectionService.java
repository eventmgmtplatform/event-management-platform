package com.eventmanagement.state;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import javax.sql.DataSource;

/** Re-read committed current state under the aggregate lock to prevent a delayed
 * caller from projecting an older snapshot. This remains synchronous dual-write,
 * not an outbox: a failure relies on Kafka redelivery for repair. */
@ApplicationScoped
public class StateProjectionService {
    @Inject DataSource dataSource;
    @Inject EventStateRepository states;
    @Inject OpenSearchStateClient search;

    @Transactional(rollbackOn=Exception.class)
    public void project(String eventKey) throws Exception {
        try (var c=dataSource.getConnection()) {
            try (var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                lock.setString(1,eventKey);lock.execute();
            }
            var state=states.findForUpdate(c,eventKey);
            if (state==null) throw new IllegalStateException("PROJECTION_STATE_MISSING");
            search.index(state);
        }
    }
}
