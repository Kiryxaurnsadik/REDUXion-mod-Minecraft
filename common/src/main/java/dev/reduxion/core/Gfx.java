package dev.reduxion.core;

/** Minimal drawing surface implemented on top of the game's GUI renderer. */
public interface Gfx {
    void fill(int x1, int y1, int x2, int y2, int argb);
    void text(String s, int x, int y, int argb, boolean shadow);
    int textWidth(String s);
    void image(String key, int x, int y, int w, int h);
    void item(String itemId, int x, int y);
    void clip(int x1, int y1, int x2, int y2);
    void unclip();
    /** Pops every active clip (used for error recovery). */
    void unclipAll();
}
