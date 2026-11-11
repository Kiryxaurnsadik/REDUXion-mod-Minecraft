package dev.reduxion.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.reduxion.game.Hooks;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Swaps the item model definition right where the game reads the item_model component. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object reduxion$model(Object original, @Local(argsOnly = true) ItemStack stack) {
        Identifier id = Hooks.itemModel(stack);
        return id != null ? id : original;
    }
}
