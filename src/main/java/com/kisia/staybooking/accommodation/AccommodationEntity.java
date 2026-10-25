package com.kisia.staybooking.accommodation;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import com.kisia.staybooking.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
