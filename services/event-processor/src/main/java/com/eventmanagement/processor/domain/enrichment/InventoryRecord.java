package com.eventmanagement.processor.domain.enrichment;
import com.eventmanagement.processor.domain.Event;
import java.util.Map;
/** Versioned local inventory facts, distinct from observations carried by the incoming event. */
public record InventoryRecord(Map<String,String> scope,Map<String,Object> facts) {
    public InventoryRecord {scope=Map.copyOf(scope);facts=Map.copyOf(facts);
        if(facts.isEmpty() || facts.size()>7)throw new IllegalArgumentException("INVENTORY_FACT_COUNT");
        for(var entry:facts.entrySet()) {
            var field=com.eventmanagement.processor.domain.rules.Rule.Field.from("enrichment."+entry.getKey());
            if(!field.type.isInstance(entry.getValue()))throw new IllegalArgumentException("INVENTORY_FACT_TYPE");
            if(entry.getValue() instanceof String text && (text.isBlank() || text.length()>4096))throw new IllegalArgumentException("INVENTORY_FACT_TEXT");
        }}
    public boolean matches(Event event) {
        return scope.entrySet().stream().allMatch(e->e.getValue().equals(e.getKey().equals("customerCode")?event.tenant():event.selectors().get(e.getKey())));
    }
}
