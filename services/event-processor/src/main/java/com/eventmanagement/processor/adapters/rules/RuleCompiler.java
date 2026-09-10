package com.eventmanagement.processor.adapters.rules;

import com.eventmanagement.processor.domain.rules.Rule;
import com.eventmanagement.processor.domain.StageResult.Directive;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Fixed local schema followed by a bounded semantic compiler. Errors never echo input values. */
public final class RuleCompiler implements com.eventmanagement.processor.ports.out.RuleValidation {
    private final ObjectMapper mapper;
    private final JsonSchema schema;
    private final JsonSchema blackoutSchema;
    public record Compiled(Rule rule, String canonicalJson) {}
    public RuleCompiler() {
        mapper=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(8192).maxNumberLength(64).build()).build());
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        try(var input=RuleCompiler.class.getResourceAsStream("/contracts/rule-v1.schema.json")) {
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(input));
            try(var blackout=RuleCompiler.class.getResourceAsStream("/contracts/blackout-v1.schema.json")) {
                blackoutSchema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(blackout));
            }
        } catch(Exception failure) { throw new IllegalStateException("RULE_SCHEMA_UNAVAILABLE",failure); }
    }
    @Override public com.eventmanagement.processor.ports.out.RuleValidation.Validated validate(String json) {
        var compiled=compile(json);
        return new com.eventmanagement.processor.ports.out.RuleValidation.Validated(compiled.rule(),compiled.canonicalJson());
    }
    public Compiled compile(String json) {
        if(json==null || json.length()>65536) throw invalid("RULE_SIZE_LIMIT");
        try {
            JsonNode source=mapper.readTree(json);
            bound(source,0,new int[]{0});
            if(Set.of("IMMEDIATE","SCHEDULED","RECURRING").contains(source.path("type").asText()))return blackout(source);
            if(!schema.validate(source).isEmpty()) throw invalid("RULE_SCHEMA_INVALID");
            String id=source.path("id").asText(); text(id,128);
            if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw invalid("INVALID_RULE_ID");
            if(!source.path("version").canConvertToInt() || !source.path("priority").canConvertToInt()) throw invalid("INTEGER_RANGE");
            if(!source.path("type").asText().equals("POLICY")) throw invalid("RULE_TYPE_NOT_IMPLEMENTED");
            var metadata=source.path("metadata");
            metadata.fieldNames().forEachRemaining(k->{if(!Set.of("owner","description","tags").contains(k))throw invalid("METADATA_NOT_SUPPORTED");});
            text(metadata.path("owner").asText(),128);
            if(metadata.has("description")) text(metadata.path("description").asText(),2048);
            if(metadata.has("tags")) { if(metadata.path("tags").size()>32)throw invalid("TAG_LIMIT"); for(var tag:metadata.path("tags"))text(tag.asText(),128); }
            var condition=condition(source.path("condition"));
            var actions=new ArrayList<Directive>();
            if(source.path("actions").isEmpty() || source.path("actions").size()>16)throw invalid("ACTION_COUNT");
            for(var action:source.path("actions")) {
                if(action.has("target") || (action.has("parameters") && !action.path("parameters").isEmpty())) throw invalid("ACTION_PARAMETERS_NOT_SUPPORTED");
                Directive directive;
                try { directive=Directive.valueOf(action.path("type").asText()); } catch(Exception e) { throw invalid("UNKNOWN_ACTION"); }
                if(!Set.of(Directive.CONTINUE,Directive.STATE_ONLY,Directive.SUPPRESS_INTEGRATIONS,Directive.CORRELATE_ONLY).contains(directive))
                    throw invalid("ACTION_NOT_IMPLEMENTED");
                if(!actions.contains(directive))actions.add(directive);
            }
            String canonical=mapper.writeValueAsString(sorted(source));
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new Compiled(new Rule(id,source.path("version").intValue(),source.path("priority").intValue(),
                    source.path("enabled").booleanValue(),checksum,condition,actions),canonical);
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw invalid("RULE_INVALID"); }
    }
    private Compiled blackout(JsonNode source)throws Exception {
        if(!blackoutSchema.validate(source).isEmpty())throw invalid("BLACKOUT_SCHEMA_INVALID");
        String type=source.path("type").asText(),id=source.path("id").asText();
        text(id,128);if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))throw invalid("INVALID_RULE_ID");
        if(!source.path("version").canConvertToInt() || !source.path("priority").canConvertToInt())throw invalid("INTEGER_RANGE");
        if(type.equals("RECURRING"))throw invalid("RECURRENCE_NOT_IMPLEMENTED");
        var schedule=source.path("schedule");
        if(schedule.hasNonNull("recurrence"))throw invalid("UNEXPECTED_RECURRENCE");
        String zone=schedule.path("timezone").asText();
        if(!java.time.ZoneId.getAvailableZoneIds().contains(zone))throw invalid("IANA_TIMEZONE_REQUIRED");
        // Requiring an explicit start also for immediate windows makes replay/activation independent of wall time.
        Instant from=Instant.parse(schedule.path("validFrom").asText());
        Instant to=schedule.hasNonNull("validTo")?Instant.parse(schedule.path("validTo").asText()):null;
        var scope=new TreeMap<String,String>();
        var selectors=source.path("scope");
        selectors.fields().forEachRemaining(e->{
            if(!Set.of("customerCode","node","nodeAlias","component","instanceId","monitoringSolution").contains(e.getKey()))
                throw invalid("BLACKOUT_SELECTOR_NOT_IMPLEMENTED");
            String value=e.getValue().asText();text(value,4096);scope.put(e.getKey(),value);
        });
        text(source.path("reason").asText(),2048);text(source.path("metadata").path("owner").asText(),128);
        source.path("metadata").fieldNames().forEachRemaining(k->{if(!Set.of("owner","externalReference").contains(k))throw invalid("METADATA_NOT_SUPPORTED");});
        if(source.path("metadata").hasNonNull("externalReference"))text(source.path("metadata").path("externalReference").asText(),512);
        var blackout=new com.eventmanagement.processor.domain.rules.Blackout(type,scope,from,to,java.time.ZoneId.of(zone),source.path("reason").asText());
        String canonical=mapper.writeValueAsString(sorted(source));
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        return new Compiled(new Rule(id,source.path("version").intValue(),source.path("priority").intValue(),source.path("enabled").booleanValue(),
                checksum,new Rule.Group(true,List.of()),List.of(Directive.SUPPRESS_INTEGRATIONS),blackout),canonical);
    }
    private void bound(JsonNode node,int depth,int[] count) {
        if(node==null || depth>32 || ++count[0]>1024)throw invalid("RULE_COMPLEXITY_LIMIT");
        if(node.isContainerNode())for(var child:node)bound(child,depth+1,count);
    }
    private Rule.Condition condition(JsonNode node) {
        if(node.has("all") || node.has("any")) {
            boolean all=node.has("all");var children=new ArrayList<Rule.Condition>();
            for(var child:node.path(all?"all":"any"))children.add(condition(child));
            return new Rule.Group(all,children);
        }
        if(node.has("not"))return new Rule.Negation(condition(node.path("not")));
        var field=Rule.Field.from(node.path("field").asText());
        var operator=Rule.Operator.valueOf(node.path("operator").asText());
        var values=new ArrayList<Object>(); Pattern regex=null;
        if(operator==Rule.Operator.EXISTS || operator==Rule.Operator.NOT_EXISTS) {
            if(node.has("value"))throw invalid("UNEXPECTED_VALUE");
        } else {
            var value=node.get("value");
            if(value==null || value.isNull())throw invalid("VALUE_REQUIRED");
            if(Set.of(Rule.Operator.IN,Rule.Operator.NOT_IN,Rule.Operator.BETWEEN).contains(operator)) {
                if(!value.isArray() || value.isEmpty() || value.size()>128)throw invalid("COLLECTION_REQUIRED");
                for(var v:value)values.add(value(field,v));
            } else values.add(value(field,value));
            if(Set.of(Rule.Operator.GT,Rule.Operator.GTE,Rule.Operator.LT,Rule.Operator.LTE,Rule.Operator.BETWEEN).contains(operator)) {
                if(field.type==String.class)throw invalid("ORDERED_TYPE_REQUIRED");
                if(operator==Rule.Operator.BETWEEN && (values.size()!=2 || Rule.compare(values.get(0),values.get(1))>0))throw invalid("INVALID_RANGE");
            }
            if(Set.of(Rule.Operator.CONTAINS,Rule.Operator.STARTS_WITH,Rule.Operator.ENDS_WITH,Rule.Operator.REGEX).contains(operator)) {
                if(field.type!=String.class)throw invalid("STRING_REQUIRED");
                if(operator==Rule.Operator.REGEX) regex=safeRegex((String)values.getFirst());
            }
        }
        return new Rule.Predicate(field,operator,values,regex);
    }
    private Object value(Rule.Field field,JsonNode value) {
        if(field.type==BigDecimal.class) { if(!value.isNumber())throw invalid("NUMBER_REQUIRED"); return value.decimalValue(); }
        if(!value.isTextual())throw invalid("STRING_REQUIRED");
        String text=value.textValue(); if(text.length()>4096)throw invalid("VALUE_SIZE_LIMIT");
        if(field.type==Instant.class) { try{return Instant.parse(text);}catch(Exception e){throw invalid("INSTANT_REQUIRED");} }
        return text;
    }
    private Pattern safeRegex(String value) {
        // Full-string matching, no groups/alternation/backreferences, at most one quantifier.
        // Single quantified atom + bounded input avoids nested or combinatorial backtracking.
        if(value.length()>128 || value.chars().anyMatch(c -> "(){}|\\".indexOf(c)>=0))throw invalid("UNSAFE_REGEX");
        long quantifiers=value.chars().filter(c->c=='*'||c=='+'||c=='?').count();
        if(quantifiers>1)throw invalid("UNSAFE_REGEX");
        try{return Pattern.compile(value);}catch(Exception e){throw invalid("INVALID_REGEX");}
    }
    private JsonNode sorted(JsonNode node) {
        if(node.isObject()) { ObjectNode result=mapper.createObjectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);for(String key:keys)result.set(key,sorted(node.get(key)));return result; }
        if(node.isArray()) { ArrayNode result=mapper.createArrayNode();for(var child:node)result.add(sorted(child));return result; }
        if(node.isNumber()) return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }
    private static void text(String value,int max) { if(value.isBlank()||value.length()>max)throw invalid("INVALID_TEXT"); }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
