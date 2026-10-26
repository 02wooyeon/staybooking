package com.kisia.staybooking.room;

import java.util.Collection;
import java.util.List;

import com.kisia.staybooking.accommodation.AccommodationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomFileRepository extends JpaRepository<RoomFileEntity, RoomFileId> {
    @Query("select rf from RoomFileEntity rf join fetch rf.file "
        + "where rf.room.roomId = :id order by rf.displayOrder, rf.file.fileId")
    List<RoomFileEntity> findImages(@Param("id") Long roomId);

    @Query("select rf from RoomFileEntity rf where rf.room.roomId in :ids "
        + "order by rf.displayOrder, rf.file.fileId")
    List<RoomFileEntity> findImagesForRooms(@Param("ids") Collection<Long> roomIds);

    boolean existsByFileFileId(Long fileId);

    @Query("select count(rf) > 0 from RoomFileEntity rf where rf.file.fileId = :fileId "
        + "and rf.room.status = :roomStatus and rf.room.accommodation.status = :accommodationStatus")
    boolean isPublicImage(@Param("fileId") Long fileId, @Param("roomStatus") RoomStatus roomStatus,
                          @Param("accommodationStatus") AccommodationStatus accommodationStatus);

    @Query("select coalesce(max(rf.displayOrder), 0) from RoomFileEntity rf where rf.room.roomId = :id")
    int lastDisplayOrder(@Param("id") Long roomId);
}
