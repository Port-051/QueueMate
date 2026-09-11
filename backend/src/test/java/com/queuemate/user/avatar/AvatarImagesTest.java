package com.queuemate.user.avatar;

import com.queuemate.common.error.UnsupportedMediaTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 업로드 정규화. 여기서 통과시킨 바이트만 디스크에 닿으므로 거절 조건을 함께 고정한다.
 */
class AvatarImagesTest {

    private static final int EDGE = 512;

    private final AvatarImages images = new AvatarImages(
            new AvatarProperties("/tmp/unused", DataSize.ofMegabytes(5), EDGE, 40_000_000L));

    private static byte[] image(String format, int width, int height) throws IOException {
        // JPEG는 알파 채널을 못 쓴다. 포맷에 맞는 타입으로 만든다.
        int type = "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage source = new BufferedImage(width, height, type);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, format, out), format + " 인코더가 없다");
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    @DisplayName("가로로 긴 PNG는 가운데를 기준으로 정사각이 된다")
    void cropsWideImageToSquare() throws IOException {
        BufferedImage result = decode(images.toSquarePng(image("png", 1200, 600)));

        assertEquals(result.getWidth(), result.getHeight());
        // 짧은 변이 600이므로 512로 줄어든다.
        assertEquals(EDGE, result.getWidth());
    }

    @Test
    @DisplayName("세로로 긴 JPEG도 같은 규칙을 따른다")
    void cropsTallJpegToSquare() throws IOException {
        BufferedImage result = decode(images.toSquarePng(image("jpeg", 400, 1000)));

        assertEquals(result.getWidth(), result.getHeight());
        // 짧은 변이 400이라 512로 늘리지 않는다. 늘려도 선명해지지 않는다.
        assertEquals(400, result.getWidth());
    }

    @Test
    @DisplayName("무엇을 넣든 나오는 것은 PNG다")
    void alwaysReencodesAsPng() throws IOException {
        byte[] result = images.toSquarePng(image("jpeg", 800, 800));

        // PNG 시그니처.
        assertEquals((byte) 0x89, result[0]);
        assertEquals('P', result[1]);
        assertEquals('N', result[2]);
        assertEquals('G', result[3]);
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 400이다")
    void rejectsNonImage() {
        byte[] text = "이건 이미지가 아니다".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> images.toSquarePng(text));
    }

    @Test
    @DisplayName("PNG 시그니처만 흉내 낸 파일은 통과하지 못한다")
    void trustsBytesNotTheName() {
        // 파일명과 Content-Type은 클라이언트가 말하는 것이라 검사에 쓰지 않는다.
        // 머리만 PNG고 뒤가 없는 바이트는 디코딩 단계에서 걸린다.
        byte[] lying = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                'n', 'o', 't', ' ', 'a', ' ', 'p', 'n', 'g',
        };

        assertThrows(IllegalArgumentException.class, () -> images.toSquarePng(lying));
    }

    @Test
    @DisplayName("읽히기는 하지만 받지 않기로 한 포맷은 415다")
    void rejectsDisallowedFormat() throws IOException {
        byte[] gif = image("gif", 300, 300);

        assertThrows(UnsupportedMediaTypeException.class, () -> images.toSquarePng(gif));
    }

    @Test
    @DisplayName("펼치면 너무 커지는 이미지는 디코딩 전에 막는다")
    void rejectsOversizedSource() throws IOException {
        AvatarImages tiny = new AvatarImages(
                new AvatarProperties("/tmp/unused", DataSize.ofMegabytes(5), EDGE, 1_000L));

        assertThrows(IllegalArgumentException.class, () -> tiny.toSquarePng(image("png", 100, 100)));
    }

    @Test
    @DisplayName("WebP 디코더가 등록되어 있다")
    void webpReaderIsOnTheClasspath() {
        // 계약이 WebP를 받겠다고 했고, 표준 ImageIO는 WebP를 모른다.
        // 의존성이 빠지면 업로드가 415로 조용히 거절되므로 여기서 잡는다.
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext(),
                "imageio-webp 의존성이 빠졌다");
    }
}
