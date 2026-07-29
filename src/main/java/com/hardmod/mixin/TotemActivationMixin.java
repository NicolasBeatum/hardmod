package com.hardmod.mixin;

import com.hardmod.feature.TotemControl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aplica la probabilidad configurada antes de que vanilla busque y consuma un totem. */
@Mixin(LivingEntity.class)
public abstract class TotemActivationMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void hardmod$rollTotemActivation(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!TotemControl.shouldActivate()) {
            cir.setReturnValue(false);
        }
    }
}
