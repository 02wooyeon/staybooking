package com.kisia.staybooking.accommodation;

import com.kisia.staybooking.room.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationRepository extends JpaRepository<AccommodationEntity, Long> {
    long countByStatus(AccommodationStatus status);
    // 키워드는 파라미터 바인딩으로만 넘긴다. pattern은 LIKE 특수문자를 이스케이프한 소문자 값이다.
    @Query("""
        select a from AccommodationEntity a
        where a.status = :status
          and (lower(a.name) like :pattern escape '!' or lower(a.address) like :pattern escape '!')
          and exists (select r from RoomEntity r
                      where r.accommodation = a and r.status = :roomStatus and r.capacity >= :guests)
        order by a.accommodationId
        """)
    Page<AccommodationEntity> search(@Param("status") AccommodationStatus status,
                                     @Param("pattern") String pattern,
                                     @Param("roomStatus") RoomStatus roomStatus,
                                     @Param("guests") int guests,
                                     Pageable pageable);
}
