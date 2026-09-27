package com.queuemate.common.security;

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
 * 검증에 쓰는 공개 키를 구한다 — 환경변수의 PEM 이 먼저이고, 없으면 파일이다. <b>둘 다 없으면 기동을 막는다</b> —
 * 키 없이 뜨면 모든 요청이 401 이 되는데 그 원인이 로그에서 잘 안 보인다.
 *
 * <p>{@code platform} 의 {@code JwtKeys} 에서 공개 키 쪽만 옮겼다. 개인 키는 다루지 않는다.
 */
@Slf4j
public final class JwtPublicKeys {

    private JwtPublicKeys() {
    }

    public static RSAPublicKey load(JwtProperties properties) {
        if (hasText(properties.publicKey())) {
            return parse(properties.publicKey());
        }
        if (hasText(properties.publicKeyFile())) {
            Path file = Path.of(properties.publicKeyFile());
            if (Files.isReadable(file)) {
                try {
                    log.info("JWT_PUBLIC_KEY 가 비어 있어 파일에서 공개 키를 읽는다 file={}", file.toAbsolutePath());
                    return parse(Files.readString(file));
                } catch (IOException e) {
                    throw new UncheckedIOException("공개 키 파일을 읽지 못했다 file=" + file.toAbsolutePath(), e);
                }
            }
            throw new IllegalStateException("공개 키가 없다 — JWT_PUBLIC_KEY 를 주거나 JWT_PUBLIC_KEY_FILE 의 파일을 두어라"
                    + " (로컬은 platform 을 한 번 띄우면 ../../platform/backend/.dev-keys/public.pem 이 생긴다). file="
                    + file.toAbsolutePath());
        }
        throw new IllegalStateException("공개 키가 없다 — JWT_PUBLIC_KEY 또는 JWT_PUBLIC_KEY_FILE 을 주어라");
    }

    static RSAPublicKey parse(String pem) {
        try {
            String base64 = pem.replace("\\n", "")
                    .replaceAll("-----[A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("공개 키를 읽지 못했다 — X.509 PEM(-----BEGIN PUBLIC KEY-----)이어야 한다", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
