package com.kisia.staybooking.accommodation;

import com.kisia.staybooking.file.FileEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "accommodation_files", uniqueConstraints = {
    @UniqueConstraint(name = "uk_accommodation_files_file", columnNames = "file_id")
})
public class AccommodationFileEntity {
    @EmbeddedId
    private AccommodationFileId id;

    @MapsId("accommodationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_accommodation_files_accommodation"))
    private AccommodationEntity accommodation;

    @MapsId("fileId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_accommodation_files_file"))
    private FileEntity file;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AccommodationFileEntity() { }

    public AccommodationFileEntity(AccommodationEntity accommodation, FileEntity file, int displayOrder) {
        this.id = new AccommodationFileId(accommodation.getAccommodationId(), file.getFileId());
        this.accommodation = accommodation;
        this.file = file;
        this.displayOrder = displayOrder;
    }

    public AccommodationFileId getId() { return id; }
    public AccommodationEntity getAccommodation() { return accommodation; }
    public FileEntity getFile() { return file; }
    public int getDisplayOrder() { return displayOrder; }
}
