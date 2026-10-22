package com.kisia.staybooking.file;

// 저장 위치(file_path)와 저장 이름은 응답에 넣지 않는다.
public record UploadResponse(Long fileId, String url, String originalName, String mimeType, long size) {
    public static String urlOf(Long fileId) {
        return "/api/v1/uploads/Images/" + fileId;
    }

    static UploadResponse from(FileEntity file) {
        return new UploadResponse(file.getFileId(), urlOf(file.getFileId()), file.getOriginalName(),
            file.getMimeType(), file.getFileSize());
    }
}
