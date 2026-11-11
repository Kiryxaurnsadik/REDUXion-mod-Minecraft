package dev.reduxion.ui;

import dev.reduxion.core.*;
import java.nio.file.Path;
import java.util.List;

/** Version-independent screen. The per-version host forwards input and calls {@link #render}. */
public abstract class UiScreen {
    public int width, height;
    public final Im im = new Im();

    public void opened() {}
    public void closed() {}
    public void tick() {}
    public void filesDropped(List<Path> files) {}
    /** Return true to let ESC close the screen. */
    public boolean escape() { Redux.platform.closeScreen(); return true; }

    protected abstract void draw(Gfx g);

    public final void render(Gfx g, int mx, int my) {
        im.begin(g, mx, my);
        try {
            draw(g);
        } catch (RuntimeException e) {
            g.unclipAll();
            g.text("REDUXion UI error: " + e, 4, 4, 0xFFFF5555, true);
        }
        im.end();
    }
}
