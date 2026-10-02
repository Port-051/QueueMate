package com.queuemate.common.security;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 모든 테스트 컨텍스트에 {@link TestJwt} 의 공개 키를 넣는다 — {@code src/test/resources/META-INF/spring.factories} 로 등록했다.
 * 이것이 없으면 테스트가 {@code ../../platform/backend/.dev-keys/public.pem} 을 찾다 기동에 실패한다(작업 폴더 밖이라 없을 수 있다).
 * 맨 앞에 넣어 환경변수 {@code JWT_PUBLIC_KEY} 보다 우선한다.
 */
public class TestJwtKeyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testJwtKey",
                Map.of("queuemate.jwt.public-key", TestJwt.publicKeyPem())));
    }
}
