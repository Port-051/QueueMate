package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** contracts/openapi.yaml `/users/me/avatar`, `/files/avatars/{objectId}`. */
class AvatarApiContractTest extends ApiContractTestSupport {

    private static final String ME = "/api/v1/users/me";
    private static final String AVATAR = "/api/v1/users/me/avatar";
    private static final String URL_PREFIX = "/api/v1/files/avatars/";

    private static byte[] image(String format, int width, int height) {
        int type = "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage source = new BufferedImage(width, height, type);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(source, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** 업로드는 JSON이 아니라 multipart라 support의 send()를 쓸 수 없다. */
    private ResponseEntity<String> upload(UUID actor, String filename, MediaType type, byte[] bytes) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(type);
        ByteArrayResource part = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, HttpEntity<?>> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(part, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (actor != null) {
            headers.setBearerAuth(tokenService.issueAccessToken(actor));
        }
        return http.exchange(AVATAR, HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<byte[]> fetch(String url) {
        // 토큰을 일부러 붙이지 않는다. img 태그가 보내는 요청과 같은 모양이어야 한다.
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
    }

    @Test
    @DisplayName("TC-AVATAR-01 업로드하면 avatarUrl이 저장된 사진을 가리킨다")
    void uploadReplacesAvatarUrl() {
        UUID alpha = alpha();

        JsonNode profile = body(upload(alpha, "me.png", MediaType.IMAGE_PNG, image("png", 900, 600)));

        String url = profile.path("avatarUrl").asText();
        assertTrue(url.startsWith(URL_PREFIX), "실제 avatarUrl=" + url);
        // 프로필은 여전히 3필드다. 업로드가 계약을 넓히지 않는다.
        assertEquals(3, profile.size());
    }

    @Test
    @DisplayName("TC-AVATAR-02 올린 사진은 토큰 없이도 PNG로 내려온다")
    void uploadedPhotoIsServedAnonymously() {
        UUID alpha = alpha();
        String url = body(upload(alpha, "me.jpg", MediaType.IMAGE_JPEG, image("jpeg", 800, 800)))
                .path("avatarUrl").asText();

        ResponseEntity<byte[]> served = fetch(url);

        assertEquals(200, served.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, served.getHeaders().getContentType());
        // JPEG를 올렸어도 내려오는 것은 PNG다. 원본 바이트를 그대로 돌려주지 않는다.
        byte[] png = served.getBody();
        assertTrue(png != null && png.length > 0);
        assertEquals((byte) 0x89, png[0]);
    }

    @Test
    @DisplayName("TC-AVATAR-03 이미지가 아니면 400이다")
    void rejectsNonImage() {
        UUID alpha = alpha();

        ResponseEntity<String> response = upload(
                alpha, "me.png", MediaType.IMAGE_PNG, "이미지가 아니다".getBytes(StandardCharsets.UTF_8));

        // 파일명과 Content-Type은 PNG라고 말하지만 내용이 아니다.
        assertError(response, 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-AVATAR-04 받지 않는 포맷은 415다")
    void rejectsDisallowedFormat() {
        UUID alpha = alpha();

        ResponseEntity<String> response = upload(alpha, "me.gif", MediaType.IMAGE_GIF, image("gif", 300, 300));

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("TC-AVATAR-05 한도를 넘으면 413이다")
    void rejectsOversizedUpload() {
        UUID alpha = alpha();

        ResponseEntity<String> response = upload(
                alpha, "huge.png", MediaType.IMAGE_PNG, new byte[6 * 1024 * 1024]);

        assertError(response, 413, "AVATAR_TOO_LARGE");
    }

    @Test
    @DisplayName("TC-AVATAR-06 다시 올리면 이전 파일은 사라진다")
    void replacingDeletesThePreviousFile() {
        UUID alpha = alpha();
        String first = body(upload(alpha, "1.png", MediaType.IMAGE_PNG, image("png", 400, 400)))
                .path("avatarUrl").asText();

        String second = body(upload(alpha, "2.png", MediaType.IMAGE_PNG, image("png", 500, 500)))
                .path("avatarUrl").asText();

        assertNotEquals(first, second);
        assertEquals(404, fetch(first).getStatusCode().value(), "직전 파일이 남아 있다");
        assertEquals(200, fetch(second).getStatusCode().value());
    }

    @Test
    @DisplayName("TC-AVATAR-07 프리셋으로 바꾸거나 지우면 올린 파일도 사라진다")
    void switchingAwayDeletesTheUploadedFile() {
        UUID alpha = alpha();
        String uploaded = body(upload(alpha, "me.png", MediaType.IMAGE_PNG, image("png", 400, 400)))
                .path("avatarUrl").asText();

        // 프리셋 선택은 업로드와 같은 avatarUrl 한 칸을 쓴다.
        patch(ME, alpha, "{ \"avatarUrl\": \"/avatars/avatar-01.webp\" }");

        assertEquals(404, fetch(uploaded).getStatusCode().value());
    }

    @Test
    @DisplayName("TC-AVATAR-08 지우면 파일도 함께 사라진다")
    void clearingDeletesTheUploadedFile() {
        UUID alpha = alpha();
        String uploaded = body(upload(alpha, "me.png", MediaType.IMAGE_PNG, image("png", 400, 400)))
                .path("avatarUrl").asText();

        JsonNode cleared = body(patch(ME, alpha, "{ \"avatarUrl\": null }"));

        assertTrue(cleared.path("avatarUrl").isNull());
        assertEquals(404, fetch(uploaded).getStatusCode().value());
    }

    @Test
    @DisplayName("TC-AVATAR-09 업로드는 인증이 필요하다")
    void uploadRequiresAuthentication() {
        ResponseEntity<String> response = upload(null, "me.png", MediaType.IMAGE_PNG, image("png", 300, 300));

        assertError(response, 401, "UNAUTHORIZED");
    }

    @Test
    @DisplayName("TC-AVATAR-10 없는 사진은 404 AVATAR_NOT_FOUND다")
    void missingPhotoIsNotFound() {
        ResponseEntity<String> response = get(URL_PREFIX + NONE, null);

        assertError(response, 404, "AVATAR_NOT_FOUND");
    }
}
