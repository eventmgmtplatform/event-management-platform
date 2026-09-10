package com.eventmanagement.processor.domain.enrichment;
import java.util.*;
import java.time.Instant;
/** Canonical DA-05 result. Values are validated immutable scalars. */
public record EnrichmentResult(Status status,Map<String,Object> facts,List<Lookup> lookups,
        List<Provenance> provenance,List<Conflict> conflicts,boolean degraded) {
    public enum Status { SUCCESS, PARTIAL, NOT_FOUND, FAILED }
    public enum LookupStatus { FOUND, NOT_FOUND, ERROR, SKIPPED }
    public record Lookup(String source,LookupStatus status,boolean required,String errorCode,String planId,int planVersion) {}
    public record Provenance(String field,String source,String sourceVersion,Instant observedAt,String checksum) {}
    public record Conflict(String field,String selectedSource,String rejectedSource,String resolution) {}
    public EnrichmentResult {
        facts=Map.copyOf(facts);lookups=List.copyOf(lookups);provenance=List.copyOf(provenance);conflicts=List.copyOf(conflicts);
    }
    public static EnrichmentResult empty(){return new EnrichmentResult(Status.NOT_FOUND,Map.of(),List.of(),List.of(),List.of(),false);}
}
