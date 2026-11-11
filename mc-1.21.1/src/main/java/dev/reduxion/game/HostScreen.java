package dev.reduxion.game;

import dev.reduxion.ui.UiScreen;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Thin vanilla Screen that forwards everything to a version-independent UiScreen. */
public final class HostScreen extends Screen {
    private final UiScreen ui;
    private boolean opened;

    public HostScreen(UiScreen ui) {
        super(Component.literal("REDUXion"));
        this.ui = ui;
    }

    @Override protected void init() {
        ui.width = this.width;
        ui.height = this.height;
        if (!opened) { opened = true; ui.opened(); }
    }

    @Override public void render(GuiGraphics gg, int mx, int my, float delta) {
        ui.width = this.width;
        ui.height = this.height;
        // dim the world only by half so the result of a replacement stays visible behind the menu
        gg.fill(0, 0, this.width, this.height, 0x66000000);
        GfxImpl g = GfxImpl.of(gg);
        ui.render(g, mx, my);
        g.unclipAll();
    }

    @Override public void renderBackground(GuiGraphics gg, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void tick() { ui.tick(); }
    @Override public void removed() { ui.closed(); }
    @Override public void onFilesDrop(List<Path> files) { ui.filesDropped(files); }

    @Override public boolean mouseClicked(double x, double y, int button) { ui.im.onPress(x, y, button); return true; }
    @Override public boolean mouseReleased(double x, double y, int button) { ui.im.onRelease(); return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { ui.im.onDrag(dx, dy); return true; }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { ui.im.onScroll(sy); return true; }
    @Override public boolean charTyped(char c, int mods) { ui.im.onChar(c); return true; }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { ui.escape(); return true; }
        ui.im.onKey(key, mods);
        return true;
    }
}
