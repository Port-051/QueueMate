package com.queuemate.platform.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서명 키를 구하는 세 갈래 — 환경변수의 PEM / 개발용 키 새로 만들기 / 있던 개발용 키 읽기. 스프링 없이 돈다.
 */
class JwtKeysTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("키가 비어 있으면 개발용 키를 PEM 두 파일로 만들고, 다음에는 같은 키를 다시 읽는다")
    void createsDevKeysOnceAndReusesThem() throws Exception
    {
        Path dir = tempDir.resolve(".dev-keys");

        JwtKeys first = JwtKeys.load(properties(null, "", dir));
        JwtKeys second = JwtKeys.load(properties(null, "", dir));

        assertThat(Files.readString(dir.resolve("private.pem"))).startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(Files.readString(dir.resolve("public.pem"))).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(first.publicKey().getModulus().bitLength()).isEqualTo(2048);
        // 재시작해도 로그인이 풀리지 않고, 옆 서비스가 읽어 간 public.pem 이 계속 맞는다
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
        assertThat(second.privateKey()).isEqualTo(first.privateKey());
    }

    @Test
    @DisplayName("환경변수의 PEM 을 읽는다 — 줄바꿈이 글자 그대로의 \\n 으로 들어와도 읽는다. 개발용 키는 만들지 않는다")
    void readsPemFromProperties() throws Exception
    {
        JwtKeys original = JwtKeys.loadOrCreateDevKeys(tempDir.resolve("source"));
        String privatePem = JwtKeys.toPem("PRIVATE KEY", original.privateKey().getEncoded());
        String publicPem = JwtKeys.toPem("PUBLIC KEY", original.publicKey().getEncoded());
        Path unused = tempDir.resolve("unused");

        JwtKeys loaded = JwtKeys.load(properties(privatePem, publicPem, unused));
        JwtKeys escaped = JwtKeys.load(properties(
                privatePem.replace("\n", "\\n"), publicPem.replace("\n", "\\n"), unused));

        assertThat(loaded.publicKey()).isEqualTo(original.publicKey());
        assertThat(loaded.privateKey()).isEqualTo(original.privateKey());
        assertThat(escaped.privateKey()).isEqualTo(original.privateKey());
        assertThat(unused).doesNotExist();
    }

    @Test
    @DisplayName("둘 중 하나만 주면 기동을 막는다 — 조용히 개발용 키로 넘어가지 않는다")
    void rejectsHalfConfiguredKeys()
    {
        JwtKeys original = JwtKeys.loadOrCreateDevKeys(tempDir.resolve("source"));
        String publicPem = JwtKeys.toPem("PUBLIC KEY", original.publicKey().getEncoded());
        Path unused = tempDir.resolve("unused");

        assertThatThrownBy(() -> JwtKeys.load(properties(" ", publicPem, unused)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(unused).doesNotExist();
        // 깨진 PEM 도 기동을 막는다
        assertThatThrownBy(() -> JwtKeys.load(properties("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----", publicPem, unused)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtProperties properties(String privateKey, String publicKey, Path devKeyDir)
    {
        return new JwtProperties(privateKey, publicKey, "dev-1", Duration.ofMinutes(15), Duration.ofDays(7),
                devKeyDir.toString());
    }
}
