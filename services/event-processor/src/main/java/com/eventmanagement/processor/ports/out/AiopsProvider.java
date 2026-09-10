package com.eventmanagement.processor.ports.out;
import com.eventmanagement.processor.domain.aiops.*;
public interface AiopsProvider {
    AiopsAssessment assess(String tenant, AiopsSignal signal);
}
