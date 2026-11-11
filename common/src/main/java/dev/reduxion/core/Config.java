package dev.reduxion.core;

import com.google.gson.*;
import dev.reduxion.protect.Protect;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Config {
    public boolean enabled = true;
    public boolean items = true, armor = true, trims = true;
    public boolean hud = true;
    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right */
    public int hudCorner = 0;
    public float hudOpacity = 0.6f;
    public float hudScale = 1f;
    public boolean showNotice = true;
    public boolean confirmReload = true;
    /** pack ids in priority order (first wins) */
    public final List<String> order = new ArrayList<>();
    public final Set<String> disabled = new HashSet<>();
    /** pack id -> locally wrapped password */
    public final Map<String, String> passwords = new LinkedHashMap<>();

    private Path file;

    public static Config load(Path file) {
        Config c = new Config();
        c.file = file;
        try {
            if (Files.exists(file)) {
                JsonObject o = J.parse(Files.readAllBytes(file));
                c.enabled = J.b(o, "enabled", true);
                c.items = J.b(o, "items", true);
                c.armor = J.b(o, "armor", true);
                c.trims = J.b(o, "trims", true);
                c.hud = J.b(o, "hud", true);
                c.hudCorner = Math.max(0, Math.min(3, J.i(o, "hud_corner", 0)));
                c.hudOpacity = Math.max(0.1f, Math.min(1f, J.f(o, "hud_opacity", 0.6f)));
                c.hudScale = Math.max(0.5f, Math.min(2f, J.f(o, "hud_scale", 1f)));
                c.showNotice = J.b(o, "show_notice", true);
                c.confirmReload = J.b(o, "confirm_reload", true);
                JsonArray a = J.arr(o, "order");
                if (a != null) for (JsonElement e : a) c.order.add(e.getAsString());
                a = J.arr(o, "disabled");
                if (a != null) for (JsonElement e : a) c.disabled.add(e.getAsString());
                JsonObject p = J.obj(o, "passwords");
                if (p != null) for (Map.Entry<String, JsonElement> e : p.entrySet()) c.passwords.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    public void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", enabled);
            o.addProperty("items", items);
            o.addProperty("armor", armor);
            o.addProperty("trims", trims);
            o.addProperty("hud", hud);
            o.addProperty("hud_corner", hudCorner);
            o.addProperty("hud_opacity", hudOpacity);
            o.addProperty("hud_scale", hudScale);
            o.addProperty("show_notice", showNotice);
            o.addProperty("confirm_reload", confirmReload);
            JsonArray a = new JsonArray();
            order.forEach(a::add);
            o.add("order", a);
            JsonArray d = new JsonArray();
            disabled.forEach(d::add);
            o.add("disabled", d);
            JsonObject p = new JsonObject();
            passwords.forEach(p::addProperty);
            o.add("passwords", p);
            Files.createDirectories(file.getParent());
            Files.write(file, J.PRETTY.toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public void remember(String packId, String password) { passwords.put(packId, Protect.wrapLocal(password)); }
    public String recall(String packId) {
        String w = passwords.get(packId);
        return w == null ? null : Protect.unwrapLocal(w);
    }
}
