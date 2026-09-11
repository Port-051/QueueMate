package com.queuemate.user.avatar;

import com.queuemate.common.error.PayloadTooLargeException;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import com.queuemate.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 업로드된 사진과 {@code users.avatar_url} 한 칸을 잇는다.
 *
 * <p>프리셋 아바타(`/avatars/*.webp`)와 업로드한 사진은 같은 칸을 쓴다. 그래서
 * "무엇으로 바꾸든 직전에 업로드했던 파일은 지운다"가 한 곳에 있어야 한다.
 * 그 자리가 {@link #changeAvatar}다.
 */
@Service
public class AvatarService {

    /** 저장된 사진을 가리키는 URL의 앞부분. contracts/openapi.yaml의 서빙 경로와 같아야 한다. */
    static final String URL_PREFIX = "/api/v1/files/avatars/";

    private final UserRepository users;
    private final AvatarStorage storage;
    private final AvatarImages images;
    private final AvatarProperties properties;

    public AvatarService(UserRepository users, AvatarStorage storage,
                         AvatarImages images, AvatarProperties properties) {
        this.users = users;
        this.storage = storage;
        this.images = images;
        this.properties = properties;
    }

    @Transactional
    public User upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있다");
        }
        // 서블릿 컨테이너의 한도가 먼저 걸리는 것이 보통이지만, 그쪽 설정이 느슨해도
        // 계약상의 한도는 여기서 지켜져야 한다.
        if (file.getSize() > properties.maxUploadSize().toBytes()) {
            throw new PayloadTooLargeException("AVATAR_TOO_LARGE", "사진이 너무 큽니다");
        }
        byte[] source = readBytes(file);
        byte[] png = images.toSquarePng(source);

        UUID objectId = storage.store(png);
        try {
            return changeAvatar(userId, URL_PREFIX + objectId);
        } catch (RuntimeException e) {
            // 프로필에 못 붙인 파일은 가리키는 사람이 없다. 그 자리에서 지운다.
            storage.delete(objectId);
            throw e;
        }
    }

    @Transactional
    public User changeAvatar(UUID userId, String newAvatarUrl) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없다"));
        String previous = user.getAvatarUrl();
        user.changeAvatarUrl(newAvatarUrl);
        User saved = users.saveAndFlush(user);
        discardReplaced(previous, newAvatarUrl);
        return saved;
    }

    /**
     * 프로필에서 내려간 사진 파일을 지운다.
     *
     * <p>{@code PATCH /users/me}로 프리셋을 고르거나 null로 지우는 경로도 여기를 지나야 한다.
     * 지나지 않으면 아무도 가리키지 않는 파일이 디스크에 쌓인다. 프리셋 URL과 외부 URL은
     * 우리 것이 아니므로 아무 일도 하지 않는다.
     */
    public void discardReplaced(String previousAvatarUrl, String newAvatarUrl) {
        UUID next = objectIdOf(newAvatarUrl).orElse(null);
        objectIdOf(previousAvatarUrl)
                .filter(previous -> !previous.equals(next))
                .ifPresent(storage::delete);
    }

    public Optional<byte[]> read(UUID objectId) {
        return storage.read(objectId);
    }

    /** 우리가 저장한 사진을 가리키는 URL이면 그 objectId. 프리셋이나 외부 URL이면 비어 있다. */
    static Optional<UUID> objectIdOf(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(URL_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(avatarUrl.substring(URL_PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드를 읽지 못했다", e);
        }
    }
}
