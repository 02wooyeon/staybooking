package com.kisia.staybooking.mileage;

import java.util.List;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마일리지 잔액과 이력을 함께 바꾼다(docs/architecture/DATABASE.md의 mileage_history).
 *
 * <p>사용은 결제가 확정될 때만 차감한다. 예약을 만들 때 차감하면 결제하지 않고 버려진 예약이
 * 잔액을 영영 붙잡게 되는데, 만료된 예약을 되돌리는 흐름이 아직 없기 때문이다.
 * 그래서 예약의 {@code used_mileage}는 "쓰겠다는 요청"이고, 실제 차감은 결제 확정 시점에 일어난다.
 *
 * <p>예약당 사용(USE)과 복원(REFUND)은 각각 한 번만 기록한다. 같은 처리가 다시 들어와도 잔액은 한 번만 움직인다.
 */
@Service
public class MileageService {
    private final UserRepository users;
    private final MileageHistoryRepository history;

    public MileageService(UserRepository users, MileageHistoryRepository history) {
        this.users = users;
        this.history = history;
    }

    /**
     * 결제가 확정된 예약의 사용 마일리지를 차감한다. 잔액이 모자라면 결제를 막는다.
     * 호출하는 쪽(PaymentService)이 예약 행을 이미 잠그고 있는 트랜잭션 안에서만 쓴다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void useForBooking(BookingEntity booking) {
        long amount = booking.getUsedMileage();
        if (amount <= 0 || history.existsByBookingBookingIdAndType(booking.getBookingId(), MileageType.USE)) {
            return;
        }
        UserEntity user = lock(booking.getUser().getUserId());
        if (user.getMileageBalance() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "MILEAGE_BALANCE_SHORT",
                "보유 마일리지가 부족합니다. 예약을 다시 만들어 주세요.");
        }
        record(user, booking, MileageType.USE, -amount, "예약 " + booking.getBookingId() + " 결제 사용");
    }

    /**
     * 취소된 예약에 썼던 마일리지를 돌려준다. 결제 전에 취소돼 차감된 적이 없으면 아무것도 하지 않는다.
     * 금액은 예약이 아니라 실제 차감 기록을 기준으로 삼아, 예약 금액이 바뀌어도 더 돌려주지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForBooking(BookingEntity booking) {
        Long bookingId = booking.getBookingId();
        MileageHistoryEntity used = history.findFirstByBookingBookingIdAndType(bookingId, MileageType.USE)
            .orElse(null);
        if (used == null || history.existsByBookingBookingIdAndType(bookingId, MileageType.REFUND)) {
            return;
        }
        UserEntity user = lock(booking.getUser().getUserId());
        record(user, booking, MileageType.REFUND, -used.getAmount(), "예약 " + bookingId + " 취소 복원");
    }

    /** 관리자 수동 조정. 양수는 적립(EARN), 음수는 차감(USE)으로 남긴다. */
    @Transactional
    public MileageHistoryResponse adjust(Long userId, long amount, String description) {
        if (amount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "0이 아닌 금액을 입력해 주세요.", List.of("amount"));
        }
        UserEntity user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
        if (user.getMileageBalance() + amount < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MILEAGE_BALANCE_SHORT",
                "보유 마일리지보다 많이 차감할 수 없습니다.", List.of("amount"));
        }
        MileageType type = amount > 0 ? MileageType.EARN : MileageType.USE;
        return MileageHistoryResponse.from(record(user, null, type, amount, description));
    }

    @Transactional(readOnly = true)
    public PageResponse<MileageHistoryResponse> historyOf(Long userId, int page, int size) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        }
        Page<MileageHistoryEntity> result = history
            .findByUserUserIdOrderByCreatedAtDescMileageHistoryIdDesc(userId, PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(MileageHistoryResponse::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    private UserEntity lock(Long userId) {
        return users.findByIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private MileageHistoryEntity record(UserEntity user, BookingEntity booking, MileageType type,
                                        long amount, String description) {
        long balanceAfter = user.addMileage(amount);
        return history.save(new MileageHistoryEntity(user, booking, type, amount, balanceAfter, description));
    }
}
