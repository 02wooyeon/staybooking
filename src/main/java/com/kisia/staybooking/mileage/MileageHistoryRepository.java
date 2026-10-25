package com.kisia.staybooking.mileage;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileageHistoryRepository extends JpaRepository<MileageHistoryEntity, Long> {
    Page<MileageHistoryEntity> findByUserUserIdOrderByCreatedAtDescMileageHistoryIdDesc(Long userId,
                                                                                        Pageable pageable);

    // 예약당 사용·복원은 각각 한 번만 기록한다. 같은 요청이 다시 와도 잔액이 두 번 움직이지 않게 하는 기준이다.
    Optional<MileageHistoryEntity> findFirstByBookingBookingIdAndType(Long bookingId, MileageType type);

    boolean existsByBookingBookingIdAndType(Long bookingId, MileageType type);
}
