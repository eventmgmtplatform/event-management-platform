package com.eventmanagement.processor.ports.out;

import com.eventmanagement.processor.domain.admin.AdminActor;

public interface RuleAdministration {
    enum Change { CREATE, ENABLE, DISABLE, RETIRE }
    record Mutation(AdminActor actor,String requestId,long expectedRevision,String ruleId,int version,
                    Change change,String definition,String reason) {}
    record Receipt(String ruleId,int version,long revision,String status,String requestId) {}
    Receipt mutate(Mutation mutation);
    String list(String tenant,int limit,String after);
    String get(String tenant,String id,Integer version);
    String history(String tenant,String id,int limit,long afterRevision);
    String explain(String tenant,String processingId);
    void rejected(AdminActor actor,String requestId,String action,String resource);
}
