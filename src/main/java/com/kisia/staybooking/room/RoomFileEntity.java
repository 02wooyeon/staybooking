package com.kisia.staybooking.room;

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
@Table(name = "room_files", uniqueConstraints = {
    @UniqueConstraint(name = "uk_room_files_file", columnNames = "file_id")
})
public class RoomFileEntity {
    @EmbeddedId
    private RoomFileId id;

    @MapsId("roomId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_room_files_room"))
    private RoomEntity room;

    @MapsId("fileId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_room_files_file"))
    private FileEntity file;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected RoomFileEntity() { }

    public RoomFileEntity(RoomEntity room, FileEntity file, int displayOrder) {
        this.id = new RoomFileId(room.getRoomId(), file.getFileId());
        this.room = room;
        this.file = file;
        this.displayOrder = displayOrder;
    }

    public RoomEntity getRoom() { return room; }
    public FileEntity getFile() { return file; }
    public int getDisplayOrder() { return displayOrder; }
}
