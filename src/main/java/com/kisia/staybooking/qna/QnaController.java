package com.kisia.staybooking.qna;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qna")
public class QnaController {
    private final QnaService service;
    public QnaController(QnaService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QnaResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                              @Valid @RequestBody CreateRequest request) {
        return service.create(me.userId(), request.bookingId(), request.category(),
            request.title().trim(), request.content().trim(), request.secret());
    }

    @PutMapping("/{qnaId}")
    public QnaResponse update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long qnaId,
                              @Valid @RequestBody CreateRequest request) {
        return service.update(me.userId(), qnaId, request.bookingId(), request.category(),
            request.title().trim(), request.content().trim(), request.secret());
    }

    @DeleteMapping("/{qnaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long qnaId) {
        service.delete(me.userId(), qnaId);
    }

    @GetMapping("/mine")
    public List<QnaResponse> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.mine(me.userId());
    }

    @GetMapping("/public")
    public List<QnaResponse> publicQuestions() { return service.publicQuestions(); }

    @GetMapping("/{qnaId}")
    public QnaResponse detail(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long qnaId) {
        return service.detail(me == null ? null : me.userId(), qnaId);
    }

    public record CreateRequest(
        @NotNull @Pattern(regexp = "booking|payment|refund|account|etc") String category,
        Long bookingId,
        @NotBlank @Size(min = 2, max = 100) String title,
        @NotBlank @Size(min = 10, max = 2000) String content,
        boolean secret) { }
}
