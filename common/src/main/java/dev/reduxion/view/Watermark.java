package dev.reduxion.view;

/** Diagonal "PREVIEW REDUX" watermark burned into preview pixels. */
public final class Watermark {
    private Watermark() {}

    // 5x7 glyphs, one int per row (5 low bits)
    private static final String TEXT = "PREVIEW REDUX";
    private static int[] glyph(char c) {
        return switch (c) {
            case 'P' -> new int[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000};
            case 'R' -> new int[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001};
            case 'E' -> new int[]{0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111};
            case 'V' -> new int[]{0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100};
            case 'I' -> new int[]{0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110};
            case 'W' -> new int[]{0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001};
            case 'D' -> new int[]{0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110};
            case 'U' -> new int[]{0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110};
            case 'X' -> new int[]{0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001};
            default -> new int[7];
        };
    }

    private static boolean[][] pattern;
    private static synchronized boolean[][] pattern() {
        if (pattern == null) {
            int cw = 6, ph = 22, pw = TEXT.length() * cw + 18;
            boolean[][] p = new boolean[ph][pw];
            for (int i = 0; i < TEXT.length(); i++) {
                int[] g = glyph(TEXT.charAt(i));
                for (int y = 0; y < 7; y++)
                    for (int x = 0; x < 5; x++)
                        if ((g[y] >> (4 - x) & 1) != 0) p[y + 7][i * cw + x + 6] = true;
            }
            pattern = p;
        }
        return pattern;
    }

    public static void stamp(int[] px, int w, int h) {
        boolean[][] p = pattern();
        int ph = p.length, pw = p[0].length;
        float s = Math.max(1f, Math.min(w, h) / 150f);
        double ang = Math.toRadians(-28);
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float rx = (x * cos - y * sin) / s, ry = (x * sin + y * cos) / s;
                int row = Math.floorDiv((int) Math.floor(ry), ph);
                int tx = Math.floorMod((int) Math.floor(rx) + row * 31, pw);
                int ty = Math.floorMod((int) Math.floor(ry), ph);
                if (p[ty][tx]) {
                    int i = y * w + x;
                    px[i] = SoftRenderer.blend(px[i], 0xFFFFFF, 105) | 0xFF000000;
                } else {
                    // thin dark outline for readability on bright textures
                    int ty2 = Math.floorMod(ty + 1, ph), tx2 = Math.floorMod(tx - 1, pw);
                    if (p[ty2][tx] || p[ty][tx2]) {
                        int i = y * w + x;
                        px[i] = SoftRenderer.blend(px[i], 0x000000, 70);
                    }
                }
            }
        }
    }
}
