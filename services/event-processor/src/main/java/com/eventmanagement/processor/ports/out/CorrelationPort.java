package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.correlation.*;
import java.util.Optional;
/** Production sessions serialize selection and persistence within the event transaction. */
public interface CorrelationPort {
    Optional<CorrelationGroup> load(String tenant,String key);
    void apply(String tenant,CorrelationResult result);
}
