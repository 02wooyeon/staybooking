package com.kisia.staybooking.accommodation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.kisia.staybooking.booking.BookingInventoryService;
import com.kisia.staybooking.booking.StayDatePolicy;
import com.kisia.staybooking.common.ApiException;
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
    // 날짜별 재고는 애플리케이션에서 계산하지만 후보 전체를 한 번에 적재하지 않는다.
    private static final int DATE_SEARCH_BATCH_SIZE = 200;

    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;
    private final BookingInventoryService inventory;
    private final AccommodationImageService images;

    public AccommodationService(AccommodationRepository accommodations, RoomRepository rooms,
                                BookingInventoryService inventory, AccommodationImageService images) {
        this.accommodations = accommodations;
        this.rooms = rooms;
        this.inventory = inventory;
        this.images = images;
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

        long from = (long) page * size;
        List<AccommodationSummary> content = new ArrayList<>(size);
        long total = 0;
        int batch = 0;
        Page<AccommodationEntity> candidates;
        do {
            candidates = accommodations.search(AccommodationStatus.ACTIVE, pattern,
                RoomStatus.ACTIVE, guests, PageRequest.of(batch++, DATE_SEARCH_BATCH_SIZE));
            for (AccommodationSummary summary : summaries(candidates.getContent(), guests, stay)) {
                if (total >= from && content.size() < size) content.add(summary);
                total++;
            }
        } while (candidates.hasNext());
        int totalPages = Math.toIntExact((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, totalPages);
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
                    room.getDescription(), null, room.getPrice(), room.getCapacity(), remaining,
                    remaining > 0 ? "AVAILABLE" : "SOLD_OUT");
            })
            .toList();
        return new AccommodationDetail(accommodation.getAccommodationId(), accommodation.getName(),
            accommodation.getAddress(), accommodation.getDescription(), accommodation.getCheckInTime(),
            accommodation.getCheckOutTime(), accommodation.getLatitude(), accommodation.getLongitude(),
            accommodation.getStatus(), accommodation.getAmenities(), accommodation.getRules(),
            images.urls(accommodationId),
            roomSummaries);
    }

    // stay가 있으면 그 기간에 예약 가능한 객실이 있는 숙소만 남기고, 최저가도 예약 가능한 객실 기준으로 계산한다.
    private List<AccommodationSummary> summaries(List<AccommodationEntity> list, int guests, Stay stay) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        Map<Long, String> mainImages = images.mainImageUrls(
            list.stream().map(AccommodationEntity::getAccommodationId).toList());
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
                        a.getAddress(), a.getDescription(), mainImages.get(a.getAccommodationId()),
                        cheapest.getPrice()))
                    .orElse(null);
            })
            .filter(summary -> summary != null)
            .toList();
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
