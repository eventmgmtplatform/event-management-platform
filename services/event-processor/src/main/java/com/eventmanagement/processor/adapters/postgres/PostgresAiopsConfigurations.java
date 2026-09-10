package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.domain.admin.*;
import com.eventmanagement.processor.ports.out.AiopsConfigurations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@ApplicationScoped
public class PostgresAiopsConfigurations implements AiopsConfigurations {
    @Inject DataSource source;
    public List<AiopsConfiguration> list(String tenant,int limit,String after) {
        if(limit<1 || limit>100 || after==null || after.length()>64)throw new IllegalArgumentException("INVALID_PAGE");
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT id,name,enabled,revision FROM event_processor.aiops_configuration WHERE tenant=? AND NOT deleted AND id>? ORDER BY id LIMIT ?")) {
            s.setString(1,tenant);s.setString(2,after);s.setInt(3,limit);s.setQueryTimeout(3);
            try(var r=s.executeQuery()){var result=new ArrayList<AiopsConfiguration>();while(r.next())result.add(read(r));return List.copyOf(result);}
        }catch(SQLException e){throw unavailable();}
    }
    public AiopsConfiguration get(String tenant,String id) {
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT id,name,enabled,revision FROM event_processor.aiops_configuration WHERE tenant=? AND id=? AND NOT deleted")) {
            s.setString(1,tenant);s.setString(2,id);s.setQueryTimeout(3);
            try(var r=s.executeQuery()){if(!r.next())throw new AdminFailure(AdminFailure.Kind.NOT_FOUND,"AIOPS_NOT_FOUND");return read(r);}
        }catch(SQLException e){throw unavailable();}
    }
    public AiopsConfiguration save(AdminActor actor,AiopsConfiguration value,long expected,boolean create) {
        if(expected<0 || (create && expected!=0))throw new IllegalArgumentException("INVALID_REVISION");
        return mutate(actor,value.id(),value,expected,create,false);
    }
    public void delete(AdminActor actor,String id,long expected){mutate(actor,id,null,expected,false,true);}
    private AiopsConfiguration mutate(AdminActor actor,String id,AiopsConfiguration value,long expected,boolean create,boolean delete) {
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql=create?"INSERT INTO event_processor.aiops_configuration(tenant,id,name,enabled,revision) VALUES (?,?,?,?,1) ON CONFLICT DO NOTHING RETURNING id,name,enabled,revision":
                        delete?"UPDATE event_processor.aiops_configuration SET deleted=true,enabled=false,revision=revision+1 WHERE tenant=? AND id=? AND revision=? AND NOT deleted RETURNING id,name,enabled,revision":
                        "UPDATE event_processor.aiops_configuration SET name=?,enabled=?,revision=revision+1 WHERE tenant=? AND id=? AND revision=? AND NOT deleted RETURNING id,name,enabled,revision";
                AiopsConfiguration result;
                try(var s=c.prepareStatement(sql)) {
                    s.setQueryTimeout(3);
                    if(create){s.setString(1,actor.tenant());s.setString(2,id);s.setString(3,value.name());s.setBoolean(4,value.enabled());}
                    else if(delete){s.setString(1,actor.tenant());s.setString(2,id);s.setLong(3,expected);}
                    else{s.setString(1,value.name());s.setBoolean(2,value.enabled());s.setString(3,actor.tenant());s.setString(4,id);s.setLong(5,expected);}
                    try(var r=s.executeQuery()){if(!r.next())throw new AdminFailure(AdminFailure.Kind.CONFLICT,"AIOPS_REVISION_CONFLICT");result=read(r);}
                }
                try(var s=c.prepareStatement("INSERT INTO event_processor.aiops_change(tenant,id,revision,actor,operation,name,enabled) VALUES (?,?,?,?,?,?,?)")) {
                    s.setString(1,actor.tenant());s.setString(2,id);s.setLong(3,result.revision());s.setString(4,actor.subject());
                    s.setString(5,create?"CREATE":delete?"DELETE":"UPDATE");s.setString(6,result.name());s.setBoolean(7,result.enabled());s.executeUpdate();
                }
                c.commit();return result;
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw unavailable();}
    }
    private static AiopsConfiguration read(ResultSet r)throws SQLException{return new AiopsConfiguration(r.getString(1),r.getString(2),r.getBoolean(3),r.getLong(4));}
    private static AdminFailure unavailable(){return new AdminFailure(AdminFailure.Kind.UNAVAILABLE,"AIOPS_STORE_UNAVAILABLE");}
}
