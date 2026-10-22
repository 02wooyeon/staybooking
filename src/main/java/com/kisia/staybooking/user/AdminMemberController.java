package com.kisia.staybooking.user;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
  private final UserRepository users;
  public AdminMemberController(UserRepository users) { this.users = users; }
  @GetMapping public List<MemberSummary> list() { return users.findAll().stream().map(MemberSummary::from).toList(); }
  @PatchMapping("/{userId}/status") public MemberSummary changeStatus(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long userId,@Valid @RequestBody StatusRequest request) {
    if (me.userId().equals(userId) && request.status() != UserStatus.ACTIVE) throw new IllegalStateException("Administrator cannot suspend own account");
    UserEntity user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("Member not found")); user.changeStatus(request.status()); return MemberSummary.from(user);
  }
  public record StatusRequest(@NotNull UserStatus status) {}
  public record MemberSummary(Long userId,String loginId,String name,String nickname,String email,UserRole role,UserStatus status,long mileageBalance) { static MemberSummary from(UserEntity u) { return new MemberSummary(u.getUserId(),u.getLoginId(),u.getName(),u.getNickname(),u.getEmail(),u.getRole(),u.getStatus(),u.getMileageBalance()); } }
}
