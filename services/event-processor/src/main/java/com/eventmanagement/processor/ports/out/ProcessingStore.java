package com.eventmanagement.processor.ports.out;

public interface ProcessingStore {
    /** Returns false for replay; a conflicting payload for the same ID is rejected. */
    boolean accept(String processingId, String inputHash, String eventId, String tenant,
                   String evidenceJson, String topic, String key, String outputJson) throws Exception;
}
