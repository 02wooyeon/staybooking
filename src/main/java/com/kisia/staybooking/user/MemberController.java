package com.kisia.staybooking.user;

import com.kisia.staybooking.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// URL에 회원번호를 받지 않고 토큰에서 꺼낸다. (IDOR 방지)
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final UserService users;

    public MemberController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return users.me(me.userId());
    }
}
