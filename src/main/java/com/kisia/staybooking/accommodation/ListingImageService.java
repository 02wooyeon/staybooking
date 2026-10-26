package com.kisia.staybooking.accommodation;

import java.util.List;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ADMIN_001·ADMIN_002의 숙소·객실 사진. 사진 파일은 먼저 POST /api/v1/uploads/Images로 올리고
// (확장자·매직 바이트·용량 검사와 재인코딩은 FileService가 한다) 받은 fileId를 여기서 붙인다.
@Service
public class ListingImageService {
    static final int MAX_ACCOMMODATION_IMAGES = 10;
    static final int MAX_ROOM_IMAGES = 5;

    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;
    private final FileService files;

    public ListingImageService(AccommodationRepository accommodations, RoomRepository rooms, FileService files) {
        this.accommodations = accommodations;
        this.rooms = rooms;
        this.files = files;
    }

    @Transactional
    public List<AdminAccommodationQueryService.Image> attachToAccommodation(Long adminId, Long accommodationId,
                                                                           Long fileId) {
        AccommodationEntity accommodation = accommodation(accommodationId);
        if (accommodation.getImages().size() >= MAX_ACCOMMODATION_IMAGES) throw tooMany(MAX_ACCOMMODATION_IMAGES);
        accommodation.addImage(usableFile(adminId, fileId));
        return AdminAccommodationQueryService.Image.listOf(accommodation.getImages());
    }

    @Transactional
    public void detachFromAccommodation(Long accommodationId, Long fileId) {
        AccommodationEntity accommodation = accommodation(accommodationId);
        FileEntity file = find(accommodation.getImages(), fileId);
        accommodation.removeImage(fileId);
        accommodations.flush();
        files.deleteDetached(file);
    }

    @Transactional
    public List<AdminAccommodationQueryService.Image> attachToRoom(Long adminId, Long accommodationId, Long roomId,
                                                                  Long fileId) {
        RoomEntity room = room(accommodationId, roomId);
        if (room.getImages().size() >= MAX_ROOM_IMAGES) throw tooMany(MAX_ROOM_IMAGES);
        room.addImage(usableFile(adminId, fileId));
        return AdminAccommodationQueryService.Image.listOf(room.getImages());
    }

    @Transactional
    public void detachFromRoom(Long accommodationId, Long roomId, Long fileId) {
        RoomEntity room = room(accommodationId, roomId);
        FileEntity file = find(room.getImages(), fileId);
        room.removeImage(fileId);
        rooms.flush();
        files.deleteDetached(file);
    }

    // 본인이 올렸고 아직 어디에도 붙지 않은 사진만 쓴다. (다른 사람이 올린 후기 사진 등을 가져다 쓰지 못하게)
    private FileEntity usableFile(Long adminId, Long fileId) {
        FileEntity file = files.ownedFile(adminId, fileId);
        if (files.isLinked(fileId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 곳에 사용한 사진입니다.");
        }
        return file;
    }

    private AccommodationEntity accommodation(Long accommodationId) {
        return accommodations.findById(accommodationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "숙소를 찾을 수 없습니다."));
    }

    private RoomEntity room(Long accommodationId, Long roomId) {
        return rooms.findByIdForUpdate(roomId)
            .filter(room -> room.getAccommodation().getAccommodationId().equals(accommodationId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "객실을 찾을 수 없습니다."));
    }

    private static FileEntity find(List<FileEntity> images, Long fileId) {
        return images.stream().filter(file -> file.getFileId().equals(fileId)).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "사진을 찾을 수 없습니다."));
    }

    private static ApiException tooMany(int max) {
        return new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_IMAGES", "사진은 최대 " + max + "장까지 등록할 수 있습니다.");
    }
}
