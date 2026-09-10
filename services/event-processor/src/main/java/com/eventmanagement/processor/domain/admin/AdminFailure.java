package com.eventmanagement.processor.domain.admin;

public final class AdminFailure extends RuntimeException {
    public enum Kind { INVALID, FORBIDDEN, NOT_FOUND, CONFLICT, UNAVAILABLE }
    private final Kind kind;
    public AdminFailure(Kind kind,String code) { super(code);this.kind=kind; }
    public Kind kind() { return kind; }
}
