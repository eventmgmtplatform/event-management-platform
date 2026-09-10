package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.Event;
import com.eventmanagement.processor.domain.enrichment.EnrichmentResult.LookupStatus;
import java.util.*;
/** Inventory source boundary. Implementations return bounded candidates; no invented defaults. */
public interface InventoryPort {
    record Record(String id,int version,int priority,String checksum,Map<String,Object> facts) {
        public Record {facts=new com.eventmanagement.processor.domain.enrichment.InventoryRecord(Map.of(),facts).facts();}
    }
    record Result(LookupStatus status,List<Record> records,String errorCode) {
        public Result {records=List.copyOf(records);Objects.requireNonNull(status);
            if((status==LookupStatus.FOUND)!=(!records.isEmpty()))throw new IllegalArgumentException("INVENTORY_RESULT_STATUS");
            if(records.size()>256)throw new IllegalArgumentException("INVENTORY_LIMIT");}
    }
    Result lookup(Event event);
}
