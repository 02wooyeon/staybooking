package com.kisia.staybooking.accommodation;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accommodations")
public class AccommodationEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accommodation_id")
    private Long accommodationId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime;

    // 편의시설·이용 규칙은 한 줄에 하나씩 저장한다. 값 안의 줄바꿈은 요청 검증에서 막는다.
    @Column(name = "amenities", columnDefinition = "TEXT")
    private String amenities;

    @Column(name = "rules", columnDefinition = "TEXT")
    private String rules;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private AccommodationStatus status = AccommodationStatus.ACTIVE;

    // docs/architecture/DATABASE.md의 accommodation_files. 관리자 API가 엔티티를 그대로 응답하므로
    // 업로드한 사람 정보가 JSON으로 나가지 않게 @JsonIgnore로 뺀다.
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "accommodation_files",
        joinColumns = @JoinColumn(name = "accommodation_id",
            foreignKey = @ForeignKey(name = "fk_accommodation_files_accommodation")),
        inverseJoinColumns = @JoinColumn(name = "file_id",
            foreignKey = @ForeignKey(name = "fk_accommodation_files_file")),
        uniqueConstraints = @UniqueConstraint(name = "uk_accommodation_files_file", columnNames = "file_id"))
    @OrderColumn(name = "display_order")
    private List<FileEntity> images = new ArrayList<>();

    protected AccommodationEntity() {
    }

    public AccommodationEntity(String name, String address, LocalTime checkInTime, LocalTime checkOutTime) {
        this.name = Objects.requireNonNull(name);
        this.address = Objects.requireNonNull(address);
        this.checkInTime = Objects.requireNonNull(checkInTime);
        this.checkOutTime = Objects.requireNonNull(checkOutTime);
    }

    public Long getAccommodationId() { return accommodationId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public AccommodationStatus getStatus() { return status; }
    public List<String> getAmenities() { return lines(amenities); }
    public List<String> getRules() { return lines(rules); }

    // 사진 목록은 화면에 보여 줄 순서대로 둔다. 첫 장이 대표 사진이다.
    public List<FileEntity> getImages() { return Collections.unmodifiableList(images); }

    public void addImage(FileEntity file) {
        images.add(Objects.requireNonNull(file));
    }

    public boolean removeImage(Long fileId) {
        return images.removeIf(file -> file.getFileId().equals(fileId));
    }

    public void updateAmenitiesAndRules(List<String> amenities, List<String> rules) {
        this.amenities = join(amenities);
        this.rules = join(rules);
    }

    private static List<String> lines(String value) {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\n"));
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("\n", values);
    }

    public void update(String name, String address, LocalTime checkInTime, LocalTime checkOutTime,
                       String description, AccommodationStatus status) {
        this.name = Objects.requireNonNull(name); this.address = Objects.requireNonNull(address);
        this.checkInTime = Objects.requireNonNull(checkInTime); this.checkOutTime = Objects.requireNonNull(checkOutTime);
        this.description = description; this.status = Objects.requireNonNull(status);
    }
}
