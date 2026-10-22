package com.kisia.staybooking.review;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// REVIEW_001~004, ADMIN_006(리뷰 관리)
@Service
public class ReviewService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String DELETED_BY_WRITER = "작성자 삭제";

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final AccommodationRepository accommodations;
    private final UserRepository users;
    private final FileService files;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings,
                         AccommodationRepository accommodations, UserRepository users, FileService files) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.accommodations = accommodations;
        this.users = users;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public ReviewListResponse list(Long accommodationId, Long actorId, int page, int size) {
        accommodations.findById(accommodationId)
            .filter(a -> a.getStatus() == AccommodationStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "숙소를 찾을 수 없습니다."));
        Page<ReviewEntity> result = reviews.findByAccommodationAccommodationIdAndDeletedFalse(accommodationId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "reviewId")));
        Double average = reviews.averageRating(accommodationId);
        double rounded = average == null ? 0 : Math.round(average * 10) / 10.0;
        return new ReviewListResponse(rounded, reviews.countByAccommodationAccommodationIdAndDeletedFalse(accommodationId),
            result.getContent().stream().map(r -> ReviewResponse.from(r, actorId)).toList(),
            page, size, result.getTotalPages());
    }

    // 화면은 숙소 번호만 보내므로, bookingId가 없으면 그 숙소에서 이용을 마친 내 예약 중 후기 없는 것을 쓴다.
    @Transactional
    public ReviewResponse create(Long userId, Long accommodationId, Long bookingId, int rating, String content,
                                 Long imageFileId) {
        UserEntity user = activeUser(userId);
        BookingEntity booking = bookingId != null
            ? reviewableBooking(userId, bookingId, accommodationId)
            : firstReviewableBooking(userId, accommodationId);
        Set<FileEntity> images = images(userId, imageFileId, null);
        try {
            return ReviewResponse.from(reviews.saveAndFlush(new ReviewEntity(user, booking, rating, content, images)),
                userId);
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, int rating, String content, Long imageFileId) {
        ReviewEntity review = ownedReview(userId, reviewId);
        review.update(rating, content, images(userId, imageFileId, reviewId));
        return ReviewResponse.from(review, userId);
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        ownedReview(userId, reviewId).hide(DELETED_BY_WRITER);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(Long userId) {
        return reviews.findByUserUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
            .map(r -> ReviewResponse.from(r, userId)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> allForAdmin(int page, int size) {
        Page<ReviewEntity> result = reviews.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(AdminReviewResponse::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    // ADM-14: 원본은 남기고 숨김 처리하며 사유를 기록한다.
    @Transactional
    public AdminReviewResponse hideByAdmin(Long reviewId, String reason) {
        ReviewEntity review = reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::notFound);
        review.hide(reason);
        return AdminReviewResponse.from(review);
    }

    private BookingEntity reviewableBooking(Long userId, Long bookingId, Long accommodationId) {
        BookingEntity booking = bookings.findById(bookingId).orElseThrow(ReviewService::notAllowed);
        boolean mine = booking.getUser().getUserId().equals(userId);
        boolean sameAccommodation = accommodationId == null
            || booking.getRoom().getAccommodation().getAccommodationId().equals(accommodationId);
        if (!mine || !sameAccommodation || !stayFinished(booking)) throw notAllowed();
        if (reviews.existsByBookingBookingId(bookingId)) throw duplicate();
        return booking;
    }

    private BookingEntity firstReviewableBooking(Long userId, Long accommodationId) {
        if (accommodationId == null) throw notAllowed();
        return reviews.findReviewableBookings(userId, accommodationId, BookingStatus.COMPLETED, today())
            .stream().findFirst().orElseThrow(ReviewService::notAllowed);
    }

    // 승인 완료(COMPLETED)된 예약이고 체크아웃 날짜가 지났을 때를 "이용 완료"로 본다.
    private static boolean stayFinished(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.COMPLETED && !booking.getCheckOutDate().isAfter(today());
    }

    private Set<FileEntity> images(Long userId, Long imageFileId, Long reviewId) {
        Set<FileEntity> result = new LinkedHashSet<>();
        if (imageFileId == null) return result;
        FileEntity file = files.ownedFile(userId, imageFileId);
        boolean usedElsewhere = reviewId == null
            ? reviews.existsByFilesFileId(imageFileId)
            : reviews.fileUsedByOtherReview(imageFileId, reviewId);
        if (usedElsewhere) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 후기에 사용한 사진입니다.");
        }
        result.add(file);
        return result;
    }

    private ReviewEntity ownedReview(Long userId, Long reviewId) {
        ReviewEntity review = reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::notFound);
        if (review.isDeleted() || !review.getUser().getUserId().equals(userId)) throw notFound();
        return review;
    }

    private UserEntity activeUser(Long userId) {
        return users.findById(userId)
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    }

    private static LocalDate today() {
        return LocalDate.now(KOREA);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "후기를 찾을 수 없습니다.");
    }

    private static ApiException notAllowed() {
        return new ApiException(HttpStatus.FORBIDDEN, "REVIEW_NOT_ALLOWED", "이용을 마친 예약이 있어야 후기를 쓸 수 있습니다.");
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REVIEW", "이 예약에는 이미 후기를 썼습니다.");
    }
}
