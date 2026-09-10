package com.eventmanagement.processor.application;
import com.eventmanagement.processor.ports.out.InventoryPort;
import com.eventmanagement.processor.domain.Event;
import com.eventmanagement.processor.domain.rules.RuleSnapshot;
import com.eventmanagement.processor.domain.enrichment.EnrichmentResult.LookupStatus;
/** Local authoritative inventory loaded by the same PostgreSQL snapshot as processing rules. */
public final class SnapshotInventory implements InventoryPort {
    private final RuleSnapshot snapshot;
    public SnapshotInventory(RuleSnapshot snapshot){this.snapshot=snapshot;}
    public Result lookup(Event event) {
        if(!snapshot.tenant().equals(event.tenant()))throw new IllegalArgumentException("INVENTORY_TENANT_MISMATCH");
        var records=snapshot.rules().stream().filter(r->r.inventory()!=null && r.inventory().matches(event))
                .map(r->new InventoryPort.Record(r.id(),r.version(),r.priority(),r.checksum(),r.inventory().facts())).toList();
        return new Result(records.isEmpty()?LookupStatus.NOT_FOUND:LookupStatus.FOUND,records,null);
    }
}
