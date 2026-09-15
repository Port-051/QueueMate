package com.queuemate.user.avatar;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** 로컬 디렉터리 한 곳에 {objectId}.png로 담는다. */
@Component
public class LocalAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalAvatarStorage.class);

    private final Path dir;

    public LocalAvatarStorage(AvatarProperties properties) {
        this.dir = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureDirectory() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // 여기서 죽는 편이 낫다. 업로드를 받고 나서야 못 쓴다는 것을 아는 것보다 낫다.
            throw new IllegalStateException("아바타 저장 디렉터리를 만들 수 없다: " + dir, e);
        }
        // 있는 것과 쓸 수 있는 것은 다르다. 볼륨 마운트 지점은 컨테이너가 root 소유로
        // 만들어 두기 때문에, 디렉터리 존재만 확인하면 첫 업로드에서야 500으로 드러난다.
        if (!Files.isWritable(dir)) {
            throw new IllegalStateException("아바타 저장 디렉터리에 쓸 수 없다: " + dir);
        }
        log.info("아바타 저장 디렉터리 dir={}", dir);
    }

    @Override
    public UUID store(byte[] png) {
        UUID objectId = UUID.randomUUID();
        Path target = pathOf(objectId);
        // 쓰다 만 파일이 보이면 깨진 이미지가 내려간다. 임시 파일에 다 쓰고 옮긴다.
        Path temp = dir.resolve(objectId + ".tmp");
        try {
            Files.write(temp, png);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return objectId;
        } catch (IOException e) {
            quietlyDelete(temp);
            // 계약에 이 상황을 위한 code가 없다. 디스크가 찼거나 마운트가 빠진 것이고
            // 사용자가 고칠 수 있는 일이 아니다. 4xx로 위장하지 않고 500으로 둔다.
            throw new UncheckedIOException("아바타를 저장하지 못했다", e);
        }
    }

    @Override
    public Optional<byte[]> read(UUID objectId) {
        Path path = pathOf(objectId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            log.warn("아바타를 읽지 못했다 objectId={}", objectId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID objectId) {
        quietlyDelete(pathOf(objectId));
    }

    /**
     * objectId가 UUID라 경로 조작이 들어갈 자리가 없다. 그래도 결과가 디렉터리
     * 안인지 확인한다. 타입이 바뀌는 날 조용히 뚫리지 않게 한다.
     */
    private Path pathOf(UUID objectId) {
        Path path = dir.resolve(objectId + ".png").normalize();
        if (!path.startsWith(dir)) {
            throw new IllegalArgumentException("저장 디렉터리 밖이다");
        }
        return path;
    }

    private void quietlyDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 지우지 못해도 요청은 성공한다. 남은 파일은 가리키는 사람이 없는 쓰레기일 뿐이다.
            log.warn("아바타 파일을 지우지 못했다 path={}", path, e);
        }
    }
}
