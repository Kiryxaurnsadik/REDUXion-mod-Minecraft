package dev.reduxion.ui;

import dev.reduxion.core.*;

/** One-line translucent status panel in a screen corner. */
public final class Hud {
    private Hud() {}

    /** @param sw,sh screen size already divided by the HUD scale */
    public static void render(Gfx g, int sw, int sh) {
        Config c = Redux.config;
        if (c == null || !c.hud) return;
        long now = System.currentTimeMillis();
        long left = Redux.hudMessageUntil - now;
        if (left <= 0 && !Redux.reloading) return;
        float fade = Redux.reloading ? 1f : Math.min(1f, left / 500f);
        String text = Redux.hudMessage;
        if (text == null || text.isEmpty()) return;
        int w = g.textWidth(text) + 12, h = 16 + (Redux.reloading ? 3 : 0);
        int x = (c.hudCorner & 1) == 0 ? 4 : sw - w - 4;
        int y = c.hudCorner < 2 ? 4 : sh - h - 4;
        int a = (int) (255 * c.hudOpacity * fade);
        if (a < 8) return;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, (a << 24) | 0x101014);
        int border = ((int) (255 * fade) << 24);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, ((int) (70 * fade) << 24) | 0xFFFFFF);
        int ta = Math.max(8, (int) (255 * fade));
        boolean off = !c.enabled;
        g.text(text, x + 6, y + 4, (ta << 24) | (off ? 0xFF8080 : 0x9FE89F), true);
        if (Redux.reloading) {
            // indeterminate progress bar
            int bw = w - 4, seg = bw / 4;
            int pos = (int) ((now / 12) % (bw + seg)) - seg;
            g.fill(x + 2, y + h - 4, x + 2 + bw, y + h - 2, 0xFF303030);
            g.fill(x + 2 + Math.max(0, pos), y + h - 4, x + 2 + Math.min(bw, pos + seg), y + h - 2, 0xFF6FD26F);
        }
    }
}
