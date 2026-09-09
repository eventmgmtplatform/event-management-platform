package com.eventmanagement.processor.domain.rules;

import com.eventmanagement.processor.domain.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Compiled values contain only immutable domain types. No JSON, reflection or provider IO. */
public record Rule(String id, int version, int priority, boolean enabled, String checksum,
                   Condition condition, List<StageResult.Directive> actions) {
    public Rule { actions = List.copyOf(actions); }
    public enum Operator { EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX, EXISTS, NOT_EXISTS, BETWEEN }
    public enum Field {
        IDENTIFIER("event.identifier", String.class), KEY("event.key", String.class),
        STATUS("event.status", String.class), SEVERITY("event.severity", BigDecimal.class),
        RECEIVED_AT("event.receivedAt", Instant.class), TENANT("tenant.customerCode", String.class);
        public final String path; public final Class<?> type;
        Field(String path, Class<?> type) { this.path=path; this.type=type; }
        public static Field from(String path) {
            return Arrays.stream(values()).filter(f->f.path.equals(path)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("UNKNOWN_FIELD"));
        }
        public Object resolve(Event event) {
            return switch(this) {
                case IDENTIFIER -> event.eventId(); case KEY -> event.eventKey();
                case STATUS -> event.status().name(); case SEVERITY -> BigDecimal.valueOf(event.severity());
                case RECEIVED_AT -> event.receivedAt(); case TENANT -> event.tenant();
            };
        }
    }
    public sealed interface Condition permits Group, Negation, Predicate {
        boolean matches(Event event, List<String> evidence);
    }
    public record Group(boolean all, List<Condition> children) implements Condition {
        public Group { children=List.copyOf(children); }
        public boolean matches(Event event, List<String> evidence) {
            boolean result=all;
            for(var child:children) { boolean next=child.matches(event,evidence); result=all ? result & next : result | next; }
            return result;
        }
    }
    public record Negation(Condition child) implements Condition {
        public boolean matches(Event event,List<String> evidence) { return !child.matches(event,evidence); }
    }
    public record Predicate(Field field, Operator operator, List<Object> values, Pattern regex) implements Condition {
        public Predicate { values=List.copyOf(values); }
        public boolean matches(Event event,List<String> evidence) {
            Object actual=field.resolve(event);
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
