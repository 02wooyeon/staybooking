package com.kisia.staybooking.accommodation;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 관리자 화면(admin-accommodations.js의 IMAGE_API)과 같은 경로. /api/v1/admin/** 는 SecurityConfig가 ROLE_ADMIN만 들여보낸다.
// 상세 조회(GET /api/v1/admin/accommodations/{id})의 images, rooms[].images 에 붙인 사진이 나온다.
@RestController
@RequestMapping("/api/v1/admin/accommodations/{accommodationId}")
public class AdminListingImageController {
    private final ListingImageService images;

    public AdminListingImageController(ListingImageService images) {
        this.images = images;
    }

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AdminAccommodationQueryService.Image> attach(@AuthenticationPrincipal AuthenticatedUser me,
                                                             @PathVariable @Positive Long accommodationId,
                                                             @Valid @RequestBody AttachRequest request) {
        return images.attachToAccommodation(me.userId(), accommodationId, request.fileId());
    }

    @DeleteMapping("/images/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@PathVariable @Positive Long accommodationId, @PathVariable @Positive Long fileId) {
        images.detachFromAccommodation(accommodationId, fileId);
    }

    @PostMapping("/rooms/{roomId}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AdminAccommodationQueryService.Image> attachToRoom(@AuthenticationPrincipal AuthenticatedUser me,
                                                                   @PathVariable @Positive Long accommodationId,
                                                                   @PathVariable @Positive Long roomId,
                                                                   @Valid @RequestBody AttachRequest request) {
        return images.attachToRoom(me.userId(), accommodationId, roomId, request.fileId());
    }

    @DeleteMapping("/rooms/{roomId}/images/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detachFromRoom(@PathVariable @Positive Long accommodationId, @PathVariable @Positive Long roomId,
                               @PathVariable @Positive Long fileId) {
        images.detachFromRoom(accommodationId, roomId, fileId);
    }

    public record AttachRequest(@NotNull @Positive Long fileId) { }
}
