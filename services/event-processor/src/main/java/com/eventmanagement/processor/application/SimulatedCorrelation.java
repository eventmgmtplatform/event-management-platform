package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.correlation.*;
import com.eventmanagement.processor.ports.out.CorrelationPort;
import java.util.*;
/** Request-scoped simulation state only. Never used as durable production authority. */
public final class SimulatedCorrelation implements CorrelationPort {
    private final Map<String,CorrelationGroup> groups=new HashMap<>();
    private String key(String tenant,String key){return tenant+"/"+key;}
    public Optional<CorrelationGroup> load(String tenant,String key){return Optional.ofNullable(groups.get(key(tenant,key)));}
    public void apply(String tenant,CorrelationResult result){if(!result.failed())for(var d:result.decisions())if(d.changed())groups.put(key(tenant,d.group().key()),d.group());}
}
