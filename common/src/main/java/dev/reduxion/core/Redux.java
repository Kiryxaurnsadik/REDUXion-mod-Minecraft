package dev.reduxion.core;

import com.google.gson.JsonObject;
import dev.reduxion.model.*;
import dev.reduxion.pack.*;
import dev.reduxion.protect.Protect;
import dev.reduxion.rules.RuleTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Central state of the mod. All game versions talk to this class. */
public final class Redux {
    private Redux() {}

    public static final String NS = "reduxion";
    public static Platform platform;
    public static Config config;
    public static final List<ReduxPack> packs = new ArrayList<>();
    /** Swapped atomically; read from the render thread. */
    public static volatile RuleTable table = RuleTable.EMPTY;

    /** modelKey -> model, for every installed pack (enabled or not) so toggling needs no reload. */
    private static volatile Map<String, CubeModel> models = Map.of();
    private static volatile Map<String, byte[]> images = Map.of();
    private static volatile Map<String, byte[]> virtualFiles = Map.of();
    private static String loadedSignature = "";
    private static String wantedSignature = "";
    public static boolean reloading;
    public static String hudMessage = "";
    public static long hudMessageUntil;

    public static Path packsDir() { return platform.configDir().resolve("packs"); }
    public static Path exportDir() { return platform.configDir().resolve("export"); }

    public static void init(Platform p) {
        platform = p;
        config = Config.load(p.configDir().resolve("config.json"));
        try {
            Files.createDirectories(packsDir());
            Files.createDirectories(exportDir());
        } catch (IOException ignored) {
        }
        loadAll();
        rebuild();
        loadedSignature = wantedSignature;
    }

    public static void loadAll() {
        packs.clear();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(packsDir())) {
            for (Path f : ds) {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(PackIO.EXT_OPEN) || n.endsWith(PackIO.EXT_PROTECTED)) files.add(f);
            }
        } catch (IOException ignored) {
        }
        Collections.sort(files);
        for (Path f : files) {
            try {
                ReduxPack pack = PackIO.load(f, null);
                if (pack.locked && pack.manifest.hasPassword) {
                    String pw = config.recall(pack.manifest.id);
                    if (pw != null) pack = PackIO.load(f, pw.toCharArray());
                }
                packs.add(pack);
            } catch (Exception e) {
                ReduxPack bad = new ReduxPack();
                bad.manifest.id = bad.manifest.name = f.getFileName().toString();
                bad.file = f;
                bad.error = String.valueOf(e.getMessage());
                packs.add(bad);
            }
        }
        sortPacks();
    }

    private static void sortPacks() {
        for (ReduxPack p : packs) if (!config.order.contains(p.manifest.id)) config.order.add(p.manifest.id);
        packs.sort(Comparator.comparingInt(p -> config.order.indexOf(p.manifest.id)));
    }

    public static boolean isEnabled(ReduxPack p) { return !config.disabled.contains(p.manifest.id); }

    public static boolean usable(ReduxPack p) { return p.error == null && !p.locked; }

    public static int activeCount() {
        int n = 0;
        for (ReduxPack p : packs) if (usable(p) && isEnabled(p)) n++;
        return n;
    }

    /** Rebuilds the rule table and the generated resources. Cheap; call after any change. */
    public static void rebuild() {
        String ver = platform.gameVersion();
        List<RuleTable.Entry> entries = new ArrayList<>();
        Map<String, CubeModel> allModels = new HashMap<>();
        Map<String, byte[]> allImages = new HashMap<>();
        int order = 0;
        for (ReduxPack p : packs) {
            order++;
            if (!usable(p)) continue;
            p.models.forEach((k, m) -> allModels.put(k, m.padded()));
            allImages.putAll(p.images);
            if (!isEnabled(p)) continue;
            for (Rule r : p.rules) if (r.appliesTo(ver)) entries.add(new RuleTable.Entry(r, order));
        }
        Map<String, byte[]> files = new HashMap<>();
        boolean modern = platform.modernItems();
        int slots = modern ? 9 : 8;
        TreeSet<String> sig = new TreeSet<>();
        for (Map.Entry<String, CubeModel> e : allModels.entrySet()) {
            CubeModel m = e.getValue();
            String key = e.getKey();
            sig.add(key);
            List<String> texIds = new ArrayList<>();
            for (CubeModel.Tex t : m.textures) {
                if (m.isFlat() && m.layers.isEmpty()) break;
                String th = "t_" + PackCompiler.hash(t.png);
                texIds.add(NS + ":item/" + th);
                files.put("assets/" + NS + "/textures/item/" + th + ".png", t.png);
                if (t.animated()) files.put("assets/" + NS + "/textures/item/" + th + ".png.mcmeta", t.mcmeta);
            }
            // 1.21.1 bakes cube models itself (free rotations); everything else goes through vanilla JSON
            if (modern || m.isFlat()) {
                JsonObject json = VanillaJson.write(m, texIds, modern, slots);
                files.put("assets/" + NS + "/models/item/" + key + ".json", J.COMPACT.toJson(json).getBytes(StandardCharsets.UTF_8));
            }
            if (modern) {
                String def = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + NS + ":item/" + key + "\"}}";
                files.put("assets/" + NS + "/items/" + key + ".json", def.getBytes(StandardCharsets.UTF_8));
            }
        }
        models = allModels;
        images = allImages;
        virtualFiles = files;
        wantedSignature = String.join(",", sig);
        table = new RuleTable(entries);
    }

    /** Applies pending changes; reloads game resources only when new item models/textures appeared. */
    public static void apply() {
        rebuild();
        config.save();
        platform.resetTextures();
        if (needsReload()) {
            reloading = true;
            hud(L.t("REDUX: загрузка моделей…", "REDUX: loading models…"), 60000);
            platform.reloadResources();
        } else {
            hud(statusLine(), 4000);
        }
    }

    public static boolean needsReload() {
        // only models that are not loaded yet force a reload; removed ones can stay until the next one
        Set<String> have = new HashSet<>(Arrays.asList(loadedSignature.split(",")));
        for (String k : wantedSignature.split(",")) if (!k.isEmpty() && !have.contains(k)) return true;
        return false;
    }

    /** Called by the version adapter when a resource reload has started with the current generated files. */
    public static void onResourcesCollected() { loadedSignature = wantedSignature; }

    public static void onReloadFinished() {
        if (reloading) {
            reloading = false;
            hud(statusLine(), 4000);
            platform.toast("REDUXion", L.t("Паки применены", "Packs applied"));
        }
    }

    public static String statusLine() {
        if (!config.enabled) return L.t("REDUX: выкл", "REDUX: off");
        int n = activeCount();
        if (L.ru) {
            int m10 = n % 10, m100 = n % 100;
            String w = m10 == 1 && m100 != 11 ? "пак" : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? "пака" : "паков";
            return "REDUX: " + n + " " + w;
        }
        return "REDUX: " + n + (n == 1 ? " pack" : " packs");
    }

    public static void hud(String msg, long millis) {
        hudMessage = msg;
        hudMessageUntil = System.currentTimeMillis() + millis;
    }

    public static Map<String, byte[]> virtualFiles() { return virtualFiles; }
    public static CubeModel model(String key) { return models.get(key); }
    public static Set<String> modelKeys() { return models.keySet(); }
    public static byte[] image(String key) { return images.get(key); }
    public static boolean active() { return config != null && config.enabled; }

    public static void toggleAll() {
        config.enabled = !config.enabled;
        config.save();
        hud(statusLine(), 4000);
    }

    public static void setEnabled(ReduxPack p, boolean on) {
        if (on) config.disabled.remove(p.manifest.id); else config.disabled.add(p.manifest.id);
        apply();
    }

    public static void move(ReduxPack p, int delta) {
        int i = packs.indexOf(p), j = i + delta;
        if (i < 0 || j < 0 || j >= packs.size()) return;
        Collections.swap(packs, i, j);
        config.order.clear();
        for (ReduxPack x : packs) config.order.add(x.manifest.id);
        apply();
    }

    public static ReduxPack find(String id) {
        for (ReduxPack p : packs) if (p.manifest.id.equals(id)) return p;
        return null;
    }

    /** Copies a pack file into the packs folder and loads it. Returns the loaded pack (possibly locked). */
    public static ReduxPack importFile(Path src) throws IOException {
        String name = src.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(PackIO.EXT_OPEN) && !lower.endsWith(PackIO.EXT_PROTECTED) && !lower.endsWith(".zip"))
            throw new IOException(L.t("Нужен файл .redux или .reduxp", "A .redux or .reduxp file is required"));
        ReduxPack probe = PackIO.load(src, null);
        String ext = probe.isProtected() ? PackIO.EXT_PROTECTED : PackIO.EXT_OPEN;
        ReduxPack old = find(probe.manifest.id);
        Path dst = old != null && old.file != null ? old.file : unique(PackIO.safeName(probe.manifest.id), ext);
        if (!dst.getFileName().toString().endsWith(ext)) {
            Files.deleteIfExists(dst);
            dst = unique(PackIO.safeName(probe.manifest.id), ext);
        }
        if (!src.toAbsolutePath().equals(dst.toAbsolutePath())) Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        ReduxPack pack = PackIO.load(dst, null);
        if (old != null) packs.set(packs.indexOf(old), pack); else packs.add(0, pack);
        config.order.remove(pack.manifest.id);
        config.order.add(Math.max(0, packs.indexOf(pack)), pack.manifest.id);
        return pack;
    }

    private static Path unique(String base, String ext) {
        Path p = packsDir().resolve(base + ext);
        for (int i = 2; Files.exists(p); i++) p = packsDir().resolve(base + "_" + i + ext);
        return p;
    }

    /** Tries a password on a locked pack. */
    public static boolean unlock(ReduxPack pack, String password, boolean remember) throws IOException {
        ReduxPack loaded = PackIO.load(pack.file, password.toCharArray());
        if (loaded.locked) return false;
        int i = packs.indexOf(pack);
        if (i >= 0) packs.set(i, loaded); else packs.add(loaded);
        if (remember) config.remember(loaded.manifest.id, password);
        return true;
    }

    public static ReduxPack create(String name, String author) throws IOException {
        ReduxPack p = new ReduxPack();
        p.manifest.name = name;
        p.manifest.author = author;
        String base = PackIO.safeName(name);
        String id = base;
        for (int i = 2; find(id) != null; i++) id = base + "_" + i;
        p.manifest.id = id;
        p.file = unique(id, PackIO.EXT_OPEN);
        PackIO.saveOpen(p, p.file);
        packs.add(0, p);
        config.order.add(0, id);
        return p;
    }

    public static void save(ReduxPack p) throws IOException {
        if (p.isProtected()) return;
        PackCompiler.compile(p);
        PackIO.saveOpen(p, p.file);
    }

    public static void delete(ReduxPack p) {
        try {
            if (p.file != null) Files.deleteIfExists(p.file);
        } catch (IOException ignored) {
        }
        packs.remove(p);
        config.order.remove(p.manifest.id);
        config.disabled.remove(p.manifest.id);
        config.passwords.remove(p.manifest.id);
        apply();
    }

    public static Path export(ReduxPack p, boolean protect, String password) throws IOException {
        Files.createDirectories(exportDir());
        String base = PackIO.safeName(p.manifest.id);
        if (p.isProtected()) {
            if (!protect) throw new IOException(L.t("Защищённый пак нельзя экспортировать открытым", "A protected pack cannot be exported as open"));
            Path out = exportDir().resolve(base + PackIO.EXT_PROTECTED);
            Files.copy(p.file, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        }
        if (protect) {
            Path out = exportDir().resolve(base + PackIO.EXT_PROTECTED);
            Protect.export(p, out, password == null || password.isEmpty() ? null : password.toCharArray());
            // exporting recompiled the pack; keep runtime state consistent
            return out;
        }
        Path out = exportDir().resolve(base + PackIO.EXT_OPEN);
        PackIO.saveOpen(p, out);
        return out;
    }
}
