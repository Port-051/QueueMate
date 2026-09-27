package com.queuemate.notification.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 키를 구하는 순서 — {@code JWT_PUBLIC_KEY} 가 먼저, 비면 {@code JWT_PUBLIC_KEY_FILE}, 둘 다 없으면 <b>기동 실패</b>.
 */
class JwtPublicKeyTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("둘 다 비어 있으면 기동하지 않는다")
    void 둘_다_없으면_실패() {
        assertThatThrownBy(() -> JwtPublicKey.load(new JwtProperties("", "")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtPublicKey.load(new JwtProperties(null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("JWT_PUBLIC_KEY 가 비고 파일이 없으면 기동하지 않는다")
    void 파일이_없으면_실패() {
        assertThatThrownBy(() -> JwtPublicKey.load(new JwtProperties("", dir.resolve("public.pem").toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY_FILE");
    }

    @Test
    @DisplayName("JWT_PUBLIC_KEY 가 비면 파일에서 읽는다")
    void 파일에서_읽는다() throws Exception {
        RSAPublicKey key = newKey();
        Path file = dir.resolve("public.pem");
        Files.writeString(file, TestTokens.toPem(key));

        assertThat(JwtPublicKey.load(new JwtProperties("", file.toString())).getModulus()).isEqualTo(key.getModulus());
    }

    @Test
    @DisplayName("JWT_PUBLIC_KEY 가 있으면 파일 설정은 보지 않는다 — 줄바꿈이 글자 \\n 으로 들어와도 읽는다")
    void 환경변수가_먼저다() throws Exception {
        RSAPublicKey key = newKey();
        String oneLine = TestTokens.toPem(key).replace("\n", "\\n");

        assertThat(JwtPublicKey.load(new JwtProperties(oneLine, dir.resolve("없다.pem").toString())).getModulus())
                .isEqualTo(key.getModulus());
    }

    @Test
    @DisplayName("PEM 이 아니면 기동하지 않는다")
    void 깨진_PEM_은_실패() {
        assertThatThrownBy(() -> JwtPublicKey.load(new JwtProperties("not-a-pem", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RSAPublicKey newKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return (RSAPublicKey) generator.generateKeyPair().getPublic();
    }
}
