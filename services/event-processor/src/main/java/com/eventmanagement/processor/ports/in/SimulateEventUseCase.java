package com.eventmanagement.processor.ports.in;
import com.eventmanagement.processor.domain.Event;
import com.eventmanagement.processor.domain.ProcessingContext;
public interface SimulateEventUseCase { ProcessingContext simulate(Event event); }
