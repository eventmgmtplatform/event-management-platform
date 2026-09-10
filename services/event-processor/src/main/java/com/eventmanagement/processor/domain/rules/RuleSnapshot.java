package com.eventmanagement.processor.domain.rules;

import com.eventmanagement.processor.domain.*;
import java.util.*;

public record RuleSnapshot(String tenant, List<Rule> rules) {
    public RuleSnapshot {
        if(tenant==null || (tenant.isBlank() && !rules.isEmpty())) throw new IllegalArgumentException("TENANT_REQUIRED");
        rules=rules.stream().sorted(Comparator.comparingInt(Rule::priority).reversed()
                .thenComparing(Rule::id).thenComparingInt(Rule::version)).toList();
        if(rules.stream().filter(r->r.correlationRule()!=null).count()>8)throw new IllegalArgumentException("CORRELATION_RULE_LIMIT");
        if(rules.size()>256 || rules.stream().map(Rule::id).distinct().count()!=rules.size())
            throw new IllegalArgumentException("INVALID_SNAPSHOT");
        if(rules.stream().anyMatch(r->!r.enabled())) throw new IllegalArgumentException("DISABLED_RULE");
    }
    public String checksum() {
        var parts=new ArrayList<String>(); parts.add(tenant);
        for(var rule:rules) { parts.add(rule.id()); parts.add(Integer.toString(rule.version())); parts.add(rule.checksum()); }
        return StableIdentity.of("rule-snapshot-v1",parts.toArray(String[]::new));
    }
    public StageResult evaluateSuppressions(Event event,java.time.Instant now) {
        if(!tenant.equals(event.tenant()))throw new IllegalArgumentException("SNAPSHOT_TENANT_MISMATCH");
        var evidence=new LinkedHashMap<String,String>();evidence.put("snapshotChecksum",checksum());evidence.put("evaluatedAt",now.toString());
        Rule primary=null;int count=0,matches=0;
        for(var rule:rules) {
            var suppression=rule.suppression();if(suppression==null)continue;var window=suppression.window();
            boolean time=window.windowMatches(now),scope=window.scopeMatches(event),match=suppression.eligible()&&time&&scope;
            String key="suppression."+(count++)+".";
            evidence.put(key+"id",rule.id());evidence.put(key+"version",Integer.toString(rule.version()));evidence.put(key+"checksum",rule.checksum());
            evidence.put(key+"source",suppression.source());evidence.put(key+"externalStatus",suppression.externalStatus());
            evidence.put(key+"externalReference",suppression.externalReference());evidence.put(key+"windowMatch",Boolean.toString(time));
            evidence.put(key+"scopeMatch",Boolean.toString(scope));evidence.put(key+"eligible",Boolean.toString(suppression.eligible()));
            evidence.put(key+"reason",window.reason());evidence.put(key+"validFrom",window.from().toString());evidence.put(key+"validTo",window.to().toString());
            evidence.put(key+"timezone",window.timezone().getId());
            if(match){matches++;if(primary==null)primary=rule;}
        }
        evidence.put("suppressionCount",Integer.toString(count));evidence.put("matchCount",Integer.toString(matches));
        return new StageResult(StageResult.Stage.AutoSuppression,StageResult.Status.SUCCESS,
                primary==null?StageResult.Match.NO_MATCH:StageResult.Match.MATCH,
                primary==null?StageResult.Directive.CONTINUE:StageResult.Directive.SUPPRESS_INTEGRATIONS,
                primary==null?"NO_MATCHING_SUPPRESSION":"SUPPRESSION_MATCH",primary==null?null:primary.id(),primary==null?null:primary.version(),0,evidence);
    }
    public StageResult evaluateBlackouts(Event event,java.time.Instant now) {
        if(!tenant.equals(event.tenant()))throw new IllegalArgumentException("SNAPSHOT_TENANT_MISMATCH");
        if(tenant.isBlank())return StageResult.pending(StageResult.Stage.Blackout,"TENANT_REQUIRED_FOR_BLACKOUTS");
        var evidence=new LinkedHashMap<String,String>();evidence.put("snapshotChecksum",checksum());evidence.put("evaluatedAt",now.toString());
        Rule primary=null;int count=0,matches=0;
        for(var rule:rules) {
            var blackout=rule.blackout();if(blackout==null)continue;
            boolean window=blackout.windowMatches(now),scope=blackout.scopeMatches(event),match=window&&scope;
            String key="blackout."+(count++)+".";
            evidence.put(key+"id",rule.id());evidence.put(key+"version",Integer.toString(rule.version()));evidence.put(key+"checksum",rule.checksum());
            evidence.put(key+"windowMatch",Boolean.toString(window));evidence.put(key+"scopeMatch",Boolean.toString(scope));
            evidence.put(key+"timezone",blackout.timezone().getId());evidence.put(key+"validFrom",blackout.from().toString());
            evidence.put(key+"validTo",blackout.to()==null?"UNBOUNDED":blackout.to().toString());evidence.put(key+"reason",blackout.reason());
            evidence.put(key+"scope",new TreeMap<>(blackout.scope()).toString());
            if(match){matches++;if(primary==null)primary=rule;}
        }
        evidence.put("blackoutCount",Integer.toString(count));evidence.put("matchCount",Integer.toString(matches));
        return new StageResult(StageResult.Stage.Blackout,StageResult.Status.SUCCESS,
                primary==null?StageResult.Match.NO_MATCH:StageResult.Match.MATCH,
                primary==null?StageResult.Directive.CONTINUE:StageResult.Directive.SUPPRESS_INTEGRATIONS,
                primary==null?"NO_MATCHING_BLACKOUT":"BLACKOUT_MATCH",primary==null?null:primary.id(),primary==null?null:primary.version(),0,evidence);
    }
    public StageResult evaluate(Event event) {return evaluate(event,Map.of());}
    public StageResult evaluate(Event event,Map<String,Object> facts) {
        if(!tenant.equals(event.tenant())) throw new IllegalArgumentException("SNAPSHOT_TENANT_MISMATCH");
        if(tenant.isBlank()) return StageResult.pending(StageResult.Stage.PolicyEvaluation,"TENANT_REQUIRED_FOR_RULES");
        var evidence=new LinkedHashMap<String,String>(); evidence.put("snapshotChecksum",checksum());
        evidence.put("fieldContractVersion","policy-fields-v2");
        evidence.put("ruleCount",Long.toString(rules.stream().filter(r->r.capability().equals("POLICY")).count()));
        var directive=StageResult.Directive.CONTINUE; boolean matched=false; int i=0;
        for(var rule:rules) {
            if(!rule.capability().equals("POLICY"))continue;
            var trace=new ArrayList<String>(); boolean match;
            try { match=rule.condition().matches(event,facts,trace); }
            catch(IllegalArgumentException failure) {
                evidence.put("failedRuleId",rule.id());evidence.put("failedRuleVersion",Integer.toString(rule.version()));
                evidence.put("failedRuleChecksum",rule.checksum());evidence.put("errorCode","RULE_INPUT_LIMIT");
                return new StageResult(StageResult.Stage.PolicyEvaluation,StageResult.Status.FAILED,StageResult.Match.ERROR,
                        StageResult.Directive.DEAD_LETTER,"RULE_EVALUATION_FAILED",rule.id(),rule.version(),0,evidence);
            }
            String key="rule."+(i++)+".";
            evidence.put(key+"id",rule.id()); evidence.put(key+"version",Integer.toString(rule.version()));
            evidence.put(key+"checksum",rule.checksum()); evidence.put(key+"match",match?"MATCH":"NO_MATCH");
            evidence.put(key+"conditions",String.join(";",trace));
            if(match) { matched=true; for(var action:rule.actions()) directive=DirectiveResolver.combine(directive,action); }
            evidence.put(key+"proposals",match?rule.actions().toString():"[]");
        }
        evidence.put("resolvedDirective",directive.name());
        return new StageResult(StageResult.Stage.PolicyEvaluation,StageResult.Status.SUCCESS,
                matched?StageResult.Match.MATCH:StageResult.Match.NO_MATCH,directive,
                i==0?"NO_ACTIVE_RULES":"RULE_SNAPSHOT_EVALUATED",null,null,0,evidence);
    }
}
