package dev.reduxion.pack;

import com.google.gson.*;
import dev.reduxion.core.J;
import dev.reduxion.protect.Protect;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Reads and writes open ".redux" packs (plain zip archives) and dispatches protected ones to {@link Protect}. */
public final class PackIO {
    private PackIO() {}
    public static final String EXT_OPEN = ".redux", EXT_PROTECTED = ".reduxp";
    private static final long MAX_ENTRY = 64L << 20, MAX_TOTAL = 256L << 20;

    public static Map<String, byte[]> readZip(Path file) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                if (name.contains("..") || name.startsWith("/")) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int n;
                long size = 0;
                while ((n = zin.read(buf)) > 0) {
                    size += n; total += n;
                    if (size > MAX_ENTRY || total > MAX_TOTAL) throw new IOException("pack is too large");
                    bo.write(buf, 0, n);
                }
                out.put(name, bo.toByteArray());
            }
        }
        // tolerate archives that wrap everything in a single top-level folder
        if (!out.containsKey("redux.json")) {
            String prefix = null;
            for (String k : out.keySet()) if (k.endsWith("/redux.json")) { prefix = k.substring(0, k.length() - "redux.json".length()); break; }
            if (prefix != null) {
                Map<String, byte[]> fixed = new LinkedHashMap<>();
                for (Map.Entry<String, byte[]> en : out.entrySet())
                    if (en.getKey().startsWith(prefix)) fixed.put(en.getKey().substring(prefix.length()), en.getValue());
                return fixed;
            }
        }
        return out;
    }

    public static void writeZip(Path file, Map<String, byte[]> entries) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(315532800000L);
                zout.putNextEntry(ze);
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Loads a pack file. Protected packs with a password stay locked until {@link Protect#unlock} succeeds. */
    public static ReduxPack load(Path file, char[] password) throws IOException {
        Map<String, byte[]> z = readZip(file);
        byte[] mf = z.get("redux.json");
        if (mf == null) throw new IOException("redux.json not found");
        JsonObject mo = J.parse(mf);
        ReduxPack pack = new ReduxPack();
        pack.manifest = Manifest.fromJson(mo);
        pack.icon = z.get("icon.png");
        pack.file = file;
        if (pack.manifest.id.isEmpty()) pack.manifest.id = stripExt(file.getFileName().toString());
        if (pack.manifest.isProtected) {
            pack.locked = true;
            if (!pack.manifest.hasPassword || password != null) {
                try {
                    Protect.unlock(pack, z, mo, password);
                } catch (Protect.WrongPassword e) {
                    pack.locked = true;
                }
            }
            return pack;
        }
        byte[] rules = z.get("rules.json");
        if (rules != null) {
            JsonElement re = JsonParser.parseString(new String(rules, java.nio.charset.StandardCharsets.UTF_8));
            JsonArray arr = re.isJsonArray() ? re.getAsJsonArray() : J.arr(re.getAsJsonObject(), "rules");
            if (arr != null) for (JsonElement e : arr) {
                Rule r = Rule.fromJson(e.getAsJsonObject());
                if (r.id.isEmpty()) r.id = "rule_" + (pack.rules.size() + 1);
                pack.rules.add(r);
            }
        }
        for (Map.Entry<String, byte[]> e : z.entrySet()) {
            String k = e.getKey();
            if (k.equals("redux.json") || k.equals("rules.json") || k.equals("icon.png")) continue;
            pack.files.put(k, e.getValue());
        }
        PackCompiler.compile(pack);
        return pack;
    }

    public static void saveOpen(ReduxPack pack, Path file) throws IOException {
        Map<String, byte[]> z = new LinkedHashMap<>();
        Manifest m = pack.manifest;
        boolean p = m.isProtected, h = m.hasPassword;
        m.isProtected = false; m.hasPassword = false;
        z.put("redux.json", J.bytes(m.toJson()));
        m.isProtected = p; m.hasPassword = h;
        if (pack.icon != null) z.put("icon.png", pack.icon);
        JsonArray arr = new JsonArray();
        for (Rule r : pack.rules) arr.add(r.toJson(false));
        z.put("rules.json", J.bytes(arr));
        z.putAll(pack.files);
        writeZip(file, z);
    }

    public static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    public static String safeName(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("^_+|_+$", "");
        return r.isEmpty() ? "pack" : r.length() > 40 ? r.substring(0, 40) : r;
    }
}
