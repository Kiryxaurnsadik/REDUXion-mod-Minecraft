package dev.reduxion.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.reduxion.core.Config;
import dev.reduxion.core.Redux;
import dev.reduxion.rules.RuleTable;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/** Called from the mixins. Everything here runs on the render thread and must be cheap. */
public final class Hooks {
    private Hooks() {}

    private static final Map<String, Identifier> textures = new HashMap<>();
    private static final Map<String, Identifier> modelIds = new HashMap<>();
    private static final Identifier NO_TEXTURE = Identifier.fromNamespaceAndPath(Redux.NS, "none");

    public static final class StackProbe implements RuleTable.Probe {
        private final ItemStack stack;
        private CustomModelData data;
        private boolean read;
        public StackProbe(ItemStack stack) { this.stack = stack; }
        private CustomModelData data() {
            if (!read) { read = true; data = stack.get(DataComponents.CUSTOM_MODEL_DATA); }
            return data;
        }
        @Override public Integer cmd() {
            CustomModelData d = data();
            if (d == null || d.floats().isEmpty()) return null;
            Float f = d.floats().get(0);
            return f == null ? null : Math.round(f);
        }
        @Override public List<String> cmdStrings() { CustomModelData d = data(); return d == null ? List.of() : d.strings(); }
        @Override public List<Boolean> cmdFlags() { CustomModelData d = data(); return d == null ? List.of() : d.flags(); }
        @Override public String itemModel() {
            Identifier id = stack.get(DataComponents.ITEM_MODEL);
            return id == null ? null : id.toString();
        }
        @Override public String assetId() {
            Equippable e = stack.get(DataComponents.EQUIPPABLE);
            return e == null ? null : e.assetId().map(k -> k.identifier().toString()).orElse(null);
        }
    }

    public static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            modelIds.clear();
            for (Identifier rl : textures.values()) if (rl != NO_TEXTURE) mc.getTextureManager().release(rl);
            textures.clear();
        });
    }

    /** Returns the item model definition id to use instead of the stack's own, or null. */
    public static Identifier itemModel(ItemStack stack) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.items || stack.isEmpty()) return null;
        RuleTable table = Redux.table;
        if (!table.hasItems()) return null;
        Item item = stack.getItem();
        RuleTable.Entry e = table.matchItem(item, () -> id(item), new StackProbe(stack));
        if (e == null) return null;
        String ref = e.borrowItem != null ? e.borrowItem : e.rule.modelKey == null ? null : Redux.NS + ":" + e.rule.modelKey;
        if (ref == null) return null;
        Identifier id = modelIds.get(ref);
        if (id == null) {
            id = Identifier.tryParse(ref);
            if (id == null) id = NO_TEXTURE;
            modelIds.put(ref, id);
        }
        return id == NO_TEXTURE ? null : id;
    }

    private static Identifier texture(String key) {
        if (key == null) return null;
        Identifier rl = textures.get(key);
        if (rl == null) {
            rl = NO_TEXTURE;
            byte[] png = Redux.image(key);
            if (png != null) {
                try {
                    NativeImage img = NativeImage.read(png);
                    rl = Identifier.fromNamespaceAndPath(Redux.NS, "dyn/" + key);
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(() -> "reduxion " + key, img));
                } catch (Exception ex) {
                    rl = NO_TEXTURE;
                }
            }
            textures.put(key, rl);
        }
        return rl == NO_TEXTURE ? null : rl;
    }

    /** @param inner true for the leggings layer */
    public static Identifier armorTexture(ItemStack stack, boolean inner) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.armor || stack.isEmpty()) return null;
        RuleTable table = Redux.table;
        if (!table.hasArmor()) return null;
        Item item = stack.getItem();
        RuleTable.Entry e = table.matchArmor(item, () -> id(item), new StackProbe(stack));
        if (e == null) return null;
        return texture(inner ? e.rule.layer2Key : e.rule.layer1Key);
    }

    public static Identifier trimTexture(ArmorTrim trim, boolean inner) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.trims) return null;
        RuleTable table = Redux.table;
        if (!table.hasTrims()) return null;
        String pattern = trim.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse("");
        String material = trim.material().unwrapKey().map(k -> k.identifier().toString()).orElse("");
        RuleTable.Entry e = table.matchTrim(pattern, material);
        if (e == null) return null;
        return texture(inner ? e.rule.layer2Key : e.rule.layer1Key);
    }
}
