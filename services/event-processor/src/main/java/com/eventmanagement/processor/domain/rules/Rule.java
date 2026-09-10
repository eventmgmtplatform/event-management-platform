package com.eventmanagement.processor.domain.rules;

import com.eventmanagement.processor.domain.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Compiled values contain only immutable domain types. No JSON, reflection or provider IO. */
public record Rule(String id, int version, int priority, boolean enabled, String checksum,
                   Condition condition, List<StageResult.Directive> actions, Blackout blackout, com.eventmanagement.processor.domain.enrichment.EnrichmentPlan enrichmentPlan,
                   com.eventmanagement.processor.domain.enrichment.InventoryRecord inventory) {
    public Rule(String id,int version,int priority,boolean enabled,String checksum,Condition condition,List<StageResult.Directive> actions) {
        this(id,version,priority,enabled,checksum,condition,actions,null,null,null);
    }
    public Rule(String id,int version,int priority,boolean enabled,String checksum,Condition condition,List<StageResult.Directive> actions,Blackout blackout) {
        this(id,version,priority,enabled,checksum,condition,actions,blackout,null,null);
    }
    public String capability(){return blackout!=null?"BLACKOUT":inventory!=null?"INVENTORY":enrichmentPlan!=null?"ENRICHMENT":"POLICY";}
    public Rule { actions = List.copyOf(actions);
        if((blackout==null?0:1)+(inventory==null?0:1)+(enrichmentPlan==null?0:1)>1)throw new IllegalArgumentException("ONE_RULE_CAPABILITY_REQUIRED");
    }
    public enum Operator { EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX, EXISTS, NOT_EXISTS, BETWEEN }
    public enum Field {
        IDENTIFIER("event.identifier", String.class), KEY("event.key", String.class),
        STATUS("event.status", String.class), SEVERITY("event.severity", BigDecimal.class),
        RECEIVED_AT("event.receivedAt", Instant.class), TENANT("tenant.customerCode", String.class),
        NODE("resource.node",String.class), COMPONENT("resource.component",String.class),
        CI_ID("enrichment.resource.ciId",String.class), SERVICE("enrichment.service.name",String.class),
        GROUP("enrichment.assignment.group",String.class), SITE("enrichment.location.site",String.class),
        CLASS("enrichment.resource.class",String.class), MANAGED("enrichment.resource.managed",Boolean.class),
        CRITICALITY("enrichment.service.criticality",BigDecimal.class);
        public final String path; public final Class<?> type;
        Field(String path, Class<?> type) { this.path=path; this.type=type; }
        public static Field from(String path) {
            return Arrays.stream(values()).filter(f->f.path.equals(path)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("UNKNOWN_FIELD"));
        }
        public Object resolve(Event event,Map<String,Object> facts) {
            return switch(this) {
                case IDENTIFIER -> event.eventId(); case KEY -> event.eventKey();
                case STATUS -> event.status().name(); case SEVERITY -> BigDecimal.valueOf(event.severity());
                case RECEIVED_AT -> event.receivedAt(); case TENANT -> event.tenant();
                case NODE -> event.selectors().get("node");case COMPONENT -> event.selectors().get("component");
                default -> facts.get(path.substring("enrichment.".length()));
            };
        }
    }
    public sealed interface Condition permits Group, Negation, Predicate {
        default boolean matches(Event event,List<String> evidence){return matches(event,Map.of(),evidence);}
        boolean matches(Event event,Map<String,Object> facts,List<String> evidence);
    }
    public record Group(boolean all, List<Condition> children) implements Condition {
        public Group { children=List.copyOf(children); }
        public boolean matches(Event event,Map<String,Object> facts,List<String> evidence) {
            boolean result=all;
            for(var child:children) { boolean next=child.matches(event,facts,evidence); result=all ? result & next : result | next; }
            return result;
        }
    }
    public record Negation(Condition child) implements Condition {
        public boolean matches(Event event,Map<String,Object> facts,List<String> evidence) { return !child.matches(event,facts,evidence); }
    }
    public record Predicate(Field field, Operator operator, List<Object> values, Pattern regex) implements Condition {
        public Predicate { values=List.copyOf(values); }
        public boolean matches(Event event,Map<String,Object> facts,List<String> evidence) {
            Object actual=field.resolve(event,facts);
            boolean result;
            if(operator==Operator.EXISTS) result=actual!=null;
            else if(operator==Operator.NOT_EXISTS) result=actual==null;
            else if(actual==null) result=false;
            else result=switch(operator) {
                case EQ -> compare(actual,values.getFirst())==0;
                case NE -> compare(actual,values.getFirst())!=0;
                case GT -> compare(actual,values.getFirst())>0;
                case GTE -> compare(actual,values.getFirst())>=0;
                case LT -> compare(actual,values.getFirst())<0;
                case LTE -> compare(actual,values.getFirst())<=0;
                case IN -> values.stream().anyMatch(v->compare(actual,v)==0);
                case NOT_IN -> values.stream().noneMatch(v->compare(actual,v)==0);
                case BETWEEN -> compare(actual,values.get(0))>=0 && compare(actual,values.get(1))<=0;
                case CONTAINS -> ((String)actual).contains((String)values.getFirst());
                case STARTS_WITH -> ((String)actual).startsWith((String)values.getFirst());
                case ENDS_WITH -> ((String)actual).endsWith((String)values.getFirst());
                case REGEX -> {
                    if(((String)actual).length()>4096) throw new IllegalArgumentException("REGEX_INPUT_LIMIT");
                    yield regex.matcher((String)actual).matches();
                }
                default -> throw new IllegalStateException("INVALID_OPERATOR");
            };
            evidence.add(field.path+":"+operator+":"+(actual==null?"MISSING":result?"MATCH":"NO_MATCH"));
            return result;
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object a,Object b) { return ((Comparable)a).compareTo(b); }
}
