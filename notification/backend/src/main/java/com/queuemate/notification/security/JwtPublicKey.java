package com.queuemate.notification.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 검증에 쓰는 공개 키를 구한다 — 환경변수 {@code JWT_PUBLIC_KEY} 의 PEM 이 먼저이고, 비어 있으면 {@code JWT_PUBLIC_KEY_FILE} 의 파일을 읽는다.
 *
 * <p><b>둘 다 없으면 기동을 막는다.</b> 이 서비스는 연결을 연 사람의 채널로 알림을 흘려보내므로, 인증 없이 뜨면 아무나 남의 알림을 받는다.
 * {@code platform} 과 달리 키를 새로 만들지 않는다 — 여기서 만든 키로는 {@code platform} 이 찍은 토큰을 검증할 수 없다.
 * 개인 키는 받지도 읽지도 않는다({@code platform} 만 갖는다 — {@code ../platform/CLAUDE.md} §5.1 (가)).
 */
@Slf4j
public final class JwtPublicKey {

    private JwtPublicKey() {
    }

    public static RSAPublicKey load(JwtProperties properties) {
        if (hasText(properties.publicKey())) {
            return parse(properties.publicKey());
        }
        if (!hasText(properties.publicKeyFile())) {
            throw new IllegalStateException("JWT_PUBLIC_KEY 와 JWT_PUBLIC_KEY_FILE 이 둘 다 비어 있다 — 공개 키 없이는 기동하지 않는다");
        }
        Path file = Path.of(properties.publicKeyFile());
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("JWT_PUBLIC_KEY 가 비어 있고 JWT_PUBLIC_KEY_FILE 의 파일이 없다 — 공개 키 없이는 기동하지 않는다. "
                    + "로컬이면 platform 을 한 번 띄워 개발용 키(platform/backend/.dev-keys/public.pem)를 만들어라. file="
                    + file.toAbsolutePath());
        }
        try {
            log.warn("JWT_PUBLIC_KEY 가 비어 있어 파일에서 공개 키를 읽는다 — 운영에서는 환경변수로 넣어라. file={}", file.toAbsolutePath());
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("공개 키 파일을 읽지 못했다 file=" + file.toAbsolutePath(), e);
        }
    }

    /**
     * 머리 · 꼬리 줄과 공백을 걷어 내고 Base64 를 푼다. 환경변수에는 줄바꿈이 글자 그대로의 {@code \n} 두 글자로 들어오기도 한다 —
     * 그것도 걷어 낸다({@code platform} 의 {@code JwtKeys} 와 같은 규칙).
     */
    static RSAPublicKey parse(String pem) {
        try {
            String base64 = pem.replace("\\n", "")
                    .replaceAll("-----[A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("공개 키를 읽지 못했다 — RSA 의 X.509 PEM(-----BEGIN PUBLIC KEY-----)이어야 한다", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
