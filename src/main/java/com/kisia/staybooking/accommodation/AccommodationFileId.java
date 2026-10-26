package com.kisia.staybooking.accommodation;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class AccommodationFileId implements Serializable {
    @Column(name = "accommodation_id")
    private Long accommodationId;

    @Column(name = "file_id")
    private Long fileId;

    protected AccommodationFileId() { }

    public AccommodationFileId(Long accommodationId, Long fileId) {
        this.accommodationId = accommodationId;
        this.fileId = fileId;
    }

    public Long getAccommodationId() { return accommodationId; }
    public Long getFileId() { return fileId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccommodationFileId id)) return false;
        return Objects.equals(accommodationId, id.accommodationId) && Objects.equals(fileId, id.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accommodationId, fileId);
    }
}
