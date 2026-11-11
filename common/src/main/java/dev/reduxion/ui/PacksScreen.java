package dev.reduxion.ui;

import dev.reduxion.core.*;
import dev.reduxion.pack.ReduxPack;
import java.nio.file.Path;
import java.util.*;

/** List of installed REDUX packs. */
public final class PacksScreen extends UiScreen {
    private int scroll;
    private String message = "";
    private int messageColor = Im.TEXT;
    private long messageUntil;
    private ReduxPack dragging;

    @Override public void opened() {
        for (ReduxPack p : Redux.packs) Screens.uploadIcon(p);
    }

    private void say(String s, int color) { message = s; messageColor = color; messageUntil = System.currentTimeMillis() + 6000; }

    @Override public void filesDropped(List<Path> files) {
        for (Path f : files) importFile(f);
    }

    private void importFile(Path f) {
        try {
            ReduxPack p = Redux.importFile(f);
            Screens.uploadIcon(p);
            if (p.locked && p.manifest.hasPassword) {
                Screens.open(new Screens.Password(p, this));
            } else {
                say(L.t("Импортирован: ", "Imported: ") + p.manifest.name, Im.ACCENT);
                Screens.applyThen(this);
            }
        } catch (Exception e) {
            say(L.t("Ошибка импорта: ", "Import failed: ") + e.getMessage(), Im.BAD);
        }
    }

    @Override protected void draw(Gfx g) {
        int w = Math.min(460, width - 16), x = (width - w) / 2;
        int top = 8, bottom = height - 8;
        im.panel(x, top, w, bottom - top);
        // header
        im.label("REDUXion", x + 10, top + 9, Im.ACCENT);
        im.label(L.t("паки", "packs"), x + 10 + g.textWidth("REDUXion "), top + 9, Im.DIM);
        boolean on = Redux.config.enabled;
        if (im.button(on ? L.t("Отключить все", "Disable all") : L.t("Включить все", "Enable all"), x + w - 208, top + 5, 110, 18, true, on ? 2 : 1)) Redux.toggleAll();
        im.tip(x + w - 208, top + 5, 110, 18, L.t("Клавиша: ", "Key: ") + Redux.platform.keyName());
        if (im.button(L.t("Настройки", "Settings"), x + w - 92, top + 5, 82, 18)) { Screens.open(new Screens.Settings(this)); return; }

        // list
        int lx = x + 8, ly = top + 30, lw = w - 16, lh = bottom - top - 30 - 34;
        int card = 46, content = Redux.packs.size() * (card + 4);
        if (im.over(lx, ly, lw, lh)) scroll -= (int) (im.scroll * 20);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - lh)));
        g.clip(lx, ly, lx + lw, ly + lh);
        boolean inList = im.over(lx, ly, lw, lh);
        ReduxPack action = null;
        int act = 0;
        int cy = ly - scroll;
        if (Redux.packs.isEmpty()) {
            im.labelCentered(L.t("Паков пока нет", "No packs yet"), x + w / 2, ly + lh / 2 - 14, Im.TEXT);
            im.labelCentered(L.t("Создайте пак или перетащите файл .redux / .reduxp в окно игры", "Create a pack or drop a .redux / .reduxp file onto the game window"), x + w / 2, ly + lh / 2, Im.DIM);
        }
        for (int i = 0; i < Redux.packs.size(); i++) {
            ReduxPack p = Redux.packs.get(i);
            if (cy + card >= ly && cy <= ly + lh) {
                boolean enabled = Redux.isEnabled(p) && Redux.usable(p);
                g.fill(lx, cy, lx + lw - 6, cy + card, enabled && on ? 0x8C1E2A1E : 0x8C1A1A1E);
                im.frame(lx, cy, lw - 6, card, p == dragging ? 0xFFFFFFFF : 0xFF000000);
                // icon
                g.fill(lx + 5, cy + 5, lx + 41, cy + 41, 0xFF000000);
                if (p.icon != null) g.image("icon_" + p.manifest.id, lx + 7, cy + 7, 32, 32);
                else im.labelCentered("R", lx + 23, cy + 19, Im.DIM);
                int tx = lx + 48, tw = lw - 6 - 48 - 150;
                g.text(im.fit(p.manifest.name, tw), tx, cy + 5, 0xFFFFFFFF, true);
                String by = p.manifest.author.isEmpty() ? "" : L.t("автор: ", "by ") + p.manifest.author;
                g.text(im.fit(by, tw), tx, cy + 16, Im.DIM, false);
                String info;
                int infoColor = Im.DIM;
                if (p.error != null) { info = L.t("Ошибка: ", "Error: ") + p.error; infoColor = Im.BAD; }
                else if (p.locked) { info = L.t("Нужен пароль", "Password required"); infoColor = Im.WARN; }
                else if (!p.warnings.isEmpty()) { info = p.warnings.get(0); infoColor = Im.WARN; }
                else info = p.manifest.description;
                g.text(im.fit(info, tw), tx, cy + 27, infoColor, false);
                if (inList && !p.warnings.isEmpty()) im.tip(tx, cy + 27, tw, 10, p.warnings.get(0));
                // badges
                int bx = lx + lw - 6 - 146;
                bx += im.badge(p.isProtected() ? L.t("защищённый", "protected") : L.t("открытый", "open"), bx, cy + 4, p.isProtected() ? 0xCC7A4A1A : 0xCC1A4A7A) + 3;
                if (!p.locked && p.error == null) im.badge(L.t("замен: ", "rules: ") + p.ruleCount(), bx, cy + 4, 0xCC3A3A40);
                if (inList) {
                    // toggle + order
                    int rx = lx + lw - 6 - 30;
                    if (Redux.usable(p)) {
                        boolean v = im.toggle(rx, cy + 4, Redux.isEnabled(p));
                        if (v != Redux.isEnabled(p)) { action = p; act = v ? 1 : 2; }
                    }
                    int by2 = cy + 26;
                    int b = lx + lw - 6 - 146;
                    if (p.error == null && p.locked) {
                        if (im.button(L.t("Пароль", "Password"), b, by2, 52, 15, true, 1)) { action = p; act = 7; }
                    } else if (p.error == null) {
                        if (im.button(L.t("Студия", "Studio"), b, by2, 44, 15)) { action = p; act = 3; }
                    }
                    if (p.error == null && !p.locked) { if (im.button(L.t("Экспорт", "Export"), b + (p.locked ? 54 : 46), by2, 46, 15)) { action = p; act = 4; } }
                    if (im.button("✕", b + 94, by2, 15, 15, true, 2)) { action = p; act = 5; }
                    im.tip(b + 94, by2, 15, 15, L.t("Удалить пак", "Delete pack"));
                    if (im.button("▲", b + 112, by2, 15, 15, i > 0, 0)) { action = p; act = 8; }
                    if (im.button("▼", b + 128, by2, 15, 15, i < Redux.packs.size() - 1, 0)) { action = p; act = 9; }
                    im.tip(b + 112, by2, 31, 15, L.t("Пак выше перекрывает пак ниже", "A higher pack overrides a lower one"));
                }
            }
            cy += card + 4;
        }
        g.unclip();
        im.scrollbar(lx + lw - 4, ly, lh, content, scroll);

        // footer
        int fy = bottom - 28;
        if (im.button(L.t("Создать", "Create"), x + 8, fy, 80, 18, true, 1)) { Screens.open(new Screens.Details(null, this)); return; }
        if (im.button(L.t("Импорт…", "Import…"), x + 92, fy, 80, 18)) {
            Path f = FilePick.open(L.t("Импорт REDUX-пака", "Import a REDUX pack"), "REDUX (*.redux, *.reduxp)", "*.redux", "*.reduxp");
            if (f != null) { importFile(f); return; }
            else say(L.t("Файл не выбран. Можно перетащить .redux / .reduxp в окно игры.", "No file chosen. You can drop a .redux / .reduxp file onto the game window."), Im.DIM);
        }
        if (im.button(L.t("Закрыть", "Close"), x + w - 78, fy, 70, 18)) { Redux.platform.closeScreen(); return; }
        if (System.currentTimeMillis() < messageUntil) g.text(im.fit(message, w - 270), x + 178, fy + 5, messageColor, true);
        else g.text(im.fit(Redux.statusLine(), w - 270), x + 178, fy + 5, Im.DIM, true);

        if (action != null) {
            final ReduxPack p = action;
            switch (act) {
                case 1 -> { Redux.config.disabled.remove(p.manifest.id); Screens.applyThen(this); }
                case 2 -> Redux.setEnabled(p, false);
                case 3 -> Screens.open(new Studio(p));
                case 4 -> Screens.open(new Screens.Export(p, this));
                case 5 -> Screens.open(new Screens.Confirm(L.t("Удалить пак «", "Delete pack \"") + p.manifest.name + L.t("» с этого компьютера?", "\" from this computer?"),
                    L.t("Удалить", "Delete"), () -> { Redux.delete(p); Screens.open(this); }, () -> Screens.open(this)));
                case 7 -> Screens.open(new Screens.Password(p, this));
                case 8 -> Redux.move(p, -1);
                case 9 -> Redux.move(p, 1);
                default -> { }
            }
        }
    }
}
