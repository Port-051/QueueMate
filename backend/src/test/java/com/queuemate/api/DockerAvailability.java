package com.queuemate.api;

import org.testcontainers.DockerClientFactory;

/**
 * Docker 유무만 알려 준다.
 *
 * <p>컨테이너 필드를 가진 클래스에서 이 판정을 하면 안 된다. JUnit이 조건을 평가하려고
 * 그 클래스를 건드리는 순간 static 초기화가 돌아 컨테이너를 띄우려 하고,
 * Docker가 없으면 "비활성화"가 아니라 초기화 오류로 죽는다.
 */
final class DockerAvailability {

    private DockerAvailability() {
    }

    static boolean isAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
