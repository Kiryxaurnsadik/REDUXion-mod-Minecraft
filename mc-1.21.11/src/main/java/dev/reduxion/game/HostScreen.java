package dev.reduxion.game;

import dev.reduxion.ui.UiScreen;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { ui.im.onPress(e.x(), e.y(), e.button()); return true; }
    @Override public boolean mouseReleased(MouseButtonEvent e) { ui.im.onRelease(); return true; }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { ui.im.onDrag(dx, dy); return true; }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { ui.im.onScroll(sy); return true; }
    @Override public boolean charTyped(CharacterEvent e) {
        for (char c : e.codepointAsString().toCharArray()) ui.im.onChar(c);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { ui.escape(); return true; }
        ui.im.onKey(e.key(), e.modifiers());
        return true;
    }
}
