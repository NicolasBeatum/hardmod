package com.hardmod.mixin;

import com.hardmod.config.HardModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Red de seguridad ademas de DimensionLock (que bloquea poner el ojo de
 * ender): cancela entityInside del todo mientras el End este sellado,
 * asi que si por algun motivo el portal ya esta activo nadie se
 * teletransporta -- el bloque queda completamente inerte.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void hardmod$blockTeleport(
        BlockState state, Level level, BlockPos pos, Entity entity,
        InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci
    ) {
        if (HardModConfig.INSTANCE.getEndLocked()) {
            ci.cancel();
        }
    }
}
