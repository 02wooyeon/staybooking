package com.kisia.staybooking.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.accommodation.AccommodationFileRepository;
import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.qna.QnaRepository;
import com.kisia.staybooking.review.ReviewRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// UPLOADS-001/002. 정의서의 업로드 보안 대책을 서버에서 모두 검사한다.
@Service
public class FileService {
    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 255;
    // 해상도 폭탄(작은 파일이 메모리에서 거대해지는 이미지)을 막는다.
    private static final int MAX_PIXELS = 40_000_000;

    private final FileRepository files;
    private final UserRepository users;
    private final ReviewRepository reviews;
    private final QnaRepository qnas;
    private final AccommodationFileRepository accommodationImages;
    private final Path baseDir;

    public FileService(FileRepository files, UserRepository users, ReviewRepository reviews, QnaRepository qnas,
                       AccommodationFileRepository accommodationImages,
                       @Value("${staybooking.upload.dir}") String uploadDir) {
        this.files = files;
        this.users = users;
        this.reviews = reviews;
        this.qnas = qnas;
        this.accommodationImages = accommodationImages;
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public UploadResponse upload(Long userId, MultipartFile upload) {
        UserEntity user = activeUser(userId);
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_FILE_SIZE) throw invalidFile();
        String originalName = cleanName(upload.getOriginalFilename());
        String extension = singleExtension(originalName);

        byte[] bytes = read(upload);
        ImageType type = ImageType.detect(bytes)
            .filter(detected -> detected.allowsExtension(extension))
            .orElseThrow(FileService::invalidFile);
        byte[] stored = type.reencodeFormat() == null ? bytes : reencode(bytes, type);

        String storedName = UUID.randomUUID() + "." + type.storedExtension();
        Path target = baseDir.resolve(storedName);
        write(target, stored);
        FileEntity saved = files.save(new FileEntity(user, originalName, storedName, target.toString(),
            type.mimeType(), stored.length, LocalDateTime.now(ZoneOffset.UTC)));
        return UploadResponse.from(saved);
    }

    // 후기·공개 문의에 연결된 사진은 누구나 볼 수 있고, 비밀 문의 사진과 아직 연결 전인 사진은
    // 올린 사람과 관리자만 볼 수 있다.
    // 볼 수 없는 파일은 존재 여부를 숨기려고 403 대신 404로 응답한다.
    @Transactional(readOnly = true)
    public StoredImage load(Long actorId, Long fileId) {
        FileEntity file = files.findById(fileId).orElseThrow(FileService::notFound);
        boolean visible = reviews.existsByFilesFileIdAndDeletedFalse(fileId)
            || qnas.existsByFilesFileIdAndDeletedFalseAndSecretFalse(fileId)
            || accommodationImages.isPublicImage(fileId, AccommodationStatus.ACTIVE)
            || isUploader(file, actorId) || isAdmin(actorId);
        if (!visible) throw notFound();
        Path path = safePath(file);
        try {
            return new StoredImage(file.getFileId(), file.getMimeType(), Files.readAllBytes(path));
        } catch (IOException e) {
            throw notFound();
        }
    }

    @Transactional
    public void delete(Long actorId, Long fileId) {
        FileEntity file = files.findById(fileId).orElseThrow(FileService::notFound);
        if (!isUploader(file, actorId)) throw notFound();
        if (reviews.existsByFilesFileId(fileId) || qnas.existsByFilesFileId(fileId)
                || accommodationImages.existsByFileFileId(fileId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "글에 연결된 사진은 글을 수정해서 빼 주세요.");
        }
        files.delete(file);
        try {
            Files.deleteIfExists(safePath(file));
        } catch (IOException e) {
            // DB 기록은 지웠으므로 남은 파일은 운영자가 정리한다.
        }
    }

    // 다른 기능(후기 등)에서 첨부할 때 쓴다. 본인이 올린 파일만 연결할 수 있다.
    @Transactional(readOnly = true)
    public FileEntity ownedFile(Long userId, Long fileId) {
        FileEntity file = files.findById(fileId).orElseThrow(FileService::notFound);
        if (!isUploader(file, userId)) throw notFound();
        return file;
    }

    private UserEntity activeUser(Long userId) {
        return users.findById(userId)
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    }

    private boolean isUploader(FileEntity file, Long actorId) {
        return actorId != null && file.getUploadedBy().getUserId().equals(actorId);
    }

    private boolean isAdmin(Long actorId) {
        return actorId != null && users.findById(actorId)
            .filter(user -> user.getRole() == UserRole.ROLE_ADMIN && user.getStatus() == UserStatus.ACTIVE)
            .isPresent();
    }

    // 브라우저가 보낸 경로(C:\fakepath\a.png)는 버리고 이름만 쓴다. 널 바이트·제어 문자는 거부한다.
    private static String cleanName(String raw) {
        if (raw == null) throw invalidFile();
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1).trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || name.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7F)) {
            throw invalidFile();
        }
        return name;
    }

    // shell.jsp.png 같은 이중 확장자는 거부하고, 점이 정확히 하나인 이름만 받는다.
    private static String singleExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot != name.indexOf('.') || dot == name.length() - 1) throw invalidFile();
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] read(MultipartFile upload) {
        try (InputStream in = upload.getInputStream()) {
            byte[] bytes = in.readNBytes((int) MAX_FILE_SIZE + 1);
            if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE) throw invalidFile();
            return bytes;
        } catch (IOException e) {
            throw invalidFile();
        }
    }

    // 다시 그려 저장하면 이미지 뒤에 숨긴 코드와 EXIF(위치정보 등)가 사라진다.
    private static byte[] reencode(byte[] bytes, ImageType type) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw invalidFile();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) throw invalidFile();
                BufferedImage image = reader.read(0);
                if (type == ImageType.JPEG && image.getColorModel().hasAlpha()) throw invalidFile();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, type.reencodeFormat(), out)) throw invalidFile();
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) throw apiException;
            throw invalidFile();
        }
    }

    private void write(Path target, byte[] bytes) {
        try {
            Files.createDirectories(baseDir);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            // 업로드 폴더의 파일은 읽기만 가능하게 두어 실행되지 않게 한다. (Windows는 지원하지 않아 건너뜀)
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // 개발 PC(Windows)
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store upload", e);
        }
    }

    // DB에 저장된 경로를 그대로 믿지 않고, 업로드 폴더 안의 파일인지 다시 확인한다.
    private Path safePath(FileEntity file) {
        Path path = baseDir.resolve(file.getStoredName()).normalize();
        if (!path.startsWith(baseDir)) throw notFound();
        return path;
    }

    private static ApiException invalidFile() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE",
            "jpg, png, webp 이미지(5MB 이하)만 올릴 수 있습니다.", List.of("file"));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "파일을 찾을 수 없습니다.");
    }

    public record StoredImage(Long fileId, String mimeType, byte[] bytes) { }
}
