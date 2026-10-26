package com.kisia.staybooking.accommodation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.file.UploadResponse;
import com.kisia.staybooking.qna.QnaRepository;
import com.kisia.staybooking.review.ReviewRepository;
import com.kisia.staybooking.room.RoomFileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccommodationImageService {
    private static final int MAX_IMAGES = 10;

    private final AccommodationRepository accommodations;
    private final AccommodationFileRepository images;
    private final FileService files;
    private final ReviewRepository reviews;
    private final QnaRepository questions;
    private final RoomFileRepository roomImages;

    public AccommodationImageService(AccommodationRepository accommodations, AccommodationFileRepository images,
                                     FileService files, ReviewRepository reviews, QnaRepository questions,
                                     RoomFileRepository roomImages) {
        this.accommodations = accommodations;
        this.images = images;
        this.files = files;
        this.reviews = reviews;
        this.questions = questions;
        this.roomImages = roomImages;
    }

    @Transactional
    public ImageResponse attach(Long adminId, Long accommodationId, Long fileId) {
        AccommodationEntity accommodation = accommodations.findByIdForUpdate(accommodationId)
            .orElseThrow(AccommodationImageService::notFound);
        FileEntity file = files.ownedFile(adminId, fileId);
        AccommodationFileId id = new AccommodationFileId(accommodationId, fileId);
        if (images.existsById(id)) return ImageResponse.from(file);
        if (images.existsByFileFileId(fileId) || roomImages.existsByFileFileId(fileId)
                || reviews.existsByFilesFileId(fileId)
                || questions.existsByFilesFileId(fileId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 글에 연결된 사진입니다.");
        }
        if (images.findImages(accommodationId).size() >= MAX_IMAGES) {
            throw new ApiException(HttpStatus.CONFLICT, "IMAGE_LIMIT_REACHED", "숙소 사진은 최대 10장입니다.");
        }
        try {
            images.saveAndFlush(new AccommodationFileEntity(accommodation, file,
                images.lastDisplayOrder(accommodationId) + 1));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 숙소에 연결된 사진입니다.");
        }
        return ImageResponse.from(file);
    }

    @Transactional
    public void detach(Long accommodationId, Long fileId) {
        accommodations.findByIdForUpdate(accommodationId).orElseThrow(AccommodationImageService::notFound);
        AccommodationFileEntity image = images.findById(new AccommodationFileId(accommodationId, fileId))
            .orElseThrow(AccommodationImageService::notFound);
        images.delete(image);
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> images(Long accommodationId) {
        return images.findImages(accommodationId).stream().map(link -> ImageResponse.from(link.getFile())).toList();
    }

    @Transactional(readOnly = true)
    public List<String> urls(Long accommodationId) {
        return images(accommodationId).stream().map(ImageResponse::url).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> mainImageUrls(Collection<Long> accommodationIds) {
        if (accommodationIds.isEmpty()) return Map.of();
        Map<Long, String> result = new LinkedHashMap<>();
        for (AccommodationFileEntity image : images.findImagesForAccommodations(accommodationIds)) {
            result.putIfAbsent(image.getAccommodation().getAccommodationId(),
                UploadResponse.urlOf(image.getFile().getFileId()));
        }
        return result;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "숙소 사진을 찾을 수 없습니다.");
    }

    public record ImageResponse(Long fileId, String url) {
        static ImageResponse from(FileEntity file) {
            return new ImageResponse(file.getFileId(), UploadResponse.urlOf(file.getFileId()));
        }
    }
}
