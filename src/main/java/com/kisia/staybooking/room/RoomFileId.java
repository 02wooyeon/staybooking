package com.kisia.staybooking.room;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RoomFileId implements Serializable {
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "file_id")
    private Long fileId;

    protected RoomFileId() { }

    public RoomFileId(Long roomId, Long fileId) {
        this.roomId = roomId;
        this.fileId = fileId;
    }

    public Long getRoomId() { return roomId; }
    public Long getFileId() { return fileId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RoomFileId id)) return false;
        return Objects.equals(roomId, id.roomId) && Objects.equals(fileId, id.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, fileId);
    }
}
