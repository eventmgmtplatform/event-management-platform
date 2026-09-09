package com.eventmanagement.processor.ports.out;

import com.eventmanagement.processor.domain.rules.RuleSnapshot;

@FunctionalInterface
public interface RuleSnapshots {
    RuleSnapshot snapshot(String tenant);
}
