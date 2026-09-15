package com.queuemate.user.avatar;

import com.queuemate.common.error.UnsupportedMediaTypeException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 업로드된 바이트를 정사각 PNG 한 장으로 바꾼다.
 *
 * <p>여기서 하는 일의 절반은 크기 맞추기고 절반은 방어다. 확장자와 Content-Type은
 * 클라이언트가 말하는 것이라 믿지 않고, 실제로 디코더에 넣어 본 결과만 믿는다.
 * 원본 바이트는 어디에도 보관하지 않는다. 다시 인코딩한 것만 나간다.
 */
@Component
public class AvatarImages {

    /** ImageIO가 읽어낸 실제 포맷 이름 기준이다. 확장자와 무관하다. */
    private static final Set<String> ALLOWED = Set.of("png", "jpeg", "jpg", "webp");

    private final AvatarProperties properties;

    public AvatarImages(AvatarProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws IllegalArgumentException 이미지로 열리지 않는다 (400)
     * @throws UnsupportedMediaTypeException 이미지지만 허용하지 않는 포맷이다 (415)
     */
    public byte[] toSquarePng(byte[] source) {
        BufferedImage decoded = decode(source);
        BufferedImage square = cropToSquare(decoded);
        BufferedImage scaled = scale(square, properties.edgePixels());
        return encodePng(scaled);
    }

    private BufferedImage decode(byte[] source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new IllegalArgumentException("이미지로 읽을 수 없는 파일이다");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                // 어떤 디코더도 이 바이트를 모른다. 이미지가 아니다.
                throw new IllegalArgumentException("이미지로 읽을 수 없는 파일이다");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED.contains(format)) {
                    // GIF나 BMP처럼 읽히기는 하지만 받지 않기로 한 것들이다.
                    throw new UnsupportedMediaTypeException("PNG, JPEG, WebP만 올릴 수 있다");
                }
                reader.setInput(input, true, true);
                // 픽셀을 펼치기 전에 헤더의 크기부터 본다. 파일이 작아도 펼치면 클 수 있다.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > properties.maxSourcePixels()) {
                    throw new IllegalArgumentException("이미지가 너무 큽니다");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("이미지를 읽지 못했다");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // 헤더는 그럴듯한데 뒤가 잘렸거나 깨진 파일이다. 사용자가 고칠 수 있으니 400이다.
            throw new IllegalArgumentException("이미지를 읽지 못했다");
        }
    }

    /** 가운데를 기준으로 짧은 변에 맞춰 자른다. 원형 프레임에 들어가므로 가장자리는 어차피 가려진다. */
    private BufferedImage cropToSquare(BufferedImage image) {
        int edge = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - edge) / 2;
        int y = (image.getHeight() - edge) / 2;
        return image.getSubimage(x, y, edge, edge);
    }

    private BufferedImage scale(BufferedImage image, int edge) {
        // 원본이 이미 작으면 늘리지 않는다. 늘려 봐야 흐려지기만 하고 용량만 는다.
        int target = Math.min(edge, image.getWidth());
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, 0, 0, target, target, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("PNG 인코더가 없다");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("PNG로 변환하지 못했다", e);
        }
        return out.toByteArray();
    }
}
