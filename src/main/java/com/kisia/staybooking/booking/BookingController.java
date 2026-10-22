package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookings;
    private final BookingQueryService queries;
    private final CancellationRequestService cancellationRequests;

    public BookingController(BookingService bookings, BookingQueryService queries,
                             CancellationRequestService cancellationRequests) {
        this.bookings = bookings;
        this.queries = queries;
        this.cancellationRequests = cancellationRequests;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                  @Valid @RequestBody BookingCreateRequest request) {
        return BookingResponse.from(bookings.create(me.userId(), request.roomId(), request.checkIn(),
            request.checkOut(), request.guests()));
    }

    // PAYMENT_006 예약 예상 요금. 요청에 금액 필드는 없다. (BookingCreateRequest와 같은 입력)
    @PostMapping("/quote")
    public BookingQuoteResponse quote(@AuthenticationPrincipal AuthenticatedUser me,
                                      @Valid @RequestBody BookingCreateRequest request) {
        return bookings.quote(me.userId(), request.roomId(), request.checkIn(), request.checkOut(),
            request.guests());
    }

    @GetMapping("/me")
    public List<BookingResponse> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return queries.mine(me.userId());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse detail(@AuthenticationPrincipal AuthenticatedUser me,
                                  @PathVariable @Positive Long bookingId) {
        return queries.mineDetail(me.userId(), bookingId);
    }

    @PostMapping("/{bookingId}/cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CancellationRequestResponse requestCancellation(@AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable @Positive Long bookingId, @Valid @RequestBody CancellationRequestCreateRequest request) {
        CancellationRequestEntity created = cancellationRequests.request(me.userId(), bookingId,
            request.reasonType(), request.reasonDetail());
        return CancellationRequestResponse.from(created);
    }

    public record CancellationRequestCreateRequest(@NotNull CancellationReasonType reasonType,
                                                   @NotBlank String reasonDetail) { }

    public record CancellationRequestResponse(Long cancellationRequestId, Long bookingId, String status) {
        static CancellationRequestResponse from(CancellationRequestEntity request) {
            return new CancellationRequestResponse(request.getCancellationRequestId(),
                request.getBooking().getBookingId(), request.getStatus().name());
        }
    }
}
