package com.kisia.staybooking.qna;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/qna")
public class AdminQnaController {
    private final QnaService service;
    public AdminQnaController(QnaService service) { this.service = service; }

    @GetMapping
    public List<QnaResponse> all(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.allForAdmin(me.userId());
    }

    @PutMapping("/{qnaId}/answer")
    public QnaResponse answer(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long qnaId,
                              @Valid @RequestBody AnswerRequest request) {
        return service.answer(me.userId(), qnaId, request.content().trim());
    }

    @PatchMapping("/{qnaId}/hide")
    public QnaResponse hide(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long qnaId,
                            @Valid @RequestBody HideRequest request) {
        return service.hide(me.userId(), qnaId, request.reason().trim());
    }

    public record AnswerRequest(@NotBlank @Size(max = 2000) String content) { }
    public record HideRequest(@NotBlank @Size(max = 255) String reason) { }
}
