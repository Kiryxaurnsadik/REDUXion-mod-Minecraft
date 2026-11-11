package dev.reduxion.mixin;

import dev.reduxion.core.Redux;
import dev.reduxion.game.VirtualPack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Appends the generated REDUX resources on top of whatever packs the game is about to load. */
@Mixin(ReloadableResourceManager.class)
public abstract class ResourceManagerMixin {
    @Shadow @Final private PackType type;

    @ModifyVariable(method = "createReload", at = @At("HEAD"), argsOnly = true)
    private List<PackResources> reduxion$addPack(List<PackResources> packs) {
        if (this.type != PackType.CLIENT_RESOURCES || Redux.platform == null) return packs;
        List<PackResources> out = new ArrayList<>(packs);
        out.add(new VirtualPack(Redux.virtualFiles()));
        Redux.onResourcesCollected();
        return out;
    }
}
