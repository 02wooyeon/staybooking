package com.kisia.staybooking.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final String INVALID_INPUT = "INVALID_INPUT";
    private static final String INVALID_INPUT_MESSAGE = "입력값이 올바르지 않습니다.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
            .body(new ErrorResponse(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(INVALID_INPUT, INVALID_INPUT_MESSAGE));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("INVALID_STATE", "현재 상태에서는 요청을 처리할 수 없습니다."));
    }

    // 어떤 규칙에 걸렸는지는 숨기고 문제가 된 필드 이름만 알려준다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .sorted()
            .toList();
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(INVALID_INPUT, INVALID_INPUT_MESSAGE, fields));
    }

    // 본문 JSON 형식 오류, 경로 변수 타입 오류(/accommodations/abc), 경로 변수 검증 실패
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(INVALID_INPUT, INVALID_INPUT_MESSAGE));
    }
}
