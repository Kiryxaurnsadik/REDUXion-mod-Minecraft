package dev.reduxion.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.reduxion.core.Config;
import dev.reduxion.core.Redux;
import dev.reduxion.rules.RuleTable;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.CustomModelData;

/** Called from the mixins. Everything here runs on the render thread and must be cheap. */
public final class Hooks {
    private Hooks() {}

    private static final Map<String, ResourceLocation> textures = new HashMap<>();
    private static final Map<String, BakedModel> modelCache = new HashMap<>();
    private static final ResourceLocation NO_TEXTURE = ResourceLocation.fromNamespaceAndPath(Redux.NS, "none");

    static final class StackProbe implements RuleTable.Probe {
        private final ItemStack stack;
        StackProbe(ItemStack stack) { this.stack = stack; }
        @Override public Integer cmd() {
            CustomModelData d = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            return d == null ? null : d.value();
        }
        @Override public List<String> cmdStrings() { return List.of(); }
        @Override public List<Boolean> cmdFlags() { return List.of(); }
        @Override public String itemModel() { return null; }
        @Override public String assetId() { return null; }
    }

    public static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            modelCache.clear();
            for (ResourceLocation rl : textures.values()) if (rl != NO_TEXTURE) mc.getTextureManager().release(rl);
            textures.clear();
        });
    }

    public static void modelsReloaded() { modelCache.clear(); }

    public static BakedModel itemModel(ItemStack stack) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.items || stack.isEmpty()) return null;
        RuleTable table = Redux.table;
        if (!table.hasItems()) return null;
        Item item = stack.getItem();
        RuleTable.Entry e = table.matchItem(item, () -> id(item), new StackProbe(stack));
        if (e == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (e.borrowItem != null) {
            ResourceLocation rl = ResourceLocation.tryParse(e.borrowItem);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
            return mc.getItemRenderer().getItemModelShaper().getItemModel(BuiltInRegistries.ITEM.get(rl));
        }
        String key = e.rule.modelKey;
        if (key == null) return null;
        BakedModel m = modelCache.get(key);
        if (m == null) {
            if (modelCache.containsKey(key)) return null;
            BakedModel found = mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath(Redux.NS, "item/" + key));
            if (found == mc.getModelManager().getMissingModel()) found = null;
            modelCache.put(key, found);
            m = found;
        }
        return m;
    }

    private static ResourceLocation texture(String key) {
        if (key == null) return null;
        ResourceLocation rl = textures.get(key);
        if (rl == null) {
            rl = NO_TEXTURE;
            byte[] png = Redux.image(key);
            if (png != null) {
                try {
                    NativeImage img = NativeImage.read(png);
                    rl = ResourceLocation.fromNamespaceAndPath(Redux.NS, "dyn/" + key);
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                } catch (Exception ex) {
                    rl = NO_TEXTURE;
                }
            }
            textures.put(key, rl);
        }
        return rl == NO_TEXTURE ? null : rl;
    }

    /** @param inner true for the leggings layer */
    public static ResourceLocation armorTexture(ItemStack stack, boolean inner) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.armor) return null;
        RuleTable table = Redux.table;
        if (!table.hasArmor()) return null;
        Item item = stack.getItem();
        RuleTable.Entry e = table.matchArmor(item, () -> id(item), new StackProbe(stack));
        if (e == null) return null;
        return texture(inner ? e.rule.layer2Key : e.rule.layer1Key);
    }

    public static ResourceLocation trimTexture(ArmorTrim trim, boolean inner) {
        Config c = Redux.config;
        if (c == null || !c.enabled || !c.trims) return null;
        RuleTable table = Redux.table;
        if (!table.hasTrims()) return null;
        String pattern = trim.pattern().unwrapKey().map(k -> k.location().toString()).orElse("");
        String material = trim.material().unwrapKey().map(k -> k.location().toString()).orElse("");
        RuleTable.Entry e = table.matchTrim(pattern, material);
        if (e == null) return null;
        String key = inner ? (e.rule.layer2Key != null ? e.rule.layer2Key : null) : e.rule.layer1Key;
        return texture(key);
    }
}
