package dev.reduxion.ui;

import dev.reduxion.core.*;
import dev.reduxion.model.Png;
import dev.reduxion.pack.*;
import dev.reduxion.protect.Protect;
import java.nio.file.*;
import java.util.*;

/** Entry points and the smaller screens of the mod menu. */
public final class Screens {
    private Screens() {}

    /**
     * Text of the notice shown when the menu opens. Kept in one place so it is easy to change.
     */
    public static String noticeText() {
        return L.t(
            "REDUXion меняет только то, что видите вы. Мод ничего не отправляет на сервер, но некоторые серверы запрещают моды, меняющие отображение. Ответственность за использование лежит на игроке.",
            "REDUXion only changes what you see. The mod sends nothing to the server, but some servers forbid mods that change rendering. You use it at your own responsibility.");
    }

    /** Opens the mod menu (with the notice first, unless the player turned it off). */
    public static void openMenu() {
        L.ru = Redux.platform.russian();
        if (Redux.config.showNotice) Redux.platform.openScreen(new Notice());
        else Redux.platform.openScreen(new PacksScreen());
    }

    static void open(UiScreen s) { Redux.platform.openScreen(s); }

    /** Applies changes, asking first when a resource reload is needed and the player wants a confirmation. */
    static void applyThen(UiScreen back) {
        Redux.rebuild();
        if (Redux.needsReload() && Redux.config.confirmReload) {
            open(new Confirm(L.t("Появились новые модели или текстуры. Нужна короткая перезагрузка ресурсов (как F3+T). Выполнить сейчас?",
                "New models or textures were added. A short resource reload (like F3+T) is needed. Do it now?"),
                L.t("Перезагрузить", "Reload"), () -> { Redux.apply(); open(back); }, () -> open(back)));
        } else {
            Redux.apply();
        }
    }

    static void uploadIcon(ReduxPack p) {
        String key = "icon_" + p.manifest.id;
        try {
            Png.Image img = p.icon != null ? Png.decode(p.icon) : null;
            if (img == null) { Redux.platform.dropImage(key); return; }
            int s = 32;
            int[] px = new int[s * s];
            for (int y = 0; y < s; y++)
                for (int x = 0; x < s; x++) px[y * s + x] = img.get(x * img.w / s, y * img.h / s);
            Redux.platform.image(key, s, s, px);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ notice
    static final class Notice extends UiScreen {
        private boolean dontShow;

        @Override protected void draw(Gfx g) {
            int w = Math.min(340, width - 20), h = 138;
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.labelCentered("REDUXion", width / 2, y + 8, Im.ACCENT);
            int ty = im.wrap(noticeText(), x + 12, y + 26, w - 24, Im.TEXT);
            dontShow = im.check(L.t("Больше не показывать", "Don't show again"), x + 12, Math.max(ty + 6, y + h - 46), dontShow);
            if (im.button(L.t("Понятно", "Got it"), x + w / 2 - 50, y + h - 26, 100, 18, true, 1) || im.enterPressed) {
                if (dontShow) {
                    Redux.config.showNotice = false;
                    Redux.config.save();
                }
                open(new PacksScreen());
            }
        }
    }

    // ------------------------------------------------------------------ confirm
    static final class Confirm extends UiScreen {
        private final String text, yes;
        private final Runnable onYes, onNo;

        Confirm(String text, String yes, Runnable onYes, Runnable onNo) { this.text = text; this.yes = yes; this.onYes = onYes; this.onNo = onNo; }

        @Override public boolean escape() { onNo.run(); return true; }

        @Override protected void draw(Gfx g) {
            int w = Math.min(320, width - 20), h = 110;
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.wrap(text, x + 12, y + 12, w - 24, Im.TEXT);
            if (im.button(yes, x + w / 2 - 104, y + h - 26, 100, 18, true, 1)) onYes.run();
            else if (im.button(L.t("Отмена", "Cancel"), x + w / 2 + 4, y + h - 26, 100, 18)) onNo.run();
        }
    }

    // ------------------------------------------------------------------ password prompt
    static final class Password extends UiScreen {
        private final ReduxPack pack;
        private final UiScreen back;
        private String pw = "", error = "";
        private boolean remember = true;

        Password(ReduxPack pack, UiScreen back) { this.pack = pack; this.back = back; }

        @Override public void opened() { im.focus("pw"); }
        @Override public boolean escape() { open(back); return true; }

        @Override protected void draw(Gfx g) {
            int w = Math.min(300, width - 20), h = 128;
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.label(L.t("Пароль для пака", "Password for pack"), x + 12, y + 10, Im.ACCENT);
            im.label(im.fit(pack.manifest.name, w - 24), x + 12, y + 22, Im.TEXT);
            pw = im.field("pw", x + 12, y + 38, w - 24, pw, L.t("пароль", "password"), true);
            remember = im.check(L.t("Запомнить на этом компьютере", "Remember on this computer"), x + 12, y + 58, remember);
            if (!error.isEmpty()) im.label(error, x + 12, y + 76, Im.BAD);
            boolean go = im.button(L.t("Открыть", "Unlock"), x + w / 2 - 104, y + h - 26, 100, 18, !pw.isEmpty(), 1) || (im.enterPressed && !pw.isEmpty());
            if (im.button(L.t("Отмена", "Cancel"), x + w / 2 + 4, y + h - 26, 100, 18)) { open(back); return; }
            if (go) {
                try {
                    if (Redux.unlock(pack, pw, remember)) {
                        open(back);
                        applyThen(back);
                    } else {
                        error = L.t("Неверный пароль", "Wrong password");
                        pw = "";
                    }
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
            }
        }
    }

    // ------------------------------------------------------------------ pack details (create / edit)
    static final class Details extends UiScreen {
        private final ReduxPack pack;
        private final UiScreen back;
        private String name, author, desc, version, error = "";
        private byte[] icon;

        Details(ReduxPack pack, UiScreen back) {
            this.pack = pack;
            this.back = back;
            name = pack == null ? "" : pack.manifest.name;
            author = pack == null ? "" : pack.manifest.author;
            desc = pack == null ? "" : pack.manifest.description;
            version = pack == null ? "1.0" : pack.manifest.version;
            icon = pack == null ? null : pack.icon;
        }

        @Override public void opened() { im.focus("name"); }
        @Override public boolean escape() { open(back); return true; }

        @Override public void filesDropped(List<Path> files) {
            for (Path f : files) if (f.toString().toLowerCase(Locale.ROOT).endsWith(".png")) setIcon(f);
        }

        private void setIcon(Path f) {
            try {
                byte[] b = Files.readAllBytes(f);
                if (Png.size(b) == null) throw new Exception("not a PNG");
                Png.Image img = Png.decode(b);
                int s = 64;
                int[] px = new int[s * s];
                for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) px[y * s + x] = img.get(x * img.w / s, y * img.h / s);
                icon = Png.encode(new Png.Image(s, s, px));
                Redux.platform.image("icon_edit", s, s, px);
            } catch (Exception e) {
                error = L.t("Не удалось прочитать PNG", "Could not read the PNG");
            }
        }

        @Override protected void draw(Gfx g) {
            int w = Math.min(320, width - 20), h = 190;
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.label(pack == null ? L.t("Новый REDUX-пак", "New REDUX pack") : L.t("Сведения о паке", "Pack details"), x + 12, y + 10, Im.ACCENT);
            int fx = x + 90, fw = w - 102;
            im.label(L.t("Название", "Name"), x + 12, y + 31, Im.DIM);
            name = im.field("name", fx, y + 28, fw, name, "");
            im.label(L.t("Автор", "Author"), x + 12, y + 49, Im.DIM);
            author = im.field("author", fx, y + 46, fw, author, "");
            im.label(L.t("Описание", "Description"), x + 12, y + 67, Im.DIM);
            desc = im.field("desc", fx, y + 64, fw, desc, "");
            im.label(L.t("Версия", "Version"), x + 12, y + 85, Im.DIM);
            version = im.field("ver", fx, y + 82, 60, version, "1.0");
            im.label(L.t("Иконка", "Icon"), x + 12, y + 108, Im.DIM);
            g.fill(fx, y + 102, fx + 34, y + 136, 0xFF000000);
            if (icon != null) g.image(pack != null && icon == pack.icon ? "icon_" + pack.manifest.id : "icon_edit", fx + 1, y + 103, 32, 32);
            if (im.button(L.t("Выбрать PNG…", "Choose PNG…"), fx + 40, y + 102, 96, 16)) {
                Path p = FilePick.open(L.t("Иконка пака", "Pack icon"), "PNG", "*.png");
                if (p != null) setIcon(p);
            }
            im.label(L.t("или перетащите файл в окно", "or drop a file onto the window"), fx + 40, y + 124, Im.DIM);
            if (!error.isEmpty()) im.label(im.fit(error, w - 24), x + 12, y + 144, Im.BAD);
            boolean ok = !name.trim().isEmpty();
            if (im.button(pack == null ? L.t("Создать", "Create") : L.t("Сохранить", "Save"), x + w / 2 - 104, y + h - 26, 100, 18, ok, 1)) {
                try {
                    ReduxPack p = pack != null ? pack : Redux.create(name.trim(), author.trim());
                    p.manifest.name = name.trim();
                    p.manifest.author = author.trim();
                    p.manifest.description = desc.trim();
                    p.manifest.version = version.trim().isEmpty() ? "1.0" : version.trim();
                    p.icon = icon;
                    Redux.save(p);
                    uploadIcon(p);
                    Redux.config.save();
                    open(pack == null ? new Studio(p) : back);
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
            }
            if (im.button(L.t("Отмена", "Cancel"), x + w / 2 + 4, y + h - 26, 100, 18)) open(back);
        }
    }

    // ------------------------------------------------------------------ export
    static final class Export extends UiScreen {
        private final ReduxPack pack;
        private final UiScreen back;
        private boolean protect, usePassword;
        private String pw = "", pw2 = "", result = "", error = "";

        Export(ReduxPack pack, UiScreen back) { this.pack = pack; this.back = back; protect = pack.isProtected(); }

        @Override public boolean escape() { open(back); return true; }

        @Override protected void draw(Gfx g) {
            int w = Math.min(360, width - 20), h = 214;
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.label(L.t("Экспорт пака: ", "Export pack: ") + im.fit(pack.manifest.name, w - 110), x + 12, y + 10, Im.ACCENT);
            int yy = y + 28;
            if (pack.isProtected()) {
                yy = im.wrap(L.t("Этот пак защищён. Он будет скопирован как есть: открыть или изменить его нельзя.",
                    "This pack is protected. It will be copied as is: it cannot be opened or changed."), x + 12, yy, w - 24, Im.TEXT);
            } else {
                if (im.check(L.t("Открытый (.redux): bbmodel, json и текстуры как есть", "Open (.redux): bbmodel, json and textures as they are"), x + 12, yy, !protect)) protect = false;
                yy += 16;
                if (im.check(L.t("Защищённый (.reduxp): скомпилирован и зашифрован", "Protected (.reduxp): compiled and encrypted"), x + 12, yy, protect)) protect = true;
                yy += 18;
                if (protect) {
                    usePassword = im.check(L.t("Пароль на установку", "Password to install"), x + 24, yy, usePassword);
                    yy += 16;
                    if (usePassword) {
                        pw = im.field("pw", x + 24, yy, 150, pw, L.t("пароль (от 8 символов)", "password (8+ chars)"), true);
                        pw2 = im.field("pw2", x + 180, yy, 150, pw2, L.t("ещё раз", "repeat"), true);
                        yy += 18;
                    }
                    yy = im.wrap(L.t("Защита мешает открыть пак архиватором и Blockbench, но не гарантирует, что модель нельзя извлечь: игра обязана расшифровать её, чтобы нарисовать.",
                        "Protection stops people from opening the pack with an archiver or Blockbench, but cannot guarantee a model is impossible to extract: the game has to decrypt it to draw it."),
                        x + 24, yy + 2, w - 36, Im.DIM);
                }
            }
            if (!error.isEmpty()) im.wrap(error, x + 12, y + h - 62, w - 24, Im.BAD);
            if (!result.isEmpty()) im.wrap(L.t("Сохранено: ", "Saved: ") + result, x + 12, y + h - 62, w - 24, Im.ACCENT);
            boolean pwOk = !protect || !usePassword || pack.isProtected() || (pw.length() >= Protect.MIN_PASSWORD && pw.equals(pw2));
            if (im.button(L.t("Экспорт", "Export"), x + w / 2 - 104, y + h - 26, 100, 18, pwOk, 1)) {
                error = result = "";
                try {
                    Path out = Redux.export(pack, protect, protect && usePassword ? pw : null);
                    result = out.toString();
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
            }
            if (protect && usePassword && !pack.isProtected() && !pwOk && (!pw.isEmpty() || !pw2.isEmpty()))
                im.tip(x + w / 2 - 104, y + h - 26, 100, 18, pw.length() < Protect.MIN_PASSWORD ? L.t("Пароль короче 8 символов", "Password is shorter than 8 characters") : L.t("Пароли не совпадают", "Passwords do not match"));
            if (im.button(L.t("Закрыть", "Close"), x + w / 2 + 4, y + h - 26, 100, 18)) open(back);
        }
    }

    // ------------------------------------------------------------------ settings
    static final class Settings extends UiScreen {
        private final UiScreen back;
        Settings(UiScreen back) { this.back = back; }

        @Override public boolean escape() { Redux.config.save(); open(back); return true; }

        @Override protected void draw(Gfx g) {
            Config c = Redux.config;
            int w = Math.min(380, width - 20), h = Math.min(250, height - 10);
            int x = (width - w) / 2, y = (height - h) / 2;
            im.panel(x, y, w, h);
            im.label(L.t("Настройки REDUXion", "REDUXion settings"), x + 12, y + 10, Im.ACCENT);
            int lx = x + 12, yy = y + 28, half = w / 2;
            boolean was = c.enabled;
            c.enabled = im.check(L.t("Мод включён (клавиша: ", "Mod enabled (key: ") + Redux.platform.keyName() + ")", lx, yy, c.enabled);
            if (was != c.enabled) Redux.hud(Redux.statusLine(), 3000);
            yy += 16;
            c.items = im.check(L.t("Замена предметов", "Item replacements"), lx, yy, c.items);
            c.armor = im.check(L.t("Броня", "Armor"), lx + half - 20, yy, c.armor);
            c.trims = im.check("Trims", lx + half + 60, yy, c.trims);
            yy += 20;
            c.hud = im.check("HUD", lx, yy, c.hud);
            String[] corners = L.ru ? new String[]{"слева сверху", "справа сверху", "слева снизу", "справа снизу"}
                : new String[]{"top left", "top right", "bottom left", "bottom right"};
            if (im.button(L.t("Угол: ", "Corner: ") + corners[c.hudCorner], lx + 50, yy - 3, 150, 16)) { c.hudCorner = (c.hudCorner + 1) % 4; Redux.hud(Redux.statusLine(), 3000); }
            yy += 20;
            im.label(L.t("Прозрачность HUD", "HUD opacity") + ": " + Math.round(c.hudOpacity * 100) + "%", lx, yy, Im.TEXT);
            c.hudOpacity = im.slider("op", lx + half, yy - 1, half - 30, c.hudOpacity, 0.1f, 1f);
            yy += 16;
            im.label(L.t("Масштаб HUD", "HUD scale") + ": " + Math.round(c.hudScale * 100) + "%", lx, yy, Im.TEXT);
            c.hudScale = Math.round(im.slider("sc", lx + half, yy - 1, half - 30, c.hudScale, 0.5f, 2f) * 20) / 20f;
            if (im.isActive("op") || im.isActive("sc")) Redux.hud(Redux.statusLine(), 2000);
            yy += 20;
            c.showNotice = im.check(L.t("Показывать предупреждение при открытии меню", "Show the notice when the menu opens"), lx, yy, c.showNotice);
            yy += 16;
            c.confirmReload = im.check(L.t("Спрашивать перед перезагрузкой ресурсов", "Ask before reloading resources"), lx, yy, c.confirmReload);
            yy += 20;
            im.label(L.t("Сохранённые пароли паков: ", "Saved pack passwords: ") + c.passwords.size(), lx, yy, Im.TEXT);
            yy += 12;
            String forget = null;
            int n = 0;
            for (String id : c.passwords.keySet()) {
                if (yy > y + h - 44 || n++ > 3) break;
                ReduxPack p = Redux.find(id);
                im.label(im.fit(p != null ? p.manifest.name : id, w - 110), lx + 8, yy + 3, Im.DIM);
                if (im.button(L.t("Забыть", "Forget"), x + w - 82, yy, 70, 14)) forget = id;
                yy += 16;
            }
            if (forget != null) c.passwords.remove(forget);
            if (im.button(L.t("Готово", "Done"), x + w / 2 - 50, y + h - 24, 100, 18, true, 1)) { c.save(); open(back); }
        }
    }
}
