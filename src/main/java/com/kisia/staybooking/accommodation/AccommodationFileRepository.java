package com.kisia.staybooking.accommodation;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccommodationFileRepository extends JpaRepository<AccommodationFileEntity, AccommodationFileId> {
    @Query("select af from AccommodationFileEntity af join fetch af.file "
        + "where af.accommodation.accommodationId = :id order by af.displayOrder, af.file.fileId")
    List<AccommodationFileEntity> findImages(@Param("id") Long accommodationId);

    @Query("select af from AccommodationFileEntity af where af.accommodation.accommodationId in :ids "
        + "order by af.displayOrder, af.file.fileId")
    List<AccommodationFileEntity> findImagesForAccommodations(@Param("ids") Collection<Long> accommodationIds);

    boolean existsByFileFileId(Long fileId);

    @Query("select count(af) > 0 from AccommodationFileEntity af where af.file.fileId = :fileId "
        + "and af.accommodation.status = :status")
    boolean isPublicImage(@Param("fileId") Long fileId, @Param("status") AccommodationStatus status);

    @Query("select coalesce(max(af.displayOrder), 0) from AccommodationFileEntity af "
        + "where af.accommodation.accommodationId = :id")
    int lastDisplayOrder(@Param("id") Long accommodationId);
}
