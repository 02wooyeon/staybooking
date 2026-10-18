package com.kisia.staybooking.common;

import java.util.List;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<String> fields;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = List.copyOf(fields);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<String> getFields() { return fields; }
}
