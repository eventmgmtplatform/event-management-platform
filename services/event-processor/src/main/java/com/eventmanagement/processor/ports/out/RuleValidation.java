package com.eventmanagement.processor.ports.out;

import com.eventmanagement.processor.domain.rules.Rule;

public interface RuleValidation {
    record Validated(Rule rule,String canonicalJson) {}
    Validated validate(String json);
}
