package com.hardmod.mixin;

import com.hardmod.config.HardModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Red de seguridad ademas de DimensionLock (que bloquea encender el
 * portal): cancela entityInside del todo mientras el Nether este
 * sellado, asi que si por algun motivo llega a existir un portal
 * (ej. un ruined portal generado con el portal ya prendido de fabrica,
 * o cualquier otro camino que DimensionLock no cubra) nadie se
 * teletransporta -- el bloque queda completamente inerte.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void hardmod$blockTeleport(
        BlockState state, Level level, BlockPos pos, Entity entity,
        InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci
    ) {
        if (HardModConfig.INSTANCE.getNetherLocked()) {
            ci.cancel();
        }
    }
}
