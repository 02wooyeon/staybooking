package com.kisia.staybooking.accommodation;

import java.math.BigDecimal;
import java.time.LocalTime;
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
}
