package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.*;
import java.time.Instant;
import java.util.List;
public interface CommandOutbox extends CommandHistory {
    void enqueue(Event event,String processingId,Instant createdAt,List<ProcessingContext.CommandIntent> commands)throws Exception;
}
