package com.kisia.staybooking.user;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.auth.PasswordRules;
import com.kisia.staybooking.qna.QnaResponse;
import com.kisia.staybooking.qna.QnaService;
import com.kisia.staybooking.review.ReviewResponse;
import com.kisia.staybooking.review.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// URL에 회원번호를 받지 않고 토큰에서 꺼낸다. (IDOR 방지)
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final UserService users;
    private final ReviewService reviews;
    private final QnaService qna;

    public MemberController(UserService users, ReviewService reviews, QnaService qna) {
        this.users = users;
        this.reviews = reviews;
        this.qna = qna;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return users.me(me.userId());
    }

    @PatchMapping("/me")
    public MeResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                             @Valid @RequestBody UpdateRequest request) {
        return users.updateMe(me.userId(), request.nickname(), request.phone(), request.currentPassword(),
            request.newPassword());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody WithdrawRequest request) {
        users.withdraw(me.userId(), request.password());
    }

    // PROFILE_004: 내가 쓴 후기와 문의
    @GetMapping("/me/activities")
    public ActivitiesResponse activities(@AuthenticationPrincipal AuthenticatedUser me) {
        users.me(me.userId());
        return new ActivitiesResponse(reviews.mine(me.userId()), qna.mine(me.userId()));
    }

    // role, status, email 같은 필드는 받지 않는다. (Mass Assignment 방지)
    // 빈 문자열(nickname="", phone="")은 해당 값을 지우는 것으로 처리한다.
    public record UpdateRequest(
        @Size(max = 50) @Pattern(regexp = "^$|^[^<>\\p{Cntrl}]+$") String nickname,
        @Pattern(regexp = "^$|" + PasswordRules.PHONE_PATTERN) String phone,
        @Size(max = 100) String currentPassword,
        @Pattern(regexp = PasswordRules.PATTERN) String newPassword) { }

    public record WithdrawRequest(@NotBlank @Size(max = 100) String password,
                                  @NotNull @AssertTrue Boolean agree) { }

    public record ActivitiesResponse(List<ReviewResponse> reviews, List<QnaResponse> qna) { }
}
