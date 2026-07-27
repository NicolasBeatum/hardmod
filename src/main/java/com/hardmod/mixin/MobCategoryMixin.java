package com.hardmod.mixin;

import com.hardmod.feature.MobcapControl;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * getMaxInstancesPerChunk() alimenta tanto NaturalSpawner.SpawnState (cap
 * global por chunk) como LocalMobCapCalculator (cap por-jugador) -- un solo
 * hook aca escala ambos segun el multiplicador configurado por categoria.
 */
@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {

    @Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
    private void hardmod$scaleCap(CallbackInfoReturnable<Integer> cir) {
        MobCategory self = (MobCategory) (Object) this;
        cir.setReturnValue(MobcapControl.scale(self, cir.getReturnValue()));
    }
}
