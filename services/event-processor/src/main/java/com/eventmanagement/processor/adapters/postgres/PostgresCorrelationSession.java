package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.ports.out.CorrelationPort;
import com.eventmanagement.processor.domain.correlation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
/** Must only be used inside the tenant-serialized processing transaction. */
public final class PostgresCorrelationSession implements CorrelationPort {
    private final Connection connection;private final ObjectMapper mapper;private final String sessionTenant;
    public PostgresCorrelationSession(Connection connection,ObjectMapper mapper,String tenant){this.connection=connection;this.mapper=mapper;this.sessionTenant=tenant;}
    public Optional<CorrelationGroup> load(String tenant,String key) {
        if(!sessionTenant.equals(tenant))throw new IllegalArgumentException("CORRELATION_TENANT_MISMATCH");
        try(var s=connection.prepareStatement("SELECT document::text FROM event_processor.correlation_group WHERE tenant=? AND correlation_key=?")) {
            s.setString(1,tenant);s.setString(2,key);s.setQueryTimeout(5);
            try(var r=s.executeQuery()){return r.next()?Optional.of(mapper.readValue(r.getString(1),CorrelationGroup.class)):Optional.empty();}
        }catch(Exception e){throw new IllegalStateException("CORRELATION_STORE_UNAVAILABLE",e);}
    }
    public void apply(String tenant,CorrelationResult result) {
        if(!sessionTenant.equals(tenant))throw new IllegalArgumentException("CORRELATION_TENANT_MISMATCH");
        if(result.failed())throw new IllegalArgumentException("FAILED_CORRELATION_CANNOT_COMMIT");
        for(var d:result.decisions())if(d.changed()) {
            var group=d.group();
            try(var s=connection.prepareStatement("INSERT INTO event_processor.correlation_group(tenant,correlation_key,revision,document) VALUES (?,?,?,?::jsonb) ON CONFLICT(tenant,correlation_key) DO UPDATE SET revision=EXCLUDED.revision,document=EXCLUDED.document WHERE event_processor.correlation_group.revision=EXCLUDED.revision-1")) {
                s.setString(1,tenant);s.setString(2,group.key());s.setLong(3,group.revision());s.setString(4,mapper.writeValueAsString(group));s.setQueryTimeout(5);
                if(s.executeUpdate()!=1)throw new IllegalStateException("CORRELATION_REVISION_CONFLICT");
            }catch(Exception e){throw new IllegalStateException("CORRELATION_WRITE_FAILED",e);}
        }
    }
}
