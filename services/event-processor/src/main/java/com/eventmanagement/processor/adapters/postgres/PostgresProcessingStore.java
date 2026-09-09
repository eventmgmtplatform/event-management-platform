package com.eventmanagement.processor.adapters.postgres;

import com.eventmanagement.processor.ports.out.ProcessingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;

@ApplicationScoped
public class PostgresProcessingStore implements ProcessingStore {
    private final DataSource dataSource;
    @Inject public PostgresProcessingStore(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public boolean accept(String id, String hash, String eventId, String tenant,
            String evidence, String topic, String key, String output) throws Exception {
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int inserted;
                try (var s = c.prepareStatement("""
                        INSERT INTO event_processor.processing_record
                          (processing_id,input_hash,event_id,tenant,evidence)
                        VALUES (?,?,?,?,?::jsonb) ON CONFLICT (processing_id) DO NOTHING
                        """)) {
                    s.setString(1,id); s.setString(2,hash); s.setString(3,eventId);
                    s.setString(4,tenant); s.setString(5,evidence); inserted=s.executeUpdate();
                }
                if (inserted == 0) {
                    try (var s = c.prepareStatement("SELECT input_hash FROM event_processor.processing_record WHERE processing_id=?")) {
                        s.setString(1,id);
                        try (var r=s.executeQuery()) {
                            if (!r.next() || !hash.equals(r.getString(1)))
                                throw new IllegalArgumentException("EVENT_ID_COLLISION");
                        }
                    }
                    c.commit(); return false;
                }
                try (var s=c.prepareStatement("""
                        INSERT INTO event_processor.output_outbox
                          (message_id,processing_id,topic,message_key,payload)
                        VALUES (?,?,?,?,?::jsonb)
                        """)) {
                    s.setString(1,id); s.setString(2,id); s.setString(3,topic);
                    s.setString(4,key); s.setString(5,output); s.executeUpdate();
                }
                c.commit(); return true;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
}
