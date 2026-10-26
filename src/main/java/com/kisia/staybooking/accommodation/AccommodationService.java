package com.kisia.staybooking.accommodation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.kisia.staybooking.booking.BookingInventoryService;
import com.kisia.staybooking.booking.StayDatePolicy;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.UploadResponse;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccommodationService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_PAGE_SIZE = 20;
    // 날짜 조건이 있으면 빈 객실 확인을 위해 후보를 메모리로 가져오므로 개수를 제한한다.
    private static final int MAX_DATE_SEARCH_CANDIDATES = 300;

    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;
    private final BookingInventoryService inventory;

    public AccommodationService(AccommodationRepository accommodations, RoomRepository rooms,
                                BookingInventoryService inventory) {
        this.accommodations = accommodations;
        this.rooms = rooms;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccommodationSummary> search(AccommodationSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        int guests = request.guests() == null ? 1 : request.guests();
        String pattern = likePattern(request.keyword());
        Stay stay = stay(request.checkIn(), request.checkOut());

        if (stay == null) {
            Page<AccommodationEntity> result = accommodations.search(AccommodationStatus.ACTIVE, pattern,
                RoomStatus.ACTIVE, guests, PageRequest.of(page, size));
            return new PageResponse<>(summaries(result.getContent(), guests, null), page, size,
                result.getTotalElements(), result.getTotalPages());
        }

        List<AccommodationEntity> candidates = accommodations.search(AccommodationStatus.ACTIVE, pattern,
            RoomStatus.ACTIVE, guests, PageRequest.of(0, MAX_DATE_SEARCH_CANDIDATES)).getContent();
        List<AccommodationSummary> available = summaries(candidates, guests, stay);
        int from = Math.min(page * size, available.size());
        int to = Math.min(from + size, available.size());
        int totalPages = (available.size() + size - 1) / size;
        return new PageResponse<>(available.subList(from, to), page, size, available.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public AccommodationDetail detail(Long accommodationId, AccommodationSearchRequest request) {
        AccommodationEntity accommodation = accommodations.findById(accommodationId)
            .filter(a -> a.getStatus() == AccommodationStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "숙소를 찾을 수 없습니다."));
        // 날짜 없이 열면 오늘 밤 1박 기준으로 남은 객실을 보여 준다.
        Stay requested = stay(request.checkIn(), request.checkOut());
        LocalDate today = LocalDate.now(KOREA);
        Stay stay = requested != null ? requested : new Stay(today, today.plusDays(1));
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        List<AccommodationDetail.RoomSummary> roomSummaries = rooms
            .findByAccommodationAccommodationIdInAndStatusOrderByPriceAsc(List.of(accommodationId), RoomStatus.ACTIVE)
            .stream()
            .map(room -> {
                int remaining = inventory.remainingUnits(room, stay.checkIn(), stay.checkOut(), nowUtc);
                return new AccommodationDetail.RoomSummary(room.getRoomId(), room.getName(),
                    room.getDescription(), mainImage(room.getImages()), room.getPrice(), room.getCapacity(), remaining,
                    remaining > 0 ? "AVAILABLE" : "SOLD_OUT");
            })
            .toList();
        return new AccommodationDetail(accommodation.getAccommodationId(), accommodation.getName(),
            accommodation.getAddress(), accommodation.getDescription(), accommodation.getCheckInTime(),
            accommodation.getCheckOutTime(), accommodation.getLatitude(), accommodation.getLongitude(),
            accommodation.getStatus(), accommodation.getAmenities(), accommodation.getRules(),
            accommodation.getImages().stream().map(file -> UploadResponse.urlOf(file.getFileId())).toList(),
            roomSummaries);
    }

    // stay가 있으면 그 기간에 예약 가능한 객실이 있는 숙소만 남기고, 최저가도 예약 가능한 객실 기준으로 계산한다.
    private List<AccommodationSummary> summaries(List<AccommodationEntity> list, int guests, Stay stay) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        Map<Long, List<RoomEntity>> roomsByAccommodation = rooms
            .findByAccommodationAccommodationIdInAndStatusOrderByPriceAsc(
                list.stream().map(AccommodationEntity::getAccommodationId).toList(), RoomStatus.ACTIVE)
            .stream()
            .filter(room -> room.getCapacity() >= guests)
            .collect(Collectors.groupingBy(room -> room.getAccommodation().getAccommodationId()));
        return list.stream()
            .map(a -> {
                List<RoomEntity> candidates = roomsByAccommodation.getOrDefault(a.getAccommodationId(), List.of());
                return candidates.stream()
                    .filter(room -> stay == null || bookable(room, guests, stay, nowUtc))
                    .findFirst()
                    .map(cheapest -> new AccommodationSummary(a.getAccommodationId(), a.getName(),
                        a.getAddress(), a.getDescription(), mainImage(a.getImages()), cheapest.getPrice()))
                    .orElse(null);
            })
            .filter(summary -> summary != null)
            .toList();
    }

    // 첫 장이 대표 사진이다. 사진이 없으면 화면이 "이미지 준비 중"을 보여 준다.
    private static String mainImage(List<FileEntity> images) {
        return images.isEmpty() ? null : UploadResponse.urlOf(images.get(0).getFileId());
    }

    private boolean bookable(RoomEntity room, int guests, Stay stay, LocalDateTime nowUtc) {
        return room.getCapacity() >= guests
            && inventory.isAvailable(room, stay.checkIn(), stay.checkOut(), nowUtc);
    }

    // 체크인·체크아웃은 둘 다 있거나 둘 다 없어야 한다.
    private static Stay stay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null && checkOut == null) {
            return null;
        }
        if (!StayDatePolicy.isValid(checkIn, checkOut, LocalDate.now(KOREA))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.");
        }
        return new Stay(checkIn, checkOut);
    }

    // %, _ 를 일반 문자로 취급하도록 이스케이프한다.
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private record Stay(LocalDate checkIn, LocalDate checkOut) {
    }
}
