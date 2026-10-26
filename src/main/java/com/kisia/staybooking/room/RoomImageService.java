package com.kisia.staybooking.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kisia.staybooking.accommodation.AccommodationFileRepository;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.file.UploadResponse;
import com.kisia.staybooking.qna.QnaRepository;
import com.kisia.staybooking.review.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomImageService {
    private static final int MAX_IMAGES = 10;

    private final RoomRepository rooms;
    private final RoomFileRepository images;
    private final AccommodationFileRepository accommodationImages;
    private final FileService files;
    private final ReviewRepository reviews;
    private final QnaRepository questions;

    public RoomImageService(RoomRepository rooms, RoomFileRepository images,
                            AccommodationFileRepository accommodationImages, FileService files,
                            ReviewRepository reviews, QnaRepository questions) {
        this.rooms = rooms;
        this.images = images;
        this.accommodationImages = accommodationImages;
        this.files = files;
        this.reviews = reviews;
        this.questions = questions;
    }

    @Transactional
    public ImageResponse attach(Long adminId, Long accommodationId, Long roomId, Long fileId) {
        RoomEntity room = ownedRoom(accommodationId, roomId);
        FileEntity file = files.ownedFile(adminId, fileId);
        RoomFileId id = new RoomFileId(roomId, fileId);
        if (images.existsById(id)) return ImageResponse.from(file);
        if (images.existsByFileFileId(fileId) || accommodationImages.existsByFileFileId(fileId)
                || reviews.existsByFilesFileId(fileId) || questions.existsByFilesFileId(fileId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 곳에 연결된 사진입니다.");
        }
        if (images.findImages(roomId).size() >= MAX_IMAGES) {
            throw new ApiException(HttpStatus.CONFLICT, "IMAGE_LIMIT_REACHED", "객실 사진은 최대 10장입니다.");
        }
        try {
            images.saveAndFlush(new RoomFileEntity(room, file, images.lastDisplayOrder(roomId) + 1));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 객실에 연결된 사진입니다.");
        }
        return ImageResponse.from(file);
    }

    @Transactional
    public void detach(Long accommodationId, Long roomId, Long fileId) {
        ownedRoom(accommodationId, roomId);
        RoomFileEntity image = images.findById(new RoomFileId(roomId, fileId))
            .orElseThrow(RoomImageService::notFound);
        images.delete(image);
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> images(Long roomId) {
        return images.findImages(roomId).stream().map(link -> ImageResponse.from(link.getFile())).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> mainImageUrls(Collection<Long> roomIds) {
        if (roomIds.isEmpty()) return Map.of();
        Map<Long, String> result = new LinkedHashMap<>();
        for (RoomFileEntity image : images.findImagesForRooms(roomIds)) {
            result.putIfAbsent(image.getRoom().getRoomId(), UploadResponse.urlOf(image.getFile().getFileId()));
        }
        return result;
    }

    private RoomEntity ownedRoom(Long accommodationId, Long roomId) {
        RoomEntity room = rooms.findByIdForUpdate(roomId).orElseThrow(RoomImageService::notFound);
        if (!room.getAccommodation().getAccommodationId().equals(accommodationId)) throw notFound();
        return room;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "객실 사진을 찾을 수 없습니다.");
    }

    public record ImageResponse(Long fileId, String url) {
        static ImageResponse from(FileEntity file) {
            return new ImageResponse(file.getFileId(), UploadResponse.urlOf(file.getFileId()));
        }
    }
}
