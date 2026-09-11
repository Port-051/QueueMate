package com.queuemate.user.avatar;

import java.util.Optional;
import java.util.UUID;

/**
 * 정규화가 끝난 PNG 바이트를 보관한다.
 *
 * <p>인터페이스로 두는 이유는 지금의 로컬 디렉터리 구현을 나중에 객체 스토리지로
 * 바꿀 때 호출하는 쪽이 바뀌지 않게 하기 위해서다. 그 외의 목적은 없다.
 */
public interface AvatarStorage {

    /** 저장하고 그 객체를 가리키는 id를 돌려준다. */
    UUID store(byte[] png);

    Optional<byte[]> read(UUID objectId);

    /** 없는 객체를 지우는 것은 오류가 아니다. */
    void delete(UUID objectId);
}
