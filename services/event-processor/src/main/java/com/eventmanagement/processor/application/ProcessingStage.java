package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.ProcessingContext;
import com.eventmanagement.processor.domain.StageResult;
public interface ProcessingStage {
    StageResult.Stage stage();
    StageResult evaluate(ProcessingContext context);
}
