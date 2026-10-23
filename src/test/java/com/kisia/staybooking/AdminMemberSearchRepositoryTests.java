package com.kisia.staybooking;

import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class AdminMemberSearchRepositoryTests {
    @Autowired private UserRepository users;

    @Test
    void filtersByAccountRoleStatusAndPaginatesAllMembers() {
        UserEntity member = users.save(new UserEntity("member1", "hash", "Member", "member1@example.test"));
        UserEntity approver = users.save(new UserEntity("approver1", "hash", "Approver", null,
            "approver1@example.test", null, UserRole.ROLE_APPROVER));
        UserEntity suspended = new UserEntity("member2", "hash", "Member", "member2@example.test");
        suspended.changeStatus(UserStatus.SUSPENDED);
        users.save(suspended);

        assertEquals(3, users.searchForAdmin(null, null, null, null, PageRequest.of(0, 2)).getTotalElements());
        assertEquals(2, users.searchForAdmin(null, null, null, null, PageRequest.of(0, 2)).getContent().size());
        assertEquals(1, users.searchForAdmin(null, null, null, null, PageRequest.of(1, 2)).getContent().size());
        assertEquals(approver.getUserId(), users.searchForAdmin(null, UserRole.ROLE_APPROVER,
            UserStatus.ACTIVE, null, PageRequest.of(0, 10)).getContent().getFirst().getUserId());
        assertEquals(member.getUserId(), users.searchForAdmin(null, UserRole.ROLE_USER,
            UserStatus.ACTIVE, "MEMBER1@EXAMPLE.TEST", PageRequest.of(0, 10))
            .getContent().getFirst().getUserId());
        assertEquals(1, users.searchForAdmin(suspended.getUserId(), null, UserStatus.SUSPENDED,
            null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, users.searchForAdmin(suspended.getUserId(), null, UserStatus.ACTIVE,
            null, PageRequest.of(0, 10)).getTotalElements());
    }
}
