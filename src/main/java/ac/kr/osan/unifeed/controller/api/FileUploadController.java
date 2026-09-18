package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// ############################################################################
// FileUploadController — 게시글/리뷰 등에서 공용으로 쓰는 이미지 업로드
// ----------------------------------------------------------------------------
// 파일을 저장하고 접근 가능한 URL만 돌려준다. 그 URL을 각 폼의 기존 imgUrl
// 필드에 그대로 넣어 쓰므로, Post 등 엔티티/스키마는 손댈 필요가 없다.
// ############################################################################
@RestController
@RequestMapping("/api/uploads")
public class FileUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "업로드할 파일을 선택해주세요."));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미지는 5MB 이하만 업로드할 수 있습니다."));
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("message", "jpg, png, gif, webp 이미지만 업로드할 수 있습니다."));
        }

        try {
            // 확장자/Content-Type은 클라이언트가 속일 수 있으므로, 실제 픽셀 데이터로 디코딩되는지 확인한다.
            BufferedImage decoded;
            try (var in = file.getInputStream()) {
                decoded = ImageIO.read(in);
            }
            if (decoded == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "올바른 이미지 파일이 아닙니다."));
            }

            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + ext;
            file.transferTo(dir.resolve(filename));
            return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "이미지 저장에 실패했습니다."));
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
