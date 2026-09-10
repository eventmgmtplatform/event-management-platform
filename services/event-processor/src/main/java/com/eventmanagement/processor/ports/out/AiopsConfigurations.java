package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.domain.admin.AdminActor;
import java.util.List;
public interface AiopsConfigurations {
    List<AiopsConfiguration> list(String tenant, int limit, String after);
    AiopsConfiguration get(String tenant, String id);
    AiopsConfiguration save(AdminActor actor, AiopsConfiguration configuration, long expectedRevision, boolean create);
    void delete(AdminActor actor, String id, long expectedRevision);
}
