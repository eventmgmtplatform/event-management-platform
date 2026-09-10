package com.eventmanagement.processor.adapters.http;

public final class ApiFailure extends RuntimeException {
    private final int status;
    public ApiFailure(int status,String code){super(code);this.status=status;}
    public int status(){return status;}
}
