package com.kisia.staybooking.file;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// docs/architecture/DATABASE.md의 files. 실제 파일은 웹 루트 밖(staybooking.upload.dir)에 UUID 이름으로 저장한다.
@Entity
@Table(name = "files", uniqueConstraints = {
    @UniqueConstraint(name = "uk_files_stored_name", columnNames = "stored_name")
})
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false,
        foreignKey = @ForeignKey(name = "fk_files_uploaded_by"))
    private UserEntity uploadedBy;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FileEntity() {
    }

    public FileEntity(UserEntity uploadedBy, String originalName, String storedName, String filePath,
                      String mimeType, long fileSize, LocalDateTime createdAt) {
        this.uploadedBy = Objects.requireNonNull(uploadedBy);
        this.originalName = Objects.requireNonNull(originalName);
        this.storedName = Objects.requireNonNull(storedName);
        this.filePath = Objects.requireNonNull(filePath);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.fileSize = fileSize;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getFileId() { return fileId; }
    public UserEntity getUploadedBy() { return uploadedBy; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getFilePath() { return filePath; }
    public String getMimeType() { return mimeType; }
    public long getFileSize() { return fileSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
