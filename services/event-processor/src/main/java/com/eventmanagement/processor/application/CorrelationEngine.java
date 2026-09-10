package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.domain.correlation.*;
import com.eventmanagement.processor.ports.out.CorrelationPort;
import java.util.*;

/** Bounded attribute GROUP correlation. Candidate state and writes share one durable transaction. */
public final class CorrelationEngine {
    public CorrelationResult evaluate(ProcessingContext context,CorrelationPort port) {
        var decisions=new ArrayList<CorrelationResult.Decision>();boolean failed=false;
        for(var rule:context.ruleSnapshot().rules()) {
            if(rule.correlationRule()==null)continue;
            try {
                if(!rule.condition().matches(context.event(),context.enrichment().facts(),new ArrayList<>())) {
                    decisions.add(new CorrelationResult.Decision(rule.id(),rule.version(),"SCOPE_NO_MATCH",0,null,false));continue;
                }
                var parts=new ArrayList<String>();parts.add(context.event().tenant());parts.add(rule.id());
                boolean missing=false;
                for(var field:rule.correlationRule().fields()) {
                    var value=field.resolve(context.event(),context.enrichment().facts());
                    if(value==null){missing=true;break;}parts.add(field.path);parts.add(value.toString());
                }
                if(missing){decisions.add(new CorrelationResult.Decision(rule.id(),rule.version(),"MISSING_CORRELATION_KEY",0,null,false));continue;}
                String key=StableIdentity.of("correlation-key-v1",parts.toArray(String[]::new));
                var previous=port.load(context.event().tenant(),key).orElse(null);
                decisions.add(decide(context.event(),rule,key,previous));
            }catch(IllegalArgumentException | java.time.DateTimeException invalid) {
                failed=true;decisions.add(new CorrelationResult.Decision(rule.id(),rule.version(),"CORRELATION_INPUT_INVALID",0,null,false));
            }
        }
        return new CorrelationResult(decisions,failed || decisions.stream().anyMatch(d->d.reason().equals("CANDIDATE_LIMIT")));
    }
    private CorrelationResult.Decision decide(Event event,Rule rule,String key,CorrelationGroup previous) {
        var config=rule.correlationRule();var time=event.receivedAt();
        if(event.eventKey().length()>256 || event.eventId().length()>256)throw new IllegalArgumentException("CORRELATION_ID_LIMIT");
        if(previous!=null && time.isBefore(previous.updatedAt()))return decision(rule,"LATE_EVENT_IGNORED",previous.members().size(),previous,false);
        if(previous!=null && previous.resolved() && !time.isAfter(previous.updatedAt()) && !event.recovery())return decision(rule,"TIED_EVENT_IGNORED",previous.members().size(),previous,false);
        var members=new ArrayList<CorrelationGroup.Member>();
        if(previous!=null)for(var member:previous.members()) {
            boolean expired=member.status().equals("ACTIVE") && member.observedAt().isBefore(time.minusSeconds(config.windowSeconds()));
            members.add(expired?new CorrelationGroup.Member(member.eventKey(),member.eventId(),member.observedAt(),"EXPIRED"):member);
        }
        boolean active=members.stream().anyMatch(m->m.status().equals("ACTIVE"));
        int cycle=previous==null?1:previous.cycle();
        boolean newCycle=!event.recovery() && previous!=null && !active;
        if(newCycle){cycle++;members.clear();}
        int index=-1;for(int i=0;i<members.size();i++)if(members.get(i).eventKey().equals(event.eventKey()))index=i;
        if(index<0 && event.recovery())return decision(rule,"ORPHAN_RECOVERY",members.size(),previous,false);
        if(index>=0) {
            var existing=members.get(index);
            if(time.equals(existing.observedAt()) && (existing.status().equals("RESOLVED") || existing.eventId().compareTo(event.eventId())>=0) && !event.recovery())
                return decision(rule,"TIED_EVENT_IGNORED",members.size(),previous,false);
        }
        if(index<0 && members.size()>=config.maxCandidates())return decision(rule,"CANDIDATE_LIMIT",members.size(),previous,false);
        var member=new CorrelationGroup.Member(event.eventKey(),event.eventId(),time,event.recovery()?"RESOLVED":"ACTIVE");
        if(index>=0)members.set(index,member);else members.add(member);
        members.sort(Comparator.comparing(CorrelationGroup.Member::eventKey));
        String groupId=StableIdentity.of("correlation-group-v1",key,Integer.toString(cycle));
        var group=new CorrelationGroup(key,groupId,cycle,previous==null?1:previous.revision()+1,time,
                members.stream().noneMatch(m->m.status().equals("ACTIVE")),members);
        return decision(rule,event.recovery()?"MEMBER_RESOLVED":newCycle?"NEW_CYCLE":previous==null?"GROUP_CREATED":"MEMBER_ATTACHED",members.size(),group,true);
    }
    private CorrelationResult.Decision decision(Rule rule,String reason,int count,CorrelationGroup group,boolean changed){return new CorrelationResult.Decision(rule.id(),rule.version(),reason,count,group,changed);}
    public StageResult stage(CorrelationResult result) {
        boolean match=result.decisions().stream().anyMatch(CorrelationResult.Decision::changed);
        return new StageResult(StageResult.Stage.Correlation,result.failed()?StageResult.Status.FAILED:StageResult.Status.SUCCESS,
                result.failed()?StageResult.Match.ERROR:match?StageResult.Match.MATCH:StageResult.Match.NO_MATCH,
                result.failed()?StageResult.Directive.DEAD_LETTER:StageResult.Directive.CONTINUE,
                result.failed()?"CORRELATION_FAILED":match?"CORRELATION_MATCH":"CORRELATION_NO_MATCH",null,null,0,
                Map.of("ruleCount",Integer.toString(result.decisions().size())));
    }
}
