package com.kisia.staybooking.accommodation;

import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;

@RestController @RequestMapping("/api/v1/admin/accommodations")
public class AdminAccommodationController {
  private final AccommodationRepository accommodations; private final RoomRepository rooms;
  public AdminAccommodationController(AccommodationRepository accommodations, RoomRepository rooms) { this.accommodations=accommodations; this.rooms=rooms; }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public AccommodationEntity create(@Valid @RequestBody AccommodationRequest r) { return accommodations.save(new AccommodationEntity(r.name(),r.address(),r.checkInTime(),r.checkOutTime())); }
  @PatchMapping("/{id}") public AccommodationEntity update(@PathVariable Long id,@Valid @RequestBody AccommodationRequest r) { var a=accommodations.findById(id).orElseThrow(()->new IllegalArgumentException("Accommodation not found")); a.update(r.name(),r.address(),r.checkInTime(),r.checkOutTime(),r.description(),r.status()); return a; }
  @PostMapping("/{id}/rooms") @ResponseStatus(HttpStatus.CREATED) public RoomEntity createRoom(@PathVariable Long id,@Valid @RequestBody RoomRequest r) { var a=accommodations.findById(id).orElseThrow(()->new IllegalArgumentException("Accommodation not found")); return rooms.save(new RoomEntity(a,r.name(),r.price(),r.capacity(),r.totalQuantity())); }
  @PatchMapping("/{id}/rooms/{roomId}") public RoomEntity updateRoom(@PathVariable Long id,@PathVariable Long roomId,@Valid @RequestBody RoomRequest r) { var room=rooms.findByIdForUpdate(roomId).orElseThrow(()->new IllegalArgumentException("Room not found")); if(!room.getAccommodation().getAccommodationId().equals(id)) throw new IllegalArgumentException("Room does not belong to accommodation"); room.update(r.name(),r.price(),r.capacity(),r.totalQuantity(),r.description(),r.status()); return room; }
  public record AccommodationRequest(@NotBlank String name,@NotBlank String address,@NotNull LocalTime checkInTime,@NotNull LocalTime checkOutTime,String description,@NotNull AccommodationStatus status) {}
  public record RoomRequest(@NotBlank String name,@Min(0) long price,@Min(1) int capacity,@Min(1) int totalQuantity,String description,@NotNull RoomStatus status) {}
}
