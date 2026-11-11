package dev.reduxion.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** PNG helpers based on ImageIO (available in every desktop JDK). */
public final class Png {
    private Png() {}

    public static final class Image {
        public final int w, h;
        public final int[] argb;
        public Image(int w, int h, int[] argb) { this.w = w; this.h = h; this.argb = argb; }
        public int get(int x, int y) { return argb[y * w + x]; }
    }

    /** Reads width/height from the IHDR chunk without decoding. */
    public static int[] size(byte[] png) {
        if (png == null || png.length < 24) return null;
        if ((png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return null;
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null;
        return new int[]{w, h};
    }

    public static Image decode(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return checker(16, 16);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            return new Image(w, h, px);
        } catch (Exception e) {
            return checker(16, 16);
        }
    }

    public static byte[] encode(Image img) {
        try {
            BufferedImage b = new BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_ARGB);
            b.setRGB(0, 0, img.w, img.h, img.argb, 0, img.w);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(b, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Re-encodes a PNG, dropping all metadata chunks. */
    public static byte[] clean(byte[] png) { return encode(decode(png)); }

    public static Image checker(int w, int h) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                px[y * w + x] = ((x * 2 / w) + (y * 2 / h)) % 2 == 0 ? 0xFFF800F8 : 0xFF000000;
        return new Image(w, h, px);
    }

    private static byte[] missing;
    public static synchronized byte[] missing() {
        if (missing == null) missing = encode(checker(16, 16));
        return missing.clone();
    }
}
