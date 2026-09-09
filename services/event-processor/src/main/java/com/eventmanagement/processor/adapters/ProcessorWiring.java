package com.eventmanagement.processor.adapters;

import com.eventmanagement.processor.application.EventProcessingPipeline;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ProcessorWiring {
    @Produces @Singleton
    EventProcessingPipeline pipeline() { return EventProcessingPipeline.foundation(); }
}
