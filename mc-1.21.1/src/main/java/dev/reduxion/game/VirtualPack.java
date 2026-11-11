package dev.reduxion.game;

import dev.reduxion.core.Redux;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/** In-memory resources generated from the installed REDUX packs. Never listed in the pack screen. */
public final class VirtualPack implements PackResources {
    private final Map<String, byte[]> files;
    private final PackLocationInfo info = new PackLocationInfo("reduxion_generated", Component.literal("REDUXion"), PackSource.BUILT_IN, Optional.empty());

    public VirtualPack(Map<String, byte[]> files) { this.files = files; }

    @Override public IoSupplier<InputStream> getRootResource(String... path) { return null; }

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        byte[] b = files.get("assets/" + id.getNamespace() + "/" + id.getPath());
        return b == null ? null : () -> new ByteArrayInputStream(b);
    }

    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES || !Redux.NS.equals(namespace)) return;
        String prefix = "assets/" + namespace + "/";
        String dir = prefix + path + "/";
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith(dir)) continue;
            ResourceLocation id = ResourceLocation.tryBuild(namespace, e.getKey().substring(prefix.length()));
            byte[] b = e.getValue();
            if (id != null) out.accept(id, () -> new ByteArrayInputStream(b));
        }
    }

    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(Redux.NS) : Set.of();
    }

    @Override public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) { return null; }
    @Override public PackLocationInfo location() { return info; }
    @Override public void close() {}
}
