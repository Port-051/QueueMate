package com.queuemate.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 공개 키를 구하는 순서 — 환경변수의 PEM → 파일 → 둘 다 없으면 기동 실패 */
class JwtPublicKeysTest {

    @Test
    void PEM_이_있으면_그것을_쓴다() {
        assertThat(JwtPublicKeys.load(new JwtProperties(TestJwt.publicKeyPem(), "/no/such/file"))).isNotNull();
    }

    @Test
    void 환경변수에_글자_그대로의_역슬래시_n_이_들어와도_읽는다() {
        String escaped = TestJwt.publicKeyPem().replace("\n", "\\n");
        assertThat(JwtPublicKeys.load(new JwtProperties(escaped, null))).isNotNull();
    }

    @Test
    void PEM_이_없으면_파일을_읽는다(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("public.pem");
        Files.writeString(file, TestJwt.publicKeyPem());
        assertThat(JwtPublicKeys.load(new JwtProperties("", file.toString()))).isNotNull();
    }

    @Test
    void 둘_다_없으면_기동하지_않는다(@TempDir Path dir) {
        assertThatThrownBy(() -> JwtPublicKeys.load(new JwtProperties(null, dir.resolve("missing.pem").toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> JwtPublicKeys.load(new JwtProperties(null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void PEM_이_아니면_기동하지_않는다() {
        assertThatThrownBy(() -> JwtPublicKeys.load(new JwtProperties("not a key", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X.509");
    }
}
