package com.queuemate.platform.common.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 서명 키 한 쌍(RSA 2048)을 구한다 — 환경변수의 PEM 이 있으면 그것을, <b>둘 다 비어 있으면</b> 개발용 키 파일을 폴더에서 읽는다.
 * 파일이 없으면 {@code JWT_GENERATE_DEV_KEYS=true} 일 때만 만들고, 아니면 기동을 거부한다
 * ({@code contracts/platform-api.md} "access 토큰" 표의 "키" · P-51).
 *
 * <p>개발용 키를 파일로 남기는 이유 — 띄울 때마다 새로 만들면 재시작할 때마다 모든 로그인이 풀리고,
 * 옆 서비스({@code matching} · {@code notification})가 검증에 쓸 공개 키를 가져갈 곳이 없다. 옆 서비스는 그 폴더의 {@code public.pem} 을 읽는다.
 *
 * <p>하나만 준 것은 설정 실수다 — 조용히 개발용 키로 넘어가면 운영에서 아무도 검증할 수 없는 토큰을 찍어 낸다. 그래서 기동을 막는다.
 *
 * <p><b>키도 파일도 없으면 만들지 않고 기동을 거부하는 이유</b>(2026-10-02 배포 점검 · 소유자 결정 — P-51) — 컨테이너(ECS Fargate)에는
 * 그 폴더가 없다. 운영에서 키를 빠뜨린 채 예전처럼 조용히 만들어 뜨면 재시작마다 다른 키가 생기고(모든 로그인이 풀린다),
 * 태스크 둘이 서로 다른 키를 가져 한쪽이 찍은 토큰을 다른 쪽이 거절하며, 옆 서비스의 공개 키와도 맞지 않는다.
 * 경고 로그 한 줄로 끝나는 그 상태보다 안 뜨는 것이 낫다. 로컬에서는 처음 한 번(또는 폴더를 지운 뒤) 플래그를 켜고 띄우면 되고,
 * 그 뒤에는 플래그 없이도 파일을 읽는다.
 */
@Slf4j
public record JwtKeys(RSAPublicKey publicKey, RSAPrivateKey privateKey) {

    static final String PRIVATE_KEY_FILE = "private.pem";
    static final String PUBLIC_KEY_FILE = "public.pem";
    private static final int RSA_KEY_BITS = 2048;

    public static JwtKeys load(JwtProperties properties)
    {
        boolean hasPrivate = hasText(properties.privateKey());
        boolean hasPublic = hasText(properties.publicKey());
        if(hasPrivate != hasPublic)
        {
            throw new IllegalStateException(
                    "JWT_PRIVATE_KEY 와 JWT_PUBLIC_KEY 는 둘 다 주거나 둘 다 비워야 한다 — 하나만 있다");
        }
        if(hasPrivate)
        {
            return new JwtKeys(parsePublicKey(properties.publicKey()), parsePrivateKey(properties.privateKey()));
        }
        return loadDevKeys(Path.of(properties.devKeyDir()), properties.generateDevKeys());
    }

    /**
     * 두 파일이 있으면 읽는다({@code generate} 와 상관없이). 없으면 {@code generate} 일 때만 만들고, 아니면 {@link IllegalStateException}
     * 으로 기동을 막는다. <b>개발용이다</b> — 운영에서 이 길로 오면 안 된다. 그래서 읽든 만들든 경고로 남긴다.
     */
    static JwtKeys loadDevKeys(Path dir, boolean generate)
    {
        Path privateFile = dir.resolve(PRIVATE_KEY_FILE);
        Path publicFile = dir.resolve(PUBLIC_KEY_FILE);
        boolean filesExist = Files.exists(privateFile) && Files.exists(publicFile);
        if(!filesExist && !generate)
        {
            throw new IllegalStateException("JWT_PRIVATE_KEY · JWT_PUBLIC_KEY 가 비어 있고 개발용 키 파일도 없다 — "
                    + "JWT_PRIVATE_KEY · JWT_PUBLIC_KEY 를 넣어라(운영 — Secrets Manager). "
                    + "로컬에서 개발용 키를 만들려면 JWT_GENERATE_DEV_KEYS=true 로 한 번 띄운다. dir=" + dir.toAbsolutePath());
        }
        try
        {
            if(filesExist)
            {
                log.warn("JWT_PRIVATE_KEY · JWT_PUBLIC_KEY 가 비어 있어 개발용 키를 읽는다 — 운영에서는 환경변수로 넣어라. dir={}",
                        dir.toAbsolutePath());
                return new JwtKeys(parsePublicKey(Files.readString(publicFile)),
                        parsePrivateKey(Files.readString(privateFile)));
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_BITS);
            KeyPair pair = generator.generateKeyPair();

            Files.createDirectories(dir);
            // 임시 파일에 다 쓴 뒤 이름을 바꾼다 — 쓰다 죽어도 반쪽짜리 PEM 이 남지 않는다
            writeAtomically(privateFile, toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            writeAtomically(publicFile, toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
            log.warn("JWT_PRIVATE_KEY · JWT_PUBLIC_KEY 가 비어 있고 JWT_GENERATE_DEV_KEYS=true 라 개발용 키를 새로 만들었다 — "
                    + "운영에서는 환경변수로 넣어라. 옆 서비스는 이 폴더의 {} 으로 검증한다. dir={}", PUBLIC_KEY_FILE, dir.toAbsolutePath());
            return new JwtKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("개발용 키를 읽거나 쓰지 못했다 dir=" + dir.toAbsolutePath(), e);
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalStateException("개발용 키를 만들지 못했다", e);
        }
    }

    static RSAPrivateKey parsePrivateKey(String pem)
    {
        try
        {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
        }
        catch(GeneralSecurityException | IllegalArgumentException e)
        {
            // 키의 내용을 예외 메시지에 싣지 않는다 — 로그로 나간다
            throw new IllegalStateException("개인 키를 읽지 못했다 — PKCS#8 PEM(-----BEGIN PRIVATE KEY-----)이어야 한다", e);
        }
    }

    static RSAPublicKey parsePublicKey(String pem)
    {
        try
        {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePem(pem)));
        }
        catch(GeneralSecurityException | IllegalArgumentException e)
        {
            throw new IllegalStateException("공개 키를 읽지 못했다 — X.509 PEM(-----BEGIN PUBLIC KEY-----)이어야 한다", e);
        }
    }

    /**
     * 머리 · 꼬리 줄과 공백을 걷어 내고 Base64 를 푼다. 환경변수에는 줄바꿈이 글자 그대로의 {@code \n} 두 글자로 들어오기도 한다 —
     * 그것도 걷어 낸다.
     */
    private static byte[] decodePem(String pem)
    {
        String base64 = pem.replace("\\n", "")
                .replaceAll("-----[A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    static String toPem(String label, byte[] der)
    {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static void writeAtomically(Path target, String content) throws IOException
    {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
