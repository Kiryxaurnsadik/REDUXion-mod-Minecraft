package dev.reduxion.ui;

import dev.reduxion.core.*;
import dev.reduxion.model.*;
import dev.reduxion.pack.*;
import dev.reduxion.view.SoftRenderer;
import java.nio.file.*;
import java.util.*;

/** REDUX pack studio: list of replacements, 3D preview with display positions, rule properties. */
public final class Studio extends UiScreen {
    private final ReduxPack pack;
    private final boolean readOnly;
    private Rule sel;
    private String search = "";
    private int filter; // 0 all, 1 item, 2 armor, 3 trim
    private int listScroll;
    private boolean dirty;
    private String status = "";
    private int statusColor = Im.DIM;

    private final SoftRenderer renderer = new SoftRenderer();
    private boolean previewDirty = true;
    private int previewSize;
    private Mesh mesh;
    private List<Png.Image> meshTextures = List.of();
    private CubeModel meshModel;
    private Rule meshRule;
    private boolean armorSecond;
    private List<String> itemIds;

    public Studio(ReduxPack pack) {
        this.pack = pack;
        this.readOnly = pack.isProtected();
        if (!pack.rules.isEmpty()) sel = pack.rules.get(0);
    }

    @Override public void opened() {
        itemIds = Redux.platform.itemIds();
        if (!readOnly) PackCompiler.compile(pack);
        previewDirty = true;
        meshRule = null;
    }

    @Override public boolean escape() {
        leave();
        return true;
    }

    private void leave() {
        PacksScreen back = new PacksScreen();
        Screens.open(back);
        if (dirty) { save(); Screens.applyThen(back); }
    }

    private void say(String s, int color) { status = s; statusColor = color; }

    private void save() {
        if (readOnly) return;
        try {
            for (Rule r : pack.rules) {
                r.item = Rule.normId(r.item);
                if (r.itemModel != null) r.itemModel = r.itemModel.trim().isEmpty() ? null : Rule.normId(r.itemModel);
                if (r.assetId != null) r.assetId = r.assetId.trim().isEmpty() ? null : Rule.normId(r.assetId);
                if (r.trimPattern != null) r.trimPattern = r.trimPattern.trim().isEmpty() ? null : Rule.normId(r.trimPattern);
                if (r.trimMaterial != null) r.trimMaterial = r.trimMaterial.trim().isEmpty() ? null : Rule.normId(r.trimMaterial);
                r.cmdStrings.removeIf(String::isEmpty);
            }
            Redux.save(pack);
            dirty = false;
            meshRule = null;
            previewDirty = true;
            say(pack.warnings.isEmpty() ? L.t("Сохранено", "Saved") : pack.warnings.get(0), pack.warnings.isEmpty() ? Im.ACCENT : Im.WARN);
        } catch (Exception e) {
            say(L.t("Ошибка сохранения: ", "Save failed: ") + e.getMessage(), Im.BAD);
        }
    }

    private void touch() { dirty = true; }

    // ------------------------------------------------------------ files
    @Override public void filesDropped(List<Path> files) {
        if (readOnly) return;
        for (Path f : files) addFile(f, 0);
    }

    /** @param slot 0 auto/model, 1 layer1/texture, 2 layer2, 3 extra texture for a json model */
    private void addFile(Path f, int slot) {
        if (sel == null) { say(L.t("Сначала выберите или создайте замену", "Select or create a replacement first"), Im.WARN); return; }
        try {
            String name = f.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (Files.size(f) > (32L << 20)) throw new Exception(L.t("файл больше 32 МБ", "file is larger than 32 MB"));
            byte[] data = Files.readAllBytes(f);
            String clean = name.replaceAll("[^A-Za-z0-9_.\\-]", "_");
            boolean png = lower.endsWith(".png");
            if (png && Png.size(data) == null) throw new Exception(L.t("это не PNG", "not a PNG"));
            if (!Rule.ITEM.equals(sel.target)) {
                if (!png) throw new Exception(L.t("для брони и trims нужны PNG", "armor and trims need PNG files"));
                String path = "textures/" + clean;
                pack.files.put(path, data);
                if (slot == 2 || (slot == 0 && sel.layer1 != null && !sel.layer1.isEmpty() && (sel.layer2 == null || sel.layer2.isEmpty()))) sel.layer2 = path;
                else sel.layer1 = path;
            } else if (png && (slot == 3 || (slot == 0 && sel.model.toLowerCase(Locale.ROOT).endsWith(".json")))) {
                pack.files.put("textures/" + clean, data);
            } else if (png) {
                String path = "textures/" + clean;
                pack.files.put(path, data);
                sel.model = path;
            } else if (lower.endsWith(".mcmeta")) {
                // animation metadata for a texture dropped separately (name like foo.png.mcmeta)
                pack.files.put("textures/" + clean, data);
            } else if (lower.endsWith(".bbmodel") || lower.endsWith(".json")) {
                String path = "models/" + clean;
                pack.files.put(path, data);
                sel.model = path;
            } else {
                throw new Exception(L.t("поддерживаются .bbmodel, .json и .png", ".bbmodel, .json and .png are supported"));
            }
            PackCompiler.compile(pack);
            meshRule = null;
            previewDirty = true;
            touch();
            say(L.t("Добавлен файл ", "Added file ") + clean, Im.ACCENT);
            if (!pack.warnings.isEmpty()) say(pack.warnings.get(pack.warnings.size() - 1), Im.WARN);
        } catch (Exception e) {
            say(L.t("Не удалось добавить файл: ", "Could not add the file: ") + e.getMessage(), Im.BAD);
        }
    }

    private void pick(int slot) {
        Path p = slot == 0
            ? FilePick.open(L.t("Модель или текстура", "Model or texture"), "bbmodel, json, png", "*.bbmodel", "*.json", "*.png")
            : FilePick.open(L.t("Текстура", "Texture"), "PNG", "*.png");
        if (p != null) addFile(p, slot);
        else say(L.t("Файл не выбран. Можно перетащить файл в окно игры.", "No file chosen. You can drop a file onto the game window."), Im.DIM);
    }

    // ------------------------------------------------------------ preview
    private void prepareMesh() {
        if (meshRule == sel && mesh != null) return;
        meshRule = sel;
        mesh = null;
        meshModel = null;
        meshTextures = List.of();
        if (sel == null) return;
        CubeModel m = null;
        if (Rule.ITEM.equals(sel.target)) {
            if (sel.modelKey != null) m = pack.models.get(sel.modelKey);
        } else {
            String key = armorSecond ? sel.layer2Key : sel.layer1Key;
            if (key == null) key = sel.layer1Key != null ? sel.layer1Key : sel.layer2Key;
            byte[] png = key == null ? null : pack.images.get(key);
            if (png != null) m = JsonModelReader.flat(png, false);
        }
        if (m == null || (m.isFlat() && m.layers.isEmpty())) return;
        meshModel = m;
        mesh = Mesh.build(m);
        List<Png.Image> tex = new ArrayList<>();
        for (CubeModel.Tex t : m.textures) {
            Png.Image img = Png.decode(t.png);
            if (t.animated()) {
                // preview shows the first animation frame only
                int fh = Math.max(1, img.h / t.frames);
                int[] px = new int[img.w * fh];
                System.arraycopy(img.argb, 0, px, 0, Math.min(px.length, img.argb.length));
                img = new Png.Image(img.w, fh, px);
            }
            tex.add(img);
        }
        meshTextures = tex;
    }

    private Map<String, float[]> displayMap() {
        Map<String, float[]> d = new HashMap<>();
        if (meshModel != null) d.putAll(meshModel.display);
        if (sel != null) d.putAll(sel.display);
        return d;
    }

    private void drawPreview(Gfx g, int x, int y, int size) {
        prepareMesh();
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        if (mesh == null) {
            g.fill(x, y, x + size, y + size, 0xC0202024);
            String msg;
            if (sel == null) msg = L.t("Замена не выбрана", "No replacement selected");
            else if (Rule.ITEM.equals(sel.target) && sel.model.startsWith("item:")) msg = L.t("Вид ванильного предмета", "Look of a vanilla item");
            else if (Rule.ITEM.equals(sel.target) && sel.model.startsWith("vanilla:")) msg = L.t("Ванильная модель", "Vanilla model");
            else msg = L.t("Нет модели: добавьте файл", "No model: add a file");
            im.labelCentered(msg, x + size / 2, y + size / 2 - 4, Im.DIM);
            if (sel != null && sel.model.startsWith("item:")) g.item(Rule.normId(sel.model.substring(5)), x + size / 2 - 8, y + size / 2 + 10);
            return;
        }
        boolean isItem = Rule.ITEM.equals(sel.target);
        if (!isItem) renderer.mode = "edit";
        // mouse: drag rotates, wheel zooms
        if (im.clicked(x, y, size, size)) im.setActive("preview");
        if (im.isActive("preview") && im.down && (im.dragDx != 0 || im.dragDy != 0)) {
            renderer.yaw += (float) im.dragDx * 1.2f;
            renderer.pitch = Math.max(-89, Math.min(89, renderer.pitch + (float) im.dragDy * 1.2f));
            previewDirty = true;
        }
        if (im.over(x, y, size, size) && im.scroll != 0) {
            renderer.zoom = Math.max(0.3f, Math.min(6f, renderer.zoom * (float) Math.pow(1.15, im.scroll)));
            previewDirty = true;
        }
        int res = Math.max(64, Math.min(360, size * 2));
        if (previewDirty || res != previewSize) {
            previewSize = res;
            previewDirty = false;
            renderer.watermark = true;
            int[] px = renderer.render(mesh, meshTextures, displayMap(), res, res);
            Redux.platform.image("preview", res, res, px);
        }
        g.image("preview", x, y, size, size);
    }

    private static final String[][] MODE_NAMES = {
        {"edit", "Модель", "Model"}, {"thirdperson_righthand", "3-е лицо П", "3rd R"}, {"thirdperson_lefthand", "3-е лицо Л", "3rd L"},
        {"firstperson_righthand", "1-е лицо П", "1st R"}, {"firstperson_lefthand", "1-е лицо Л", "1st L"},
        {"head", "Голова", "Head"}, {"ground", "Земля", "Ground"}, {"fixed", "Рамка", "Frame"}, {"gui", "GUI", "GUI"}};

    // ------------------------------------------------------------ draw
    @Override protected void draw(Gfx g) {
        int pad = 6, top = 6, bottom = height - 6;
        int x0 = pad, w = width - pad * 2;
        im.panel(x0, top, w, bottom - top);
        // header
        g.text(im.fit(L.t("Студия: ", "Studio: ") + pack.manifest.name, w - 330), x0 + 8, top + 8, Im.ACCENT, true);
        int hx = x0 + w - 8;
        if (im.button(L.t("Назад", "Back"), hx - 56, top + 4, 56, 16)) { leave(); return; }
        if (!readOnly) {
            if (im.button(L.t("Применить", "Apply"), hx - 56 - 4 - 74, top + 4, 74, 16, true, 1)) { save(); Screens.applyThen(this); }
            if (im.button(L.t("Сведения", "Details"), hx - 56 - 4 - 74 - 4 - 68, top + 4, 68, 16)) { save(); Screens.open(new Screens.Details(pack, this)); return; }
            if (im.button(L.t("Экспорт", "Export"), hx - 56 - 4 - 74 - 4 - 68 - 4 - 60, top + 4, 60, 16)) { save(); Screens.open(new Screens.Export(pack, this)); return; }
        } else {
            im.badge(L.t("защищённый пак: только просмотр", "protected pack: view only"), hx - 56 - 8 - g.textWidth(L.t("защищённый пак: только просмотр", "protected pack: view only")) - 6, top + 7, 0xCC7A4A1A);
        }
        int bodyY = top + 26, bodyH = bottom - bodyY - 16;
        int leftW = Math.max(120, Math.min(170, w / 4)), rightW = readOnly ? 0 : Math.max(150, Math.min(190, w / 3));
        int cx = x0 + 6 + leftW + 6, cw = w - 12 - leftW - 6 - (rightW > 0 ? rightW + 6 : 0);
        drawList(g, x0 + 6, bodyY, leftW, bodyH);
        drawCenter(g, cx, bodyY, cw, bodyH);
        if (rightW > 0) drawProps(g, cx + cw + 6, bodyY, rightW, bodyH);
        g.text(im.fit(status, w - 16), x0 + 8, bottom - 12, statusColor, true);
    }

    private boolean visible(Rule r) {
        if (filter == 1 && !Rule.ITEM.equals(r.target)) return false;
        if (filter == 2 && !Rule.ARMOR.equals(r.target)) return false;
        if (filter == 3 && !Rule.TRIM.equals(r.target)) return false;
        if (search.isEmpty()) return true;
        String s = search.toLowerCase(Locale.ROOT);
        return r.id.toLowerCase(Locale.ROOT).contains(s) || r.item.contains(s) || (r.model != null && r.model.toLowerCase(Locale.ROOT).contains(s))
            || (r.trimPattern != null && r.trimPattern.contains(s)) || (r.group != null && r.group.toLowerCase(Locale.ROOT).contains(s));
    }

    private String howText(Rule r) {
        String h = r.how();
        return switch (h) {
            case "*" -> L.t("весь предмет", "whole item");
            case "armor" -> L.t("броня", "armor");
            default -> h;
        };
    }

    private void drawList(Gfx g, int x, int y, int w, int h) {
        search = im.field("search", x, y, w - 46, search, L.t("поиск", "search"));
        String[] fn = L.ru ? new String[]{"все", "предм.", "броня", "trim"} : new String[]{"all", "items", "armor", "trim"};
        if (im.button(fn[filter], x + w - 44, y, 44, 14)) { filter = (filter + 1) % 4; listScroll = 0; }
        int ly = y + 18, lh = h - 18 - (readOnly ? 0 : 38);
        g.fill(x, ly, x + w, ly + lh, 0x70000000);
        List<Rule> shown = new ArrayList<>();
        for (Rule r : pack.rules) if (visible(r)) shown.add(r);
        // order rules by group (groups keep first-seen order, rules keep pack order); insert a header per group
        LinkedHashMap<String, List<Rule>> groups = new LinkedHashMap<>();
        for (Rule r : shown) groups.computeIfAbsent(r.group == null ? "" : r.group, k -> new ArrayList<>()).add(r);
        boolean showHeaders = groups.size() > 1 || (groups.size() == 1 && !groups.containsKey(""));
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<String, List<Rule>> e : groups.entrySet()) {
            if (showHeaders) entries.add(e.getKey());
            entries.addAll(e.getValue());
        }
        int row = 22, head = 13;
        int content = 0;
        for (Object o : entries) content += o instanceof Rule ? row : head;
        if (im.over(x, ly, w, lh)) listScroll -= (int) (im.scroll * 22);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, content - lh)));
        g.clip(x, ly, x + w, ly + lh);
        int ry = ly - listScroll;
        for (Object o : entries) {
            if (o instanceof String grp) {
                if (ry + head >= ly && ry <= ly + lh) {
                    g.fill(x, ry + 1, x + w - 4, ry + head, 0x40000000);
                    String name = grp.isEmpty() ? L.t("Без группы", "Ungrouped") : grp;
                    g.text(im.fit("▸ " + name, w - 10), x + 4, ry + 3, Im.WARN, false);
                }
                ry += head;
                continue;
            }
            Rule r = (Rule) o;
            if (ry + row >= ly && ry <= ly + lh) {
                boolean hov = im.over(x, ly, w, lh) && im.over(x, ry, w - 4, row);
                if (r == sel) g.fill(x, ry, x + w - 4, ry + row, 0xA0355E35);
                else if (hov) g.fill(x, ry, x + w - 4, ry + row, 0x60FFFFFF);
                if (!Rule.TRIM.equals(r.target) && !r.item.isEmpty()) g.item(Rule.normId(r.item), x + 2, ry + 3);
                else im.label("T", x + 7, ry + 7, Im.DIM);
                String target = Rule.TRIM.equals(r.target) ? (r.trimPattern == null ? L.t("любой узор", "any pattern") : strip(r.trimPattern)) : strip(r.item);
                g.text(im.fit(target.isEmpty() ? r.id : target, w - 30), x + 22, ry + 2, 0xFFFFFFFF, false);
                String res = Rule.ITEM.equals(r.target) ? shortModel(r.model) : shortModel(r.layer1 != null ? r.layer1 : r.layer2);
                if (readOnly && res.equals("—")) res = Rule.ITEM.equals(r.target) ? L.t("модель", "model") : L.t("текстура", "texture");
                String how = howText(r) + (r.adapt ? "↔" : "") + " → " + res;
                g.text(im.fit(how, w - 30), x + 22, ry + 12, Im.DIM, false);
                if (hov && im.clicked(x, ry, w - 4, row)) {
                    sel = r;
                    meshRule = null;
                    previewDirty = true;
                    Redux.platform.click();
                }
            }
            ry += row;
        }
        g.unclip();
        im.scrollbar(x + w - 3, ly, lh, content, listScroll);
        if (shown.isEmpty()) im.labelCentered(pack.rules.isEmpty() ? L.t("Замен пока нет", "No replacements yet") : L.t("Ничего не найдено", "Nothing found"), x + w / 2, ly + 10, Im.DIM);
        if (!readOnly) {
            int by = y + h - 34, bw = (w - 4) / 3;
            if (im.button(L.t("+ Предмет", "+ Item"), x, by, bw, 15, true, 1)) add(Rule.ITEM);
            if (im.button(L.t("+ Броня", "+ Armor"), x + bw + 2, by, bw, 15, true, 1)) add(Rule.ARMOR);
            if (im.button("+ Trim", x + 2 * bw + 4, by, w - 2 * bw - 4, 15, true, 1)) add(Rule.TRIM);
            if (im.button(L.t("Дублировать", "Duplicate"), x, by + 18, w / 2 - 1, 15, sel != null, 0)) {
                Rule c = sel.copy();
                c.id = uniqueId(sel.id);
                pack.rules.add(pack.rules.indexOf(sel) + 1, c);
                sel = c;
                PackCompiler.compile(pack);
                meshRule = null;
                touch();
            }
            if (im.button(L.t("Удалить", "Delete"), x + w / 2 + 1, by + 18, w - w / 2 - 1, 15, sel != null, 2)) {
                int i = pack.rules.indexOf(sel);
                pack.rules.remove(sel);
                sel = pack.rules.isEmpty() ? null : pack.rules.get(Math.max(0, Math.min(i, pack.rules.size() - 1)));
                meshRule = null;
                previewDirty = true;
                touch();
            }
        }
    }

    private static String strip(String id) { return id != null && id.startsWith("minecraft:") ? id.substring(10) : id == null ? "" : id; }

    private static String shortModel(String m) {
        if (m == null || m.isEmpty()) return "—";
        if (m.startsWith("item:")) return strip(m.substring(5));
        int i = m.lastIndexOf('/');
        return i >= 0 ? m.substring(i + 1) : m;
    }

    private String uniqueId(String base) {
        Set<String> ids = new HashSet<>();
        for (Rule r : pack.rules) ids.add(r.id);
        String b = base.replaceAll("_\\d+$", "");
        for (int i = 2; ; i++) if (!ids.contains(b + "_" + i)) return b + "_" + i;
    }

    private void add(String target) {
        Rule r = new Rule();
        r.target = target;
        r.id = uniqueId(target);
        // a new rule joins the group of the one being viewed, so related replacements stay together
        if (sel != null && sel.group != null) r.group = sel.group;
        Platform.Held h = Redux.platform.held();
        if (Rule.ITEM.equals(target)) r.item = h != null && !h.item.isEmpty() ? h.item : "minecraft:stick";
        else if (Rule.ARMOR.equals(target)) r.item = h != null && h.armor ? h.item : "minecraft:diamond_chestplate";
        pack.rules.add(r);
        sel = r;
        filter = 0;
        meshRule = null;
        previewDirty = true;
        touch();
    }

    private void drawCenter(Gfx g, int x, int y, int w, int h) {
        boolean isItem = sel != null && Rule.ITEM.equals(sel.target);
        boolean sliders = isItem && !readOnly && !"edit".equals(renderer.mode) && mesh != null;
        int below = (w < 230 ? 49 : 34) + (sliders ? 56 : 0);
        int size = Math.max(60, Math.min(w, h - below));
        int px = x + (w - size) / 2;
        drawPreview(g, px, y, size);
        int by = y + size + 4;
        if (mesh == null) return;
        if (isItem) {
            // display position buttons, two rows
            int n = MODE_NAMES.length, perRow = w < 230 ? 3 : 5, rows = (n + perRow - 1) / perRow, bw = (w - 18 - (perRow - 1) * 2) / perRow;
            for (int i = 0; i < n; i++) {
                int bx = x + (i % perRow) * (bw + 2), yy = by + (i / perRow) * 15;
                boolean cur = MODE_NAMES[i][0].equals(renderer.mode);
                if (im.button(L.t(MODE_NAMES[i][1], MODE_NAMES[i][2]), bx, yy, bw, 14, true, cur ? 1 : 0) && !cur) {
                    renderer.mode = MODE_NAMES[i][0];
                    boolean mannequin = renderer.mode.startsWith("thirdperson") || renderer.mode.equals("head");
                    renderer.yaw = mannequin ? 215 : 35;
                    renderer.pitch = 25;
                    renderer.zoom = 1;
                    previewDirty = true;
                }
            }
            if (im.button("⟲", x + w - 16, by, 16, 14)) {
                renderer.yaw = 35; renderer.pitch = 25; renderer.zoom = 1; previewDirty = true;
            }
            by += rows * 15 + 2;
        } else {
            boolean two = sel.layer1Key != null && sel.layer2Key != null;
            String a = Rule.TRIM.equals(sel.target) ? L.t("Основная", "Main") : L.t("Слой 1", "Layer 1");
            String b = Rule.TRIM.equals(sel.target) ? L.t("Поножи", "Leggings") : L.t("Слой 2 (поножи)", "Layer 2 (leggings)");
            if (im.button(a, x, by, 90, 14, true, !armorSecond ? 1 : 0) && armorSecond) { armorSecond = false; meshRule = null; previewDirty = true; }
            if (im.button(b, x + 92, by, 110, 14, two, armorSecond ? 1 : 0) && !armorSecond) { armorSecond = true; meshRule = null; previewDirty = true; }
        }
        if (sliders) {
            String mode = renderer.mode;
            float[] cur = displayMap().get(mode);
            float[] v = cur == null ? CubeModel.defaultDisplay() : cur.clone();
            String[] names = L.ru ? new String[]{"Поворот", "Сдвиг", "Масштаб"} : new String[]{"Rotate", "Move", "Scale"};
            float[][] range = {{-180, 180}, {-80, 80}, {0, 4}};
            boolean changed = false;
            int lw = 46, sw = Math.max(20, (w - lw - 44 - 4) / 3);
            for (int row = 0; row < 3; row++) {
                int yy = by + row * 13;
                g.text(names[row], x, yy + 1, Im.DIM, false);
                for (int a = 0; a < 3; a++) {
                    int i = row * 3 + a;
                    float nv = im.slider("d" + i, x + lw + a * (sw + 2), yy, sw, v[i], range[row][0], range[row][1]);
                    if (nv != v[i]) {
                        nv = row == 2 ? Math.round(nv * 20) / 20f : row == 0 ? Math.round(nv / 2.5f) * 2.5f : Math.round(nv * 4) / 4f;
                        if (nv != v[i]) { v[i] = nv; changed = true; }
                    }
                }
                g.text(fmt(v[row * 3]) + " " + fmt(v[row * 3 + 1]) + " " + fmt(v[row * 3 + 2]), x + lw + 3 * (sw + 2) + 2, yy + 1, Im.DIM, false);
            }
            if (changed) {
                sel.display.put(mode, v);
                previewDirty = true;
                touch();
            }
            if (sel.display.containsKey(mode)) {
                if (im.button(L.t("Сбросить позицию", "Reset position"), x, by + 40, 110, 13)) {
                    sel.display.remove(mode);
                    previewDirty = true;
                    touch();
                }
            }
        }
    }

    private static String fmt(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.format(Locale.ROOT, "%.2f", f);
    }

    private int propY;
    private String prop(String label, String id, int x, int w, String value, String hint) {
        im.label(label, x, propY, Im.DIM);
        String before = value == null ? "" : value;
        String v = im.field(id + System.identityHashCode(sel), x, propY + 10, w, before, hint);
        propY += 28;
        if (!v.equals(before)) touch();
        return v;
    }

    private void drawProps(Gfx g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x50000000);
        if (sel == null) {
            im.wrap(L.t("Создайте замену кнопками «+» слева.", "Create a replacement with the \"+\" buttons on the left."), x + 6, y + 6, w - 12, Im.DIM);
            return;
        }
        Rule r = sel;
        int fx = x + 6, fw = w - 12;
        propY = y + 5;
        boolean item = Rule.ITEM.equals(r.target), armor = Rule.ARMOR.equals(r.target), trim = Rule.TRIM.equals(r.target);
        im.label(item ? L.t("Замена предмета", "Item replacement") : armor ? L.t("Текстура брони", "Armor texture") : L.t("Узор отделки (trim)", "Armor trim"), fx, propY, Im.ACCENT);
        propY += 12;
        boolean canGive = (item || armor) && r.item != null && !r.item.isEmpty();
        int thW = canGive ? fw - 56 : fw;
        if (im.button(L.t("Взять из руки", "Take from hand"), fx, propY, thW, 15)) takeFromHand(r);
        im.tip(fx, propY, thW, 15, L.t("Подставить id и компоненты предмета в руке", "Fill in the id and components of the held item"));
        if (canGive) {
            if (im.button(L.t("Выдать", "Give"), fx + fw - 52, propY, 52, 15, true, 1)) say(Redux.platform.giveItem(r), Im.ACCENT);
            im.tip(fx + fw - 52, propY, 52, 15, L.t("Выдать этот предмет (нужен творческий режим)", "Give this item (creative mode)"));
        }
        propY += 19;
        if (im.button(L.t("Из прицела", "From target"), fx, propY, fw, 15)) takeFromTarget(r);
        im.tip(fx, propY, fw, 15, L.t("Взять предмет с того, на кого смотрите (игрок, стойка, рамка, item display)",
            "Take the item from what you look at (player, armor stand, item frame, item display)"));
        propY += 19;
        boolean modern = Redux.platform.modernItems();
        if (!trim) {
            r.item = prop(L.t("Предмет (id)", "Item (id)"), "item", fx, fw, r.item, "minecraft:diamond_sword");
            String norm = Rule.normId(r.item);
            if (!r.item.isEmpty() && itemIds != null && !itemIds.isEmpty() && !itemIds.contains(norm)) {
                g.text(im.fit(L.t("нет такого предмета", "unknown item"), fw), fx, propY - 4, Im.WARN, false);
                propY += 8;
            }
            String cmdBefore = r.cmd == null ? "" : r.cmd.toString();
            String cmd = prop("custom_model_data", "cmd", fx, fw, cmdBefore, L.t("пусто = любой", "empty = any"));
            if (!cmd.equals(cmdBefore)) {
                String digits = cmd.replaceAll("[^0-9]", "");
                try { r.cmd = digits.isEmpty() ? null : Integer.parseInt(digits.length() > 9 ? digits.substring(0, 9) : digits); } catch (NumberFormatException ignored) { }
            }
            if (propY < y + h - 120) {
                String sBefore = r.cmdStrings.isEmpty() ? "" : r.cmdStrings.get(0);
                String s = prop("custom_model_data strings[0]" + (modern ? "" : " (1.21.11)"), "cmds", fx, fw, sBefore, L.t("пусто = любой", "empty = any"));
                if (!s.equals(sBefore)) { r.cmdStrings.clear(); if (!s.isEmpty()) r.cmdStrings.add(s); }
                r.itemModel = emptyNull(prop("item_model" + (modern ? "" : " (1.21.11)"), "im", fx, fw, r.itemModel, L.t("пусто = любой", "empty = any")));
                if (r.cmd != null || r.itemModel != null) {
                    boolean na = im.check(L.t("Авто item_model ↔ CMD", "Auto item_model ↔ CMD"), fx, propY, r.adapt);
                    if (na != r.adapt) { r.adapt = na; touch(); }
                    im.tip(fx, propY, fw, 12, L.t("Совпадение по любому из двух — одна замена для старых и новых серверов",
                        "Match on either one — one replacement for old and new servers"));
                    propY += 16;
                }
            }
            if (armor && propY < y + h - 100) r.assetId = emptyNull(prop("equippable asset_id" + (modern ? "" : " (1.21.11)"), "asset", fx, fw, r.assetId, L.t("пусто = любой", "empty = any")));
        } else {
            r.trimPattern = emptyNull(prop(L.t("Узор (id)", "Pattern (id)"), "pat", fx, fw, r.trimPattern, L.t("пусто = любой", "empty = any")));
            r.trimMaterial = emptyNull(prop(L.t("Материал (id)", "Material (id)"), "mat", fx, fw, r.trimMaterial, L.t("пусто = любой", "empty = any")));
        }
        if (item) {
            im.label(L.t("На что заменить", "Replace with"), fx, propY, Im.DIM);
            propY += 10;
            g.text(im.fit(r.model.isEmpty() ? "—" : r.model, fw), fx, propY, r.model.isEmpty() ? Im.WARN : Im.TEXT, false);
            propY += 11;
            if (im.button(L.t("Файл модели…", "Model file…"), fx, propY, fw, 15)) pick(0);
            im.tip(fx, propY, fw, 15, ".bbmodel / .json / .png");
            propY += 17;
            if (r.model.toLowerCase(Locale.ROOT).endsWith(".json")) {
                if (im.button(L.t("Текстура для json…", "Texture for json…"), fx, propY, fw, 15)) pick(3);
                propY += 17;
            }
            if (r.model.toLowerCase(Locale.ROOT).endsWith(".png")) {
                boolean hh = im.check(L.t("Как инструмент в руке", "Held like a tool"), fx, propY, r.handheld);
                if (hh != r.handheld) { r.handheld = hh; PackCompiler.compile(pack); meshRule = null; touch(); }
                propY += 15;
            }
            String vBefore = r.model.startsWith("item:") ? r.model.substring(5) : "";
            String v = prop(L.t("или вид ванильного предмета", "or the look of a vanilla item"), "van", fx, fw, vBefore, "minecraft:diamond_block");
            if (!v.equals(vBefore)) {
                r.model = v.isEmpty() ? "" : "item:" + v.trim();
                r.modelKey = null;
                meshRule = null;
                previewDirty = true;
            }
        } else {
            String l1 = armor ? L.t("Слой 1: шлем, нагрудник, ботинки", "Layer 1: helmet, chest, boots") : L.t("Текстура узора", "Pattern texture");
            String l2 = armor ? L.t("Слой 2: поножи", "Layer 2: leggings") : L.t("Текстура для поножей", "Leggings texture");
            im.label(im.fit(l1, fw), fx, propY, Im.DIM);
            propY += 10;
            g.text(im.fit(r.layer1 == null || r.layer1.isEmpty() ? "—" : shortModel(r.layer1), fw - 54), fx, propY + 3, Im.TEXT, false);
            if (im.button("PNG…", fx + fw - 50, propY, 50, 14)) pick(1);
            propY += 18;
            im.label(im.fit(l2, fw), fx, propY, Im.DIM);
            propY += 10;
            g.text(im.fit(r.layer2 == null || r.layer2.isEmpty() ? "—" : shortModel(r.layer2), fw - 54), fx, propY + 3, Im.TEXT, false);
            if (im.button("PNG…", fx + fw - 50, propY, 50, 14)) pick(2);
            propY += 18;
            if (trim) propY = im.wrap(L.t("Текстура рисуется как есть, в своих цветах.", "The texture is drawn as is, in its own colours."), fx, propY, fw, Im.DIM);
        }
        // versions
        if (propY < y + h - 26) {
            im.label(L.t("Версии игры", "Game versions"), fx, propY + 2, Im.DIM);
            propY += 13;
            boolean a = r.versions.isEmpty() || r.versions.contains("1.21.1"), b = r.versions.isEmpty() || r.versions.contains("1.21.11");
            boolean na = im.check("1.21.1", fx, propY, a), nb = im.check("1.21.11", fx + 64, propY, b);
            if (na != a || nb != b) {
                if (!na && !nb) { na = a; nb = b; }
                r.versions.clear();
                if (!(na && nb)) r.versions.add(na ? "1.21.1" : "1.21.11");
                touch();
            }
            propY += 16;
        }
        if (propY < y + h - 24) {
            r.group = prop(L.t("Группа", "Group"), "grp", fx, fw, r.group, L.t("без группы", "ungrouped"));
            if (r.group != null) r.group = r.group.trim();
        }
    }

    private static String emptyNull(String s) { return s == null || s.isEmpty() ? null : s; }

    private void takeFromHand(Rule r) {
        capture(r, Redux.platform.held(),
            L.t("В руке ничего нет", "Nothing in the hand"),
            L.t("Взято из руки: ", "Taken from hand: "));
    }

    private void takeFromTarget(Rule r) {
        capture(r, Redux.platform.lookedAt(),
            L.t("Под прицелом нет предмета. Наведитесь на игрока, стойку, рамку или item display.",
                "No item under the crosshair. Look at a player, armor stand, item frame or item display."),
            L.t("Взято с цели: ", "Taken from target: "));
    }

    private void capture(Rule r, Platform.Held h, String emptyMsg, String okPrefix) {
        if (h == null || h.item.isEmpty()) { say(emptyMsg, Im.WARN); return; }
        if (Rule.TRIM.equals(r.target)) {
            if (h.trimPattern == null) { say(L.t("На этом предмете нет отделки", "This item has no trim"), Im.WARN); return; }
            r.trimPattern = h.trimPattern;
            r.trimMaterial = h.trimMaterial;
        } else {
            r.item = h.item;
            r.cmd = h.cmd;
            r.cmdStrings.clear();
            r.cmdStrings.addAll(h.cmdStrings);
            r.cmdFlags.clear();
            r.cmdFlags.addAll(h.cmdFlags);
            r.itemModel = h.itemModel;
            r.assetId = Rule.ARMOR.equals(r.target) ? h.assetId : null;
            // if the server identifies the item both ways, default to adapting between them
            if (r.cmd != null && r.itemModel != null) r.adapt = true;
        }
        touch();
        say(okPrefix + h.item, Im.ACCENT);
    }
}
