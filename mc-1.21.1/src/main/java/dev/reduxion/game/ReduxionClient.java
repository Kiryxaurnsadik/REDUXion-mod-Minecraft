package dev.reduxion.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.reduxion.core.Platform;
import dev.reduxion.core.Redux;
import dev.reduxion.model.CubeModel;
import dev.reduxion.ui.Hud;
import dev.reduxion.ui.Screens;
import dev.reduxion.ui.UiScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.CustomModelData;

public final class ReduxionClient implements ClientModInitializer, Platform {
    private KeyMapping menuKey, toggleKey;
    private List<String> itemIds;

    @Override public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.reduxion.menu", InputConstants.Type.KEYSYM, 344, "key.categories.reduxion"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.reduxion.toggle", InputConstants.Type.KEYSYM, 297, "key.categories.reduxion"));
        Redux.init(this);
        ModelLoadingPlugin.register(ctx -> {
            Hooks.modelsReloaded();
            List<ResourceLocation> ids = new ArrayList<>();
            for (String key : Redux.modelKeys()) ids.add(ResourceLocation.fromNamespaceAndPath(Redux.NS, "item/" + key));
            ctx.addModels(ids);
            ctx.resolveModel().register(c -> {
                ResourceLocation id = c.id();
                if (!Redux.NS.equals(id.getNamespace()) || !id.getPath().startsWith("item/")) return null;
                CubeModel m = Redux.model(id.getPath().substring(5));
                return m != null && !m.isFlat() ? new ReduxUnbakedModel(m) : null;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) if (mc.screen == null) Screens.openMenu();
            while (toggleKey.consumeClick()) Redux.toggleAll();
        });
        HudRenderCallback.EVENT.register((gg, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || Redux.config == null) return;
            float s = Redux.config.hudScale;
            gg.pose().pushPose();
            gg.pose().scale(s, s, 1f);
            Hud.render(GfxImpl.of(gg), (int) (gg.guiWidth() / s), (int) (gg.guiHeight() / s));
            gg.pose().popPose();
        });
    }

    @Override public String gameVersion() { return "1.21.1"; }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir().resolve("reduxion"); }
    @Override public boolean modernItems() { return false; }
    @Override public void openScreen(UiScreen screen) { Minecraft.getInstance().setScreen(new HostScreen(screen)); }
    @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }
    @Override public boolean inWorld() { return Minecraft.getInstance().level != null; }

    @Override public void reloadResources() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
            Hooks.modelsReloaded();
            Redux.onReloadFinished();
        })));
    }

    @Override public void image(String key, int w, int h, int[] argb) { GfxImpl.upload(key, w, h, argb); }
    @Override public void dropImage(String key) { GfxImpl.drop(key); }
    @Override public void resetTextures() { Hooks.reset(); }

    @Override public Held held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return read(mc.player.getMainHandItem());
    }

    @Override public Held lookedAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        net.minecraft.world.entity.Entity e = Targeting.lookedAtEntity(mc);
        if (e == null) return null;
        return read(Targeting.itemOf(e));
    }

    private Held read(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        Held h = new Held();
        h.item = Hooks.id(st.getItem());
        CustomModelData cmd = st.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) h.cmd = cmd.value();
        h.armor = st.getItem() instanceof ArmorItem;
        ArmorTrim trim = st.get(DataComponents.TRIM);
        if (trim != null) {
            h.trimPattern = trim.pattern().unwrapKey().map(k -> k.location().toString()).orElse(null);
            h.trimMaterial = trim.material().unwrapKey().map(k -> k.location().toString()).orElse(null);
        }
        return h;
    }

    @Override public void toast(String title, String text) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(text));
    }

    @Override public void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean russian() {
        String l = Minecraft.getInstance().options.languageCode;
        return l != null && (l.startsWith("ru") || l.startsWith("uk") || l.startsWith("be"));
    }

    @Override public List<String> itemIds() {
        if (itemIds == null) {
            List<String> l = new ArrayList<>();
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) l.add(rl.toString());
            itemIds = l;
        }
        return itemIds;
    }

    @Override public String clipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    @Override public String keyName() { return toggleKey.getTranslatedKeyMessage().getString(); }

    @Override public String giveItem(dev.reduxion.pack.Rule rule) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return russian() ? "Нужно быть в мире" : "You must be in a world";
        if (mc.gameMode == null || !mc.gameMode.hasInfiniteItems()) return russian() ? "Нужен творческий режим" : "Creative mode is required";
        String id = dev.reduxion.pack.Rule.ITEM.equals(rule.target) || dev.reduxion.pack.Rule.ARMOR.equals(rule.target) ? rule.item : null;
        if (id == null || id.isEmpty()) return russian() ? "У этой замены нет предмета для выдачи" : "This replacement has no item to give";
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return (russian() ? "Нет такого предмета: " : "Unknown item: ") + id;
        ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        if (rule.cmd != null) st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(rule.cmd));
        int slot = 36 + mc.player.getInventory().selected;
        mc.gameMode.handleCreativeModeItemAdd(st, slot);
        return (russian() ? "Выдано: " : "Given: ") + id + (rule.cmd != null ? " (CMD " + rule.cmd + ")" : "");
    }
}
