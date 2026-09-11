package com.queuemate.user.avatar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 업로드된 프로필 사진의 저장 위치와 한도.
 *
 * <p>dir은 컨테이너 안 경로이고 운영에서는 볼륨을 붙인다. 인스턴스를 늘리면
 * 이 디렉터리를 공유하거나 객체 스토리지로 옮겨야 한다 (docs/11).
 */
@ConfigurationProperties(prefix = "queuemate.avatar")
public record AvatarProperties(
        String dir,
        DataSize maxUploadSize,
        /** 저장하는 정사각 한 변의 픽셀. 목록에서 38px, 내 정보에서 64px로 쓰인다. */
        int edgePixels,
        /**
         * 디코딩을 허용하는 원본 픽셀 수 상한.
         *
         * <p>압축률이 극단적으로 높은 이미지는 파일이 작아도 펼치면 수 GB가 된다.
         * 5MiB 제한만으로는 막히지 않으므로 헤더의 크기를 먼저 읽고 거른다.
         */
        long maxSourcePixels
) {
}
