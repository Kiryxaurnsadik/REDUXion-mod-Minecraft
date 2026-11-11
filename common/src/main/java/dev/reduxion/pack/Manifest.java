package dev.reduxion.pack;

import com.google.gson.JsonObject;
import dev.reduxion.core.J;

public final class Manifest {
    public static final int FORMAT = 1;
    public String id = "";
    public String name = "REDUX pack";
    public String description = "";
    public String author = "";
    public String version = "1.0";
    public int format = FORMAT;
    public boolean isProtected;
    public boolean hasPassword;

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("author", author);
        o.addProperty("version", version);
        o.addProperty("format", format);
        if (isProtected) o.addProperty("protected", true);
        if (hasPassword) o.addProperty("password", true);
        return o;
    }

    public static Manifest fromJson(JsonObject o) {
        Manifest m = new Manifest();
        m.id = J.str(o, "id", "");
        m.name = J.str(o, "name", "REDUX pack");
        m.description = J.str(o, "description", "");
        m.author = J.str(o, "author", "");
        m.version = J.str(o, "version", "1.0");
        m.format = J.i(o, "format", FORMAT);
        m.isProtected = J.b(o, "protected", false);
        m.hasPassword = J.b(o, "password", false);
        return m;
    }
}
