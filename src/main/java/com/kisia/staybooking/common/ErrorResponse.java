package com.kisia.staybooking.common;

import java.util.List;

public record ErrorResponse(String code, String message, List<String> fields) {
    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}
