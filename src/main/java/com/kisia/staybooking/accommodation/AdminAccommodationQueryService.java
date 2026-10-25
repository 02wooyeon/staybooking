package com.kisia.staybooking.accommodation;

import java.time.LocalTime;
import java.util.List;

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
public class AdminAccommodationQueryService {
    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;

    public AdminAccommodationQueryService(AccommodationRepository accommodations, RoomRepository rooms) {
        this.accommodations = accommodations;
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> search(AdminAccommodationSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        String keyword = request.keyword() == null ? "" : request.keyword().trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%")
            .replace("_", "!_") + "%";
        Page<AccommodationEntity> result = accommodations.searchForAdmin(request.status(), pattern,
            PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(Summary::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Detail detail(Long accommodationId) {
        AccommodationEntity accommodation = accommodations.findById(accommodationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "숙소를 찾을 수 없습니다."));
        List<RoomSummary> roomSummaries = rooms.findByAccommodationAccommodationIdOrderByRoomIdAsc(accommodationId)
            .stream().map(RoomSummary::from).toList();
        return new Detail(Summary.from(accommodation), accommodation.getDescription(),
            accommodation.getCheckInTime(), accommodation.getCheckOutTime(),
            accommodation.getAmenities(), accommodation.getRules(), roomSummaries);
    }

    public record Summary(Long accommodationId, String name, String address, AccommodationStatus status) {
        static Summary from(AccommodationEntity accommodation) {
            return new Summary(accommodation.getAccommodationId(), accommodation.getName(),
                accommodation.getAddress(), accommodation.getStatus());
        }
    }

    public record Detail(Summary accommodation, String description, LocalTime checkInTime,
                         LocalTime checkOutTime, List<String> amenities, List<String> rules,
                         List<RoomSummary> rooms) { }

    public record RoomSummary(Long roomId, String name, String description, long price,
                              int capacity, int totalQuantity, RoomStatus status) {
        static RoomSummary from(RoomEntity room) {
            return new RoomSummary(room.getRoomId(), room.getName(), room.getDescription(),
                room.getPrice(), room.getCapacity(), room.getTotalQuantity(), room.getStatus());
        }
    }
}
