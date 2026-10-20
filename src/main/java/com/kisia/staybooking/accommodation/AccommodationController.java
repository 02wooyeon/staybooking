package com.kisia.staybooking.accommodation;

import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accommodations")
public class AccommodationController {
    private final AccommodationService service;

    public AccommodationController(AccommodationService service) {
        this.service = service;
    }

    // ACCOM_001 전체 목록. page, size 외의 조건은 무시한다.
    @GetMapping
    public PageResponse<AccommodationSummary> list(@Valid @ModelAttribute AccommodationSearchRequest request) {
        return service.search(new AccommodationSearchRequest(null, null, null, null,
            request.page(), request.size()));
    }

    // ACCOM_002 검색창: keyword는 숙소명 또는 주소에 포함된 글자
    @GetMapping("/search")
    public PageResponse<AccommodationSummary> search(@Valid @ModelAttribute AccommodationSearchRequest request) {
        return service.search(request);
    }

    // ACCOM_003 상세 + 객실. checkIn, checkOut을 주면 그 기간, 없으면 오늘 밤 기준 남은 객실 수를 준다.
    @GetMapping("/{accommodationId}")
    public AccommodationDetail detail(@PathVariable @Positive Long accommodationId,
                                      @Valid @ModelAttribute AccommodationSearchRequest request) {
        return service.detail(accommodationId, request);
    }
}
