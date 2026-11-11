package dev.reduxion.pack;

import dev.reduxion.model.CubeModel;
import java.nio.file.Path;
import java.util.*;

/** A REDUX pack held in memory. Open packs keep their source files, protected packs only compiled data. */
public final class ReduxPack {
    public Manifest manifest = new Manifest();
    public byte[] icon;
    public final List<Rule> rules = new ArrayList<>();
    /** Open packs: path inside the pack -> bytes (models/..., textures/...). */
    public final Map<String, byte[]> files = new TreeMap<>();
    /** Compiled data, filled by {@link PackCompiler} (open) or {@link Protect} (protected). */
    public final Map<String, CubeModel> models = new HashMap<>();
    public final Map<String, byte[]> images = new HashMap<>();
    /** Where the pack is stored on disk. */
    public Path file;
    public boolean locked;
    public String error;
    public final List<String> warnings = new ArrayList<>();

    public boolean isProtected() { return manifest.isProtected; }

    public int ruleCount() { return rules.size(); }
}
