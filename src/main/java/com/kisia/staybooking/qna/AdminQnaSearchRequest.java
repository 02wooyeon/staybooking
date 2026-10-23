package com.kisia.staybooking.qna;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminQnaSearchRequest(
    @Positive Long userId,
    Boolean answered,
    Boolean deleted,
    @Size(max = 200) String q,
    @Min(0) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer size
) { }
