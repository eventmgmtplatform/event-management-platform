package com.eventmanagement.processor.application;

import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.admin.*;
import com.eventmanagement.processor.domain.rules.RuleSnapshot;
import com.eventmanagement.processor.ports.out.*;
import java.util.List;

/** Shared administration boundary for REST and API clients; no transport or persistence APIs. */
public final class AdminService {
    private final RuleAdministration repository;
    private final RuleValidation validator;
    private final RuleSnapshots snapshots;
    public AdminService(RuleAdministration repository,RuleValidation validator,RuleSnapshots snapshots) {
        this.repository=repository;this.validator=validator;this.snapshots=snapshots;
    }
    public RuleValidation.Validated validate(AdminActor actor,String json,String requestId) {
        return validator.validate(json);
    }
    public RuleAdministration.Receipt mutate(RuleAdministration.Mutation request) {

        if(request.requestId()==null || !request.requestId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") || request.expectedRevision()<0)
            throw new AdminFailure(AdminFailure.Kind.INVALID,"REQUEST_ID_AND_REVISION_REQUIRED");
        if(request.reason()==null || request.reason().isBlank() || request.reason().length()>2048)
            throw new AdminFailure(AdminFailure.Kind.INVALID,"CHANGE_REASON_REQUIRED");
        String id=request.ruleId();int version=request.version();String definition=request.definition();
        if(request.change()==RuleAdministration.Change.CREATE) {
            var validated=validator.validate(definition);id=validated.rule().id();version=validated.rule().version();definition=validated.canonicalJson();
        }
        ruleId(id);if(version<1)throw new AdminFailure(AdminFailure.Kind.INVALID,"VERSION_REQUIRED");
        return repository.mutate(new RuleAdministration.Mutation(request.actor(),request.requestId(),request.expectedRevision(),id,version,request.change(),definition,request.reason()));
    }
    public String list(AdminActor actor,int limit,String after,String requestId) {
        page(limit);if(!after.isEmpty())ruleId(after);
        return repository.list(actor.tenant(),limit,after);
    }
    public String get(AdminActor actor,String id,Integer version,String requestId) {
        ruleId(id);
        if(version!=null && version<1)throw new AdminFailure(AdminFailure.Kind.INVALID,"VERSION_REQUIRED");
        return repository.get(actor.tenant(),id,version);
    }
    public String history(AdminActor actor,String id,int limit,long after,String requestId) {
        ruleId(id);page(limit);
        if(after<0)throw new AdminFailure(AdminFailure.Kind.INVALID,"INVALID_CURSOR");
        return repository.history(actor.tenant(),id,limit,after);
    }
    public String explain(AdminActor actor,String id,String requestId) {

        if(id==null||!id.matches("[a-f0-9]{64}"))throw new AdminFailure(AdminFailure.Kind.INVALID,"INVALID_PROCESSING_ID");
        return repository.explain(actor.tenant(),id);
    }
    public ProcessingContext simulate(AdminActor actor,Event event,String candidate,String requestId) {
        return simulate(actor,event,candidate,requestId,event.receivedAt());
    }
    public ProcessingContext simulate(AdminActor actor,Event event,String candidate,String requestId,java.time.Instant evaluatedAt) {
        return simulateCandidates(actor,event,candidate==null?null:List.of(candidate),requestId,evaluatedAt);
    }
    public ProcessingContext simulateCandidates(AdminActor actor,Event event,List<String> candidates,String requestId,java.time.Instant evaluatedAt) {

        if(!actor.tenant().equals(event.tenant())) {
            repository.rejected(actor,requestId,"SIMULATE","simulations");
            throw new AdminFailure(AdminFailure.Kind.INVALID,"TENANT_MISMATCH");
        }
        RuleSnapshot snapshot;
        if(candidates==null)snapshot=snapshots.snapshot(actor.tenant());
        else {
            if(candidates.isEmpty() || candidates.size()>256)throw new AdminFailure(AdminFailure.Kind.INVALID,"CANDIDATE_LIMIT");
            var compiled=new java.util.ArrayList<com.eventmanagement.processor.domain.rules.Rule>();
            for(String candidate:candidates) {
                var rule=validator.validate(candidate).rule();
                compiled.add(new com.eventmanagement.processor.domain.rules.Rule(rule.id(),rule.version(),rule.priority(),true,
                        rule.checksum(),rule.condition(),rule.actions(),rule.blackout(),rule.enrichmentPlan(),rule.inventory()));
            }
            snapshot=new RuleSnapshot(actor.tenant(),compiled);
        }
        return EventProcessingPipeline.configured(tenant->snapshot,java.time.Clock.fixed(evaluatedAt,java.time.ZoneOffset.UTC)).simulate(event);
    }
    private static void page(int limit) {if(limit<1||limit>100)throw new AdminFailure(AdminFailure.Kind.INVALID,"INVALID_PAGE_LIMIT");}
    private static void ruleId(String id) {if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))throw new AdminFailure(AdminFailure.Kind.INVALID,"INVALID_RULE_ID");}
}
