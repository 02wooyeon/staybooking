package com.kisia.staybooking.file;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

// 허용하는 이미지 형식. gif·svg는 스크립트를 담을 수 있어 받지 않는다.
public enum ImageType {
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"), "jpg"),
    PNG("image/png", "png", Set.of("png"), "png"),
    WEBP("image/webp", "webp", Set.of("webp"), null);

    private final String mimeType;
    private final String storedExtension;
    private final Set<String> allowedExtensions;
    // ImageIO로 다시 인코딩할 형식 이름. webp는 JDK가 지원하지 않아 원본을 그대로 저장한다.
    private final String reencodeFormat;

    ImageType(String mimeType, String storedExtension, Set<String> allowedExtensions, String reencodeFormat) {
        this.mimeType = mimeType;
        this.storedExtension = storedExtension;
        this.allowedExtensions = allowedExtensions;
        this.reencodeFormat = reencodeFormat;
    }

    public String mimeType() { return mimeType; }
    public String storedExtension() { return storedExtension; }
    public String reencodeFormat() { return reencodeFormat; }

    public boolean allowsExtension(String extension) {
        return allowedExtensions.contains(extension);
    }

    // 확장자나 Content-Type 헤더가 아니라 파일 앞부분(매직 바이트)으로 실제 형식을 판단한다.
    public static Optional<ImageType> detect(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) return Optional.of(JPEG);
        if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(PNG);
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<ImageType> fromMimeType(String mimeType) {
        return Arrays.stream(values()).filter(type -> type.mimeType.equals(mimeType)).findFirst();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
