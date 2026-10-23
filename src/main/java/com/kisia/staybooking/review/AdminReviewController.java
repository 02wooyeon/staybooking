package com.kisia.staybooking.review;

import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/v1/admin/** 는 SecurityConfig에서 ADMIN만 들어올 수 있다.
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {
    private final ReviewService reviews;

    public AdminReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public PageResponse<AdminReviewResponse> list(@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.allForAdmin(page, size);
    }

    // 부적절한 후기 강제 삭제(ADM-14). 실제로 지우지 않고 숨기며 사유를 남긴다.
    @DeleteMapping("/{reviewId}")
    public AdminReviewResponse hide(@PathVariable @Positive Long reviewId,
                                    @RequestParam @NotBlank @Size(max = 255) String reason) {
        return reviews.hideByAdmin(reviewId, reason.trim());
    }
}
