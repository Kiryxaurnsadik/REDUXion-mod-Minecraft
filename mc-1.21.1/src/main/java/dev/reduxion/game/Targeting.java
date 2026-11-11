package dev.reduxion.game;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Finds the item on whatever entity the player is looking at. Client-only, reads nothing but the client's own world. */
final class Targeting {
    private Targeting() {}

    /** How far the crosshair reaches for this, in blocks. Blocks in the way still cut the ray short. */
    private static final double REACH = 48.0;

    static Entity lookedAtEntity(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;
        Predicate<Entity> filter = e -> e != cam && !e.isSpectator() && itemOf(e) != null;
        HitResult hr = ProjectileUtil.getHitResultOnViewVector(cam, filter, REACH);
        return hr instanceof EntityHitResult ehr ? ehr.getEntity() : null;
    }

    /** The item an entity is showing: a frame's item, an item display's item, or a living entity's hand/head item. */
    static ItemStack itemOf(Entity e) {
        ItemStack st = null;
        if (e instanceof ItemFrame f) {
            st = f.getItem();
        } else if (e instanceof Display.ItemDisplay d) {
            st = d.getSlot(0).get();
        } else if (e instanceof LivingEntity le) {
            st = le.getMainHandItem();
            if (st == null || st.isEmpty()) st = le.getOffhandItem();
            if (st == null || st.isEmpty()) st = le.getItemBySlot(EquipmentSlot.HEAD);
        }
        return st == null || st.isEmpty() ? null : st;
    }
}
