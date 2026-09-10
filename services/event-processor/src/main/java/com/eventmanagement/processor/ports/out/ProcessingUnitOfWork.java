package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.Event;
public interface ProcessingUnitOfWork {
    @FunctionalInterface interface Work {void run(ProcessingStore store,CorrelationPort correlation,RuleSnapshots snapshots,CommandOutbox commands)throws Exception;}
    /** Skips an already accepted identical event before business state is evaluated. */
    void execute(Event event,String inputHash,Work work)throws Exception;
}
