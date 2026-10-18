package com.kisia.staybooking.user;

import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    // PROFILE_001. 토큰의 회원번호로만 조회하므로 다른 회원 정보는 볼 수 없다.
    // 탈퇴·정지된 계정은 토큰이 남아 있어도 로그인하지 않은 것으로 취급한다.
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        UserEntity user = users.findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
        return new MeResponse(user.getUserId(), user.getEmail(), user.getName(), user.getNickname(),
            user.getPhone(), user.getRole(), user.getMileageBalance());
    }
}
