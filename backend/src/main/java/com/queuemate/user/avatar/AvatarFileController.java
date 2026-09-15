package com.queuemate.user.avatar;

import com.queuemate.common.error.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * 업로드된 사진을 내려준다. <b>인증하지 않는다</b> (SecurityConfig에서 열어 둔다).
 *
 * <p>access token은 localStorage에 있고 Authorization 헤더로만 나가는데 {@code <img src>}는
 * 그 헤더를 붙일 수 없다. 대신 objectId를 추측 불가능한 UUID로 두고 그것을 아는 쪽만 받는다.
 * 프리셋 아바타가 {@code /avatars/*.webp}로 이미 공개인 것과 같은 수준이다.
 */
@RestController
@RequestMapping("/api/v1/files/avatars")
public class AvatarFileController {

    private final AvatarService avatarService;

    public AvatarFileController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/{objectId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID objectId) {
        byte[] png = avatarService.read(objectId)
                .orElseThrow(() -> new NotFoundException("AVATAR_NOT_FOUND", "사진을 찾을 수 없다"));
        return ResponseEntity.ok()
                // 내용이 바뀌면 objectId가 바뀐다. 같은 URL이 다른 그림을 주는 일은 없다.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
