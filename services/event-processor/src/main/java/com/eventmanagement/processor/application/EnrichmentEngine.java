package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.domain.enrichment.*;
import com.eventmanagement.processor.ports.out.InventoryPort;
import java.time.Instant;
import java.util.*;
import static com.eventmanagement.processor.domain.enrichment.EnrichmentResult.*;
/** Evaluates each lookup contract, then resolves facts by explicit inventory priority and stable ID. */
public final class EnrichmentEngine {
    public EnrichmentResult evaluate(Event event,RuleSnapshot snapshot,Instant now,InventoryPort inventory) {
        var lookups=new ArrayList<Lookup>();var provenance=new ArrayList<Provenance>();var conflicts=new ArrayList<Conflict>();
        var facts=new TreeMap<String,Object>();var selected=new HashMap<String,String>();
        boolean failed=false,degraded=false;InventoryPort.Result result=null;
        for(var plan:snapshot.rules()) {
            if(plan.enrichmentPlan()==null)continue;
            boolean required=plan.enrichmentPlan().required();boolean applies;
            try {applies=plan.condition().matches(event,new ArrayList<>());}
            catch(IllegalArgumentException invalid){lookups.add(new Lookup("inventory",LookupStatus.ERROR,required,"ENRICHMENT_CONDITION_FAILED",plan.id(),plan.version()));failed=true;continue;}
            if(!applies){lookups.add(new Lookup("inventory",LookupStatus.SKIPPED,required,null,plan.id(),plan.version()));continue;}
            if(result==null) {
                try {result=inventory.lookup(event);}
                catch(RuntimeException unavailable){result=new InventoryPort.Result(LookupStatus.ERROR,List.of(),"INVENTORY_UNAVAILABLE");}
                if(result.status()==LookupStatus.FOUND && result.records().isEmpty())result=new InventoryPort.Result(LookupStatus.ERROR,List.of(),"INVENTORY_INVALID_RESULT");
            }
            lookups.add(new Lookup("inventory",result.status(),required,result.errorCode(),plan.id(),plan.version()));
            if(required && result.status()!=LookupStatus.FOUND)failed=true;
            if(result.status()==LookupStatus.ERROR)degraded=true;
        }
        if(result!=null && result.status()==LookupStatus.FOUND) {
            var ordered=result.records().stream().sorted(Comparator.comparingInt(InventoryPort.Record::priority).reversed().thenComparing(InventoryPort.Record::id).thenComparingInt(InventoryPort.Record::version)).toList();
            for(var record:ordered)for(var entry:new TreeMap<>(record.facts()).entrySet()) {
                String field=entry.getKey(),source=record.id()+":"+record.version();
                if(!facts.containsKey(field)) {
                    facts.put(field,entry.getValue());selected.put(field,source);
                    provenance.add(new Provenance(field,"inventory:"+record.id(),Integer.toString(record.version()),now,record.checksum()));
                }else if(!facts.get(field).equals(entry.getValue()))conflicts.add(new Conflict(field,selected.get(field),source,"PRIORITY_DESC_ID_VERSION_ASC"));
            }
        }
        return new EnrichmentResult(failed?Status.FAILED:degraded?Status.PARTIAL:facts.isEmpty()?Status.NOT_FOUND:Status.SUCCESS,
                facts,lookups,provenance,conflicts,degraded);
    }
    public StageResult stage(EnrichmentResult result,String checksum) {
        boolean failed=result.status()==Status.FAILED;
        return new StageResult(StageResult.Stage.ContextEnrichment,failed?StageResult.Status.FAILED:StageResult.Status.SUCCESS,
                failed?StageResult.Match.ERROR:result.facts().isEmpty()?StageResult.Match.NO_MATCH:StageResult.Match.MATCH,
                failed?StageResult.Directive.DEAD_LETTER:StageResult.Directive.CONTINUE,
                failed?"ENRICHMENT_REQUIRED_LOOKUP_FAILED":"ENRICHMENT_"+result.status(),null,null,0,
                Map.of("snapshotChecksum",checksum,"lookupCount",Integer.toString(result.lookups().size()),"factCount",Integer.toString(result.facts().size()),
                        "conflictCount",Integer.toString(result.conflicts().size()),"degraded",Boolean.toString(result.degraded())));
    }
}
