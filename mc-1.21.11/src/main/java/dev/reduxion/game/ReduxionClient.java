package dev.reduxion.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.reduxion.core.Platform;
import dev.reduxion.core.Redux;
import dev.reduxion.ui.Hud;
import dev.reduxion.ui.Screens;
import dev.reduxion.ui.UiScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

public final class ReduxionClient implements ClientModInitializer, Platform {
    private KeyMapping menuKey, toggleKey;
    private List<String> itemIds;

    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Redux.NS, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.reduxion.menu", InputConstants.Type.KEYSYM, 344, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.reduxion.toggle", InputConstants.Type.KEYSYM, 297, category));
        Redux.init(this);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) if (mc.screen == null) Screens.openMenu();
            while (toggleKey.consumeClick()) Redux.toggleAll();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Redux.NS, "hud"), (gg, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || Redux.config == null) return;
            float s = Redux.config.hudScale;
            gg.pose().pushMatrix();
            gg.pose().scale(s, s);
            Hud.render(GfxImpl.of(gg), (int) (gg.guiWidth() / s), (int) (gg.guiHeight() / s));
            gg.pose().popMatrix();
        });
    }

    @Override public String gameVersion() { return "1.21.11"; }
    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir().resolve("reduxion"); }
    @Override public boolean modernItems() { return true; }
    @Override public void openScreen(UiScreen screen) { Minecraft.getInstance().setScreen(new HostScreen(screen)); }
    @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }
    @Override public boolean inWorld() { return Minecraft.getInstance().level != null; }

    @Override public void reloadResources() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
            Hooks.reset();
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
        ItemStack st = Targeting.itemOf(e);
        return st == null ? null : read(st);
    }

    private Held read(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        Held h = new Held();
        h.item = Hooks.id(st.getItem());
        Hooks.StackProbe probe = new Hooks.StackProbe(st);
        h.cmd = probe.cmd();
        h.cmdStrings = new ArrayList<>(probe.cmdStrings());
        h.cmdFlags = new ArrayList<>(probe.cmdFlags());
        String model = probe.itemModel();
        // only keep an item_model that differs from the item's own id: that is what marks a custom item
        if (model != null && !model.equals(h.item)) h.itemModel = model;
        Equippable eq = st.get(DataComponents.EQUIPPABLE);
        h.armor = eq != null && eq.slot().isArmor();
        String asset = probe.assetId();
        if (asset != null && !asset.startsWith("minecraft:")) h.assetId = asset;
        ArmorTrim trim = st.get(DataComponents.TRIM);
        if (trim != null) {
            h.trimPattern = trim.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse(null);
            h.trimMaterial = trim.material().unwrapKey().map(k -> k.identifier().toString()).orElse(null);
        }
        return h;
    }

    @Override public void toast(String title, String text) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(text));
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
            for (Identifier rl : BuiltInRegistries.ITEM.keySet()) l.add(rl.toString());
            itemIds = l;
        }
        return itemIds;
    }

    @Override public String clipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    @Override public String keyName() { return toggleKey.getTranslatedKeyMessage().getString(); }

    @Override public String giveItem(dev.reduxion.pack.Rule rule) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return russian() ? "Нужно быть в мире" : "You must be in a world";
        if (mc.gameMode == null || !mc.player.hasInfiniteMaterials()) return russian() ? "Нужен творческий режим" : "Creative mode is required";
        String id = dev.reduxion.pack.Rule.ITEM.equals(rule.target) || dev.reduxion.pack.Rule.ARMOR.equals(rule.target) ? rule.item : null;
        if (id == null || id.isEmpty()) return russian() ? "У этой замены нет предмета для выдачи" : "This replacement has no item to give";
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return (russian() ? "Нет такого предмета: " : "Unknown item: ") + id;
        ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(rl));
        if (rule.cmd != null || !rule.cmdStrings.isEmpty()) {
            java.util.List<Float> floats = rule.cmd != null ? java.util.List.of((float) (int) rule.cmd) : java.util.List.of();
            st.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                floats, java.util.List.of(), rule.cmdStrings, java.util.List.of()));
        }
        if (rule.itemModel != null && !rule.itemModel.isEmpty()) {
            Identifier model = Identifier.tryParse(rule.itemModel);
            if (model != null) st.set(DataComponents.ITEM_MODEL, model);
        }
        int slot = 36 + mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleCreativeModeItemAdd(st, slot);
        String how = rule.itemModel != null && !rule.itemModel.isEmpty() ? " (item_model)" : rule.cmd != null ? " (CMD " + rule.cmd + ")" : "";
        return (russian() ? "Выдано: " : "Given: ") + id + how;
    }
}
