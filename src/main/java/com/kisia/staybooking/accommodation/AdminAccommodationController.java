package com.kisia.staybooking.accommodation;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.booking.BookingInventoryService;
import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@RestController @RequestMapping("/api/v1/admin/accommodations")
public class AdminAccommodationController {
  private final AccommodationRepository accommodations; private final RoomRepository rooms;
  private final AdminAccommodationQueryService query;
  private final BookingInventoryService inventory;
  public AdminAccommodationController(AccommodationRepository accommodations, RoomRepository rooms,
                                      AdminAccommodationQueryService query, BookingInventoryService inventory) {
    this.accommodations=accommodations; this.rooms=rooms; this.query=query; this.inventory=inventory;
  }
  @GetMapping("/search") public PageResponse<AdminAccommodationQueryService.Summary> search(
      @Valid @ModelAttribute AdminAccommodationSearchRequest request) { return query.search(request); }
  @GetMapping("/{id}") public AdminAccommodationQueryService.Detail detail(@PathVariable @Positive Long id) {
    return query.detail(id);
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public AccommodationEntity create(@Valid @RequestBody AccommodationRequest r) {
    AccommodationEntity accommodation = new AccommodationEntity(r.name(), r.address(), r.checkInTime(), r.checkOutTime());
    accommodation.update(r.name(), r.address(), r.checkInTime(), r.checkOutTime(), r.description(), r.status());
    applyAmenitiesAndRules(accommodation, r);
    return accommodations.save(accommodation);
  }
  @Transactional
  @PatchMapping("/{id}") public AccommodationEntity update(@PathVariable @Positive Long id,@Valid @RequestBody AccommodationRequest r) { var a=accommodations.findById(id).orElseThrow(AdminAccommodationController::accommodationNotFound); a.update(r.name(),r.address(),r.checkInTime(),r.checkOutTime(),r.description(),r.status()); applyAmenitiesAndRules(a, r); return a; }
  @Transactional
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable @Positive Long id) {
    var accommodation = accommodations.findById(id)
        .orElseThrow(AdminAccommodationController::accommodationNotFound);
    accommodation.update(accommodation.getName(), accommodation.getAddress(),
        accommodation.getCheckInTime(), accommodation.getCheckOutTime(),
        accommodation.getDescription(), AccommodationStatus.INACTIVE);
  }
  @Transactional
  @PostMapping("/{id}/rooms") @ResponseStatus(HttpStatus.CREATED) public RoomEntity createRoom(@PathVariable @Positive Long id,@Valid @RequestBody RoomRequest r) {
    var accommodation = accommodations.findByIdForUpdate(id).orElseThrow(AdminAccommodationController::accommodationNotFound);
    if (rooms.existsByAccommodationAccommodationIdAndName(id, r.name())) throw duplicateRoomName();
    RoomEntity room = new RoomEntity(accommodation, r.name(), r.price(), r.capacity(), r.totalQuantity());
    room.update(r.name(), r.price(), r.capacity(), r.totalQuantity(), r.description(), r.status());
    return rooms.save(room);
  }
  @Transactional
  @PatchMapping("/{id}/rooms/{roomId}") public RoomEntity updateRoom(@PathVariable @Positive Long id,@PathVariable @Positive Long roomId,@Valid @RequestBody RoomRequest r) {
    accommodations.findByIdForUpdate(id).orElseThrow(AdminAccommodationController::accommodationNotFound);
    var room=rooms.findByIdForUpdate(roomId).orElseThrow(AdminAccommodationController::roomNotFound);
    if(!room.getAccommodation().getAccommodationId().equals(id)) throw roomNotFound();
    if (rooms.existsByAccommodationAccommodationIdAndNameAndRoomIdNot(id, r.name(), roomId)) throw duplicateRoomName();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
    if (r.totalQuantity() < room.getTotalQuantity()) {
      int occupied = inventory.maximumOccupiedUnitsFrom(room, today, nowUtc);
      if (r.totalQuantity() < occupied) {
        throw new ApiException(HttpStatus.CONFLICT, "ROOM_QUANTITY_BELOW_BOOKINGS",
            "기존 예약이 점유한 객실 수보다 판매 수량을 줄일 수 없습니다.", List.of("totalQuantity"));
      }
    }
    if (r.capacity() < room.getCapacity() &&
        r.capacity() < inventory.maximumBookedGuestsFrom(room, today, nowUtc)) {
      throw new ApiException(HttpStatus.CONFLICT, "ROOM_CAPACITY_BELOW_BOOKINGS",
          "기존 예약 인원보다 객실 정원을 줄일 수 없습니다.", List.of("capacity"));
    }
    room.update(r.name(),r.price(),r.capacity(),r.totalQuantity(),r.description(),r.status());
    return room;
  }
  @Transactional
  @DeleteMapping("/{id}/rooms/{roomId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateRoom(@PathVariable @Positive Long id, @PathVariable @Positive Long roomId) {
    accommodations.findByIdForUpdate(id).orElseThrow(AdminAccommodationController::accommodationNotFound);
    var room = rooms.findByIdForUpdate(roomId)
        .orElseThrow(AdminAccommodationController::roomNotFound);
    if (!room.getAccommodation().getAccommodationId().equals(id)) {
      throw roomNotFound();
    }
    room.update(room.getName(), room.getPrice(), room.getCapacity(), room.getTotalQuantity(),
        room.getDescription(), RoomStatus.INACTIVE);
  }
  // 관리자 화면(admin-static/accommodations.html)의 편의시설 체크박스와 같은 목록만 받는다
  static final Set<String> AMENITIES = Set.of("무료 Wi-Fi", "주차", "에어컨", "난방", "TV", "냉장고", "전자레인지",
      "취사 가능", "세탁기", "수영장", "피트니스", "조식 제공", "바비큐", "엘리베이터", "반려동물 동반", "금연 객실");

  // 값이 없으면(null) 기존 편의시설·이용 규칙을 그대로 둔다
  private static void applyAmenitiesAndRules(AccommodationEntity accommodation, AccommodationRequest r) {
    if (r.amenities() != null && !AMENITIES.containsAll(r.amenities())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.", List.of("amenities"));
    }
    accommodation.updateAmenitiesAndRules(
        r.amenities() != null ? r.amenities().stream().distinct().toList() : accommodation.getAmenities(),
        r.rules() != null ? r.rules() : accommodation.getRules());
  }

  private static ApiException accommodationNotFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "숙소를 찾을 수 없습니다.");
  }

  private static ApiException roomNotFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "객실을 찾을 수 없습니다.");
  }

  private static ApiException duplicateRoomName() {
    return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_ROOM_NAME", "같은 숙소에 동일한 객실명이 있습니다.", List.of("name"));
  }

  public record AccommodationRequest(@NotBlank @Size(max = 150) String name,@NotBlank @Size(max = 255) String address,@NotNull LocalTime checkInTime,@NotNull LocalTime checkOutTime,@Size(max = 10000) String description,@NotNull AccommodationStatus status,
      @Size(max = 16) List<@NotBlank String> amenities,
      @Size(max = 20) List<@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\r\\n]*") String> rules) {
    public AccommodationRequest(String name, String address, LocalTime checkInTime, LocalTime checkOutTime,
                                String description, AccommodationStatus status) {
      this(name, address, checkInTime, checkOutTime, description, status, null, null);
    }
  }
  public record RoomRequest(@NotBlank @Size(max = 100) String name,@Min(0) long price,@Min(1) int capacity,@Min(1) int totalQuantity,@Size(max = 10000) String description,@NotNull RoomStatus status) {}
}
