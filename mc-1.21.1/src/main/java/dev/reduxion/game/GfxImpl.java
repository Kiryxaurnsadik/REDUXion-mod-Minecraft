package dev.reduxion.game;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.reduxion.core.Gfx;
import dev.reduxion.core.Redux;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class GfxImpl implements Gfx {
    static final class Img {
        ResourceLocation id;
        DynamicTexture tex;
        int w, h;
    }

    static final Map<String, Img> images = new HashMap<>();
    private static final Map<String, ItemStack> stacks = new HashMap<>();
    private static final GfxImpl INSTANCE = new GfxImpl();
    private GuiGraphics gg;
    private int clips;

    public static GfxImpl of(GuiGraphics gg) {
        INSTANCE.gg = gg;
        INSTANCE.clips = 0;
        return INSTANCE;
    }

    static void upload(String key, int w, int h, int[] argb) {
        Img img = images.get(key);
        if (img == null || img.w != w || img.h != h) {
            if (img != null) Minecraft.getInstance().getTextureManager().release(img.id);
            img = new Img();
            img.w = w;
            img.h = h;
            img.tex = new DynamicTexture(w, h, false);
            img.id = ResourceLocation.fromNamespaceAndPath(Redux.NS, "ui/" + Integer.toHexString(key.hashCode()) + "_" + w + "x" + h);
            Minecraft.getInstance().getTextureManager().register(img.id, img.tex);
            images.put(key, img);
        }
        NativeImage px = img.tex.getPixels();
        if (px == null) return;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                px.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        img.tex.upload();
    }

    static void drop(String key) {
        Img img = images.remove(key);
        if (img != null) Minecraft.getInstance().getTextureManager().release(img.id);
    }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { gg.fill(x1, y1, x2, y2, argb); }

    @Override public void text(String s, int x, int y, int argb, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        gg.drawString(Minecraft.getInstance().font, s, x, y, argb, shadow);
    }

    @Override public int textWidth(String s) { return s == null ? 0 : Minecraft.getInstance().font.width(s); }

    @Override public void image(String key, int x, int y, int w, int h) {
        Img img = images.get(key);
        if (img == null) return;
        RenderSystem.enableBlend();
        gg.blit(img.id, x, y, w, h, 0f, 0f, img.w, img.h, img.w, img.h);
    }

    @Override public void item(String itemId, int x, int y) {
        ItemStack st = stacks.get(itemId);
        if (st == null) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            st = rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? new ItemStack(BuiltInRegistries.ITEM.get(rl)) : ItemStack.EMPTY;
            stacks.put(itemId, st);
        }
        if (!st.isEmpty()) gg.renderFakeItem(st, x, y);
    }

    @Override public void clip(int x1, int y1, int x2, int y2) { gg.enableScissor(x1, y1, x2, y2); clips++; }
    @Override public void unclip() { if (clips > 0) { gg.disableScissor(); clips--; } }
    @Override public void unclipAll() { while (clips > 0) unclip(); }
}
