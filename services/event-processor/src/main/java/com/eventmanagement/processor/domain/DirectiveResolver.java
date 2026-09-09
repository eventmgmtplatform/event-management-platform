package com.eventmanagement.processor.domain;

import static com.eventmanagement.processor.domain.StageResult.Directive;

/** A restrictive business result never hides a subsequent technical failure. */
public final class DirectiveResolver {
    private DirectiveResolver() {}
    public static Directive combine(Directive first, Directive second) {
        return rank(first) >= rank(second) ? first : second;
    }
    private static int rank(Directive value) {
        return switch(value) {
            case DEAD_LETTER -> 6;
            case STATE_ONLY -> 5;
            case SUPPRESS_INTEGRATIONS -> 4;
            case CORRELATE_ONLY -> 3;
            case GENERATE_COMMANDS -> 2;
            case CONTINUE -> 1;
        };
    }
}
