package dev.reduxion.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.reduxion.game.Hooks;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerMixin {
    private static final String RENDER = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;"
        + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private Object reduxion$armor(Function<Object, Object> lookup, Object key, Operation<Object> original,
                                  @Local(argsOnly = true) EquipmentClientInfo.LayerType type, @Local(argsOnly = true) ItemStack stack) {
        if (type == EquipmentClientInfo.LayerType.HUMANOID || type == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS) {
            Identifier r = Hooks.armorTexture(stack, type == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
            if (r != null) return r;
        }
        return original.call(lookup, key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object reduxion$trim(Object original, @Local(argsOnly = true) EquipmentClientInfo.LayerType type, @Local(argsOnly = true) Model model,
                                 @Local(argsOnly = true) Object state, @Local(argsOnly = true) PoseStack pose,
                                 @Local(argsOnly = true) SubmitNodeCollector collector,
                                 @Local(argsOnly = true, ordinal = 0) int light, @Local(argsOnly = true, ordinal = 1) int outline,
                                 @Local(argsOnly = true, ordinal = 2) int order) {
        if (!(original instanceof ArmorTrim trim)) return original;
        if (type != EquipmentClientInfo.LayerType.HUMANOID && type != EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS) return original;
        Identifier r = Hooks.trimTexture(trim, type == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
        if (r == null) return original;
        collector.order(order + 8).submitModel(model, state, pose, RenderTypes.armorCutoutNoCull(r), light, OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        return null;
    }
}
