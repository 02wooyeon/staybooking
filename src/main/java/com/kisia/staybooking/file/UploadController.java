package com.kisia.staybooking.file;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 주소는 정의서(API 명세서 UPLOADS-001/002)와 화면(review.js)을 따른다.
@RestController
@RequestMapping("/api/v1/uploads/Images")
public class UploadController {
    private final FileService files;

    public UploadController(FileService files) {
        this.files = files;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@AuthenticationPrincipal AuthenticatedUser me,
                                 @RequestParam("file") MultipartFile file) {
        return files.upload(me.userId(), file);
    }

    // 브라우저가 내용을 다른 형식(HTML 등)으로 추측해 실행하지 않도록 형식을 고정하고 스크립트를 막는다.
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AuthenticatedUser me,
                                        @PathVariable @Positive Long fileId) {
        FileService.StoredImage image = files.load(me == null ? null : me.userId(), fileId);
        String extension = ImageType.fromMimeType(image.mimeType()).map(ImageType::storedExtension).orElse("bin");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(image.fileId() + "." + extension).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(image.bytes());
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable @Positive Long fileId) {
        files.delete(me.userId(), fileId);
    }
}
