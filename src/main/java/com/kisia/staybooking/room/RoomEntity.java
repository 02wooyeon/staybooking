package com.kisia.staybooking.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.file.FileEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_rooms_accommodation_status", columnList = "accommodation_id,status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_rooms_accommodation_name", columnNames = {"accommodation_id", "name"})
})
@Check(constraints = "price >= 0 AND capacity >= 1 AND total_quantity >= 1")
public class RoomEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_rooms_accommodation"))
    private AccommodationEntity accommodation;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "total_quantity", nullable = false)
    @ColumnDefault("1")
    private int totalQuantity = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private RoomStatus status = RoomStatus.ACTIVE;

    // docs/architecture/DATABASE.md의 room_files. 숙소와 같은 이유로 JSON에서 뺀다.
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "room_files",
        joinColumns = @JoinColumn(name = "room_id", foreignKey = @ForeignKey(name = "fk_room_files_room")),
        inverseJoinColumns = @JoinColumn(name = "file_id", foreignKey = @ForeignKey(name = "fk_room_files_file")),
        uniqueConstraints = @UniqueConstraint(name = "uk_room_files_file", columnNames = "file_id"))
    @OrderColumn(name = "sort_order")
    private List<FileEntity> images = new ArrayList<>();

    protected RoomEntity() {
    }

    public RoomEntity(AccommodationEntity accommodation, String name, long price, int capacity, int totalQuantity) {
        this.accommodation = Objects.requireNonNull(accommodation);
        this.name = Objects.requireNonNull(name);
        if (price < 0 || capacity < 1 || totalQuantity < 1) {
            throw new IllegalArgumentException("Price, capacity, or total quantity is invalid");
        }
        this.price = price;
        this.capacity = capacity;
        this.totalQuantity = totalQuantity;
    }

    // 사진 목록은 화면에 보여 줄 순서대로 둔다. 첫 장이 대표 사진이다.
    public List<FileEntity> getImages() { return Collections.unmodifiableList(images); }

    public void addImage(FileEntity file) {
        images.add(Objects.requireNonNull(file));
    }

    public boolean removeImage(Long fileId) {
        return images.removeIf(file -> file.getFileId().equals(fileId));
    }

    public Long getRoomId() { return roomId; }
    public AccommodationEntity getAccommodation() { return accommodation; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
    public int getCapacity() { return capacity; }
    public int getTotalQuantity() { return totalQuantity; }
    public RoomStatus getStatus() { return status; }

    public void update(String name, long price, int capacity, int totalQuantity, String description, RoomStatus status) {
        if (price < 0 || capacity < 1 || totalQuantity < 1) throw new IllegalArgumentException("Invalid room values");
        this.name = Objects.requireNonNull(name); this.price = price; this.capacity = capacity;
        this.totalQuantity = totalQuantity; this.description = description; this.status = Objects.requireNonNull(status);
    }
}
