package dev.reduxion.ui;

import dev.reduxion.core.*;
import java.util.*;

/** Small immediate-mode toolkit drawn in the vanilla style: dark translucent panels, pixel borders, vanilla font. */
public final class Im {
    public static final int TEXT = 0xFFE0E0E0, DIM = 0xFFA0A0A0, ACCENT = 0xFF6FD26F, WARN = 0xFFFFC857, BAD = 0xFFFF6B6B;
    public static final int KEY_ENTER = 257, KEY_ESC = 256, KEY_BACK = 259, KEY_DEL = 261, KEY_LEFT = 263, KEY_RIGHT = 262,
        KEY_HOME = 268, KEY_END = 269, KEY_TAB = 258, KEY_KP_ENTER = 335;

    private Gfx g;
    public int mx, my;
    // one-shot events, valid for a single frame
    private boolean pressed, released;
    private int pressButton;
    public double scroll;
    private final List<int[]> keys = new ArrayList<>();
    private final StringBuilder typed = new StringBuilder();
    public boolean down;
    public double dragDx, dragDy;
    private String focus, active;
    private int caret;
    private long blink;
    private String tooltip;
    public boolean enterPressed;

    // ---- events from the host
    public void onPress(double x, double y, int button) { mx = (int) x; my = (int) y; pressed = true; pressButton = button; down = true; }
    public void onRelease() { released = true; down = false; }
    public void onDrag(double dx, double dy) { dragDx += dx; dragDy += dy; }
    public void onScroll(double amount) { scroll += amount; }
    public void onKey(int key, int mods) { keys.add(new int[]{key, mods}); if (key == KEY_ENTER || key == KEY_KP_ENTER) enterPressed = true; }
    public void onChar(char c) { if (c >= 32 && c != 127) typed.append(c); }
    public boolean hasFocus() { return focus != null; }
    public void focus(String id) { focus = id; caret = Integer.MAX_VALUE; }

    void begin(Gfx g, int mx, int my) { this.g = g; this.mx = mx; this.my = my; tooltip = null; }

    void end() {
        if (tooltip != null) {
            int w = g.textWidth(tooltip) + 8;
            int x = Math.max(2, Math.min(mx + 10, 10000)), y = my - 14;
            g.fill(x, y, x + w, y + 13, 0xF0100010);
            frame(x, y, w, 13, 0xFF5000A0);
            g.text(tooltip, x + 4, y + 3, TEXT, false);
        }
        if (pressed && focusClickMiss) focus = null;
        focusClickMiss = true;
        pressed = false;
        if (released) active = null;
        released = false;
        scroll = 0;
        dragDx = dragDy = 0;
        keys.clear();
        typed.setLength(0);
        enterPressed = false;
    }
    private boolean focusClickMiss = true;

    public boolean over(int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    public boolean clicked(int x, int y, int w, int h) { return pressed && pressButton == 0 && over(x, y, w, h); }
    public boolean pressedAnywhere() { return pressed; }
    public boolean isActive(String id) { return id.equals(active); }
    public void setActive(String id) { active = id; }

    // ---- drawing helpers
    public void frame(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Translucent panel with a vanilla-like bevelled pixel border. */
    public void panel(int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x99101014);
        frame(x, y, w, h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x50FFFFFF);
        g.fill(x + 1, y + 2, x + 2, y + h - 1, 0x50FFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x60000000);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 2, 0x60000000);
    }

    public void label(String s, int x, int y, int color) { g.text(s, x, y, color, true); }

    public void labelCentered(String s, int cx, int y, int color) { g.text(s, cx - g.textWidth(s) / 2, y, color, true); }

    public String fit(String s, int w) {
        if (s == null) return "";
        if (g.textWidth(s) <= w) return s;
        String d = "…";
        int n = s.length();
        while (n > 0 && g.textWidth(s.substring(0, n) + d) > w) n--;
        return s.substring(0, n) + d;
    }

    /** Word-wraps and draws; returns the y after the last line. */
    public int wrap(String s, int x, int y, int w, int color) {
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (g.textWidth(t) > w && line.length() > 0) {
                    g.text(line.toString(), x, y, color, true);
                    y += 10;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(t);
                }
            }
            g.text(line.toString(), x, y, color, true);
            y += 10;
        }
        return y;
    }

    public boolean button(String label, int x, int y, int w, int h) { return button(label, x, y, w, h, true, 0); }

    /** @param tint 0 normal, 1 green (primary), 2 red (danger) */
    public boolean button(String label, int x, int y, int w, int h, boolean enabled, int tint) {
        boolean hov = enabled && over(x, y, w, h);
        int base = tint == 1 ? 0xCC2E6B34 : tint == 2 ? 0xCC7A2A2A : 0xCC55555A;
        if (!enabled) base = 0x99303033;
        else if (hov) base = tint == 1 ? 0xE63F8F47 : tint == 2 ? 0xE6A03838 : 0xE67A7A86;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
        frame(x, y, w, h, hov ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x50000000);
        String t = fit(label, w - 4);
        g.text(t, x + (w - g.textWidth(t)) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF808080, true);
        if (hov && clicked(x, y, w, h)) {
            Redux.platform.click();
            return true;
        }
        return false;
    }

    /** Checkbox with a label. Returns the new value. */
    public boolean check(String label, int x, int y, boolean value) {
        int w = 12 + 4 + g.textWidth(label);
        boolean hov = over(x, y, w, 12);
        g.fill(x + 1, y + 1, x + 11, y + 11, 0xCC202024);
        frame(x, y, 12, 12, hov ? 0xFFFFFFFF : 0xFFA0A0A0);
        if (value) g.fill(x + 3, y + 3, x + 9, y + 9, ACCENT);
        g.text(label, x + 16, y + 2, TEXT, true);
        if (hov && clicked(x, y, w, 12)) {
            Redux.platform.click();
            return !value;
        }
        return value;
    }

    /** On/off switch. Returns the new value. */
    public boolean toggle(int x, int y, boolean value) {
        boolean hov = over(x, y, 24, 12);
        g.fill(x + 1, y + 1, x + 23, y + 11, value ? 0xCC2E6B34 : 0xCC3A3A3E);
        frame(x, y, 24, 12, hov ? 0xFFFFFFFF : 0xFF000000);
        int kx = value ? x + 13 : x + 2;
        g.fill(kx, y + 2, kx + 9, y + 10, 0xFFD8D8D8);
        if (hov && clicked(x, y, 24, 12)) {
            Redux.platform.click();
            return !value;
        }
        return value;
    }

    /** Horizontal slider. Returns the new value. */
    public float slider(String id, int x, int y, int w, float value, float min, float max) {
        boolean hov = over(x, y, w, 10);
        if (hov && clicked(x, y, w, 10)) active = id;
        if (id.equals(active) && down) {
            float t = Math.max(0, Math.min(1, (mx - x - 3) / (float) (w - 6)));
            value = min + t * (max - min);
        }
        g.fill(x, y + 4, x + w, y + 6, 0xFF000000);
        g.fill(x, y + 4, x + w, y + 5, 0xFF505058);
        float t = (Math.max(min, Math.min(max, value)) - min) / (max - min);
        int kx = x + (int) (t * (w - 6));
        g.fill(kx, y, kx + 6, y + 10, hov || id.equals(active) ? 0xFFFFFFFF : 0xFFC0C0C0);
        frame(kx, y, 6, 10, 0xFF000000);
        return value;
    }

    /** Single-line text field. Returns the (possibly edited) text. */
    public String field(String id, int x, int y, int w, String value, String placeholder) { return field(id, x, y, w, value, placeholder, false); }

    public String field(String id, int x, int y, int w, String value, String placeholder, boolean secret) {
        if (value == null) value = "";
        int h = 14;
        boolean hov = over(x, y, w, h);
        if (pressed && hov) { focus = id; caret = value.length(); focusClickMiss = false; }
        boolean foc = id.equals(focus);
        if (foc) {
            caret = Math.max(0, Math.min(caret, value.length()));
            StringBuilder sb = new StringBuilder(value);
            for (int[] k : keys) {
                switch (k[0]) {
                    case KEY_BACK -> { if (caret > 0) { sb.deleteCharAt(caret - 1); caret--; } }
                    case KEY_DEL -> { if (caret < sb.length()) sb.deleteCharAt(caret); }
                    case KEY_LEFT -> caret = Math.max(0, caret - 1);
                    case KEY_RIGHT -> caret = Math.min(sb.length(), caret + 1);
                    case KEY_HOME -> caret = 0;
                    case KEY_END -> caret = sb.length();
                    case 86 -> {
                        if ((k[1] & 2) != 0) {
                            String clip = Redux.platform.clipboard();
                            if (clip != null) {
                                clip = clip.replace("\r", "").replace("\n", " ");
                                sb.insert(caret, clip);
                                caret += clip.length();
                            }
                        }
                    }
                    case 65 -> { if ((k[1] & 2) != 0) caret = sb.length(); }
                    default -> { }
                }
            }
            if (typed.length() > 0) {
                sb.insert(caret, typed);
                caret += typed.length();
            }
            if (sb.length() > 512) sb.setLength(512);
            caret = Math.min(caret, sb.length());
            value = sb.toString();
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xE0000000);
        frame(x, y, w, h, foc ? 0xFFFFFFFF : hov ? 0xFFC0C0C0 : 0xFF808080);
        String shown = secret ? "*".repeat(value.length()) : value;
        g.clip(x + 2, y + 1, x + w - 2, y + h - 1);
        int cx = g.textWidth(shown.substring(0, Math.min(foc ? caret : 0, shown.length())));
        int off = Math.max(0, cx - (w - 10));
        if (shown.isEmpty() && !foc && placeholder != null) g.text(placeholder, x + 4, y + 3, 0xFF707070, false);
        else g.text(shown, x + 4 - off, y + 3, TEXT, false);
        if (foc && (System.currentTimeMillis() - blink) / 400 % 2 == 0) g.fill(x + 4 + cx - off, y + 2, x + 5 + cx - off, y + h - 2, 0xFFFFFFFF);
        g.unclip();
        return value;
    }

    public void tip(int x, int y, int w, int h, String text) { if (over(x, y, w, h)) tooltip = text; }

    /** Small rounded badge with text; returns its width. */
    public int badge(String s, int x, int y, int color) {
        int w = g.textWidth(s) + 6;
        g.fill(x, y, x + w, y + 11, color);
        g.text(s, x + 3, y + 2, 0xFFFFFFFF, false);
        return w;
    }

    public void scrollbar(int x, int y, int h, int content, int offset) {
        if (content <= h) return;
        g.fill(x, y, x + 3, y + h, 0x60000000);
        int bh = Math.max(12, h * h / content);
        int by = y + (int) ((h - bh) * (offset / (float) (content - h)));
        g.fill(x, by, x + 3, by + bh, 0xFFB0B0B0);
    }
}
