package com.kisia.staybooking.room;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.kisia.staybooking.accommodation.AccommodationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    long countByStatus(RoomStatus status);
    @Query("select count(r) from RoomEntity r where r.status = :roomStatus "
        + "and r.accommodation.status = :accommodationStatus")
    long countByStatusAndAccommodationStatus(@Param("roomStatus") RoomStatus roomStatus,
                                             @Param("accommodationStatus") AccommodationStatus accommodationStatus);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r join fetch r.accommodation where r.roomId = :roomId")
    Optional<RoomEntity> findByIdForUpdate(@Param("roomId") Long roomId);

    List<RoomEntity> findByAccommodationAccommodationIdInAndStatusOrderByPriceAsc(
        Collection<Long> accommodationIds, RoomStatus status);
    List<RoomEntity> findByAccommodationAccommodationIdOrderByRoomIdAsc(Long accommodationId);
}
