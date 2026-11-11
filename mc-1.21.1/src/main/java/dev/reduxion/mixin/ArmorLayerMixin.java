package dev.reduxion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.reduxion.game.Hooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorLayerMixin {
    @WrapOperation(method = "renderArmorPiece", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ArmorMaterial$Layer;texture(Z)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation reduxion$armor(ArmorMaterial.Layer layer, boolean inner, Operation<ResourceLocation> original, @Local ItemStack stack) {
        ResourceLocation r = Hooks.armorTexture(stack, inner);
        return r != null ? r : original.call(layer, inner);
    }

    @Inject(method = "renderTrim", at = @At("HEAD"), cancellable = true)
    private void reduxion$trim(Holder<ArmorMaterial> material, PoseStack pose, MultiBufferSource buffers, int light, ArmorTrim trim,
                               HumanoidModel<?> model, boolean inner, CallbackInfo ci) {
        ResourceLocation r = Hooks.trimTexture(trim, inner);
        if (r != null) {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(r)), light, OverlayTexture.NO_OVERLAY);
            ci.cancel();
        }
    }
}
