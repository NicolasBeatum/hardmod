package com.hardmod.mixin;

import com.hardmod.feature.VillagerSpawnControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * addEntity (privado) es el punto de entrada COMUN al que delegan
 * addFreshEntity (entidades nuevas, ej. cria), addWithUUID (entidades
 * cargadas con un UUID ya asignado -- el caso de los aldeanos que vienen
 * incrustados en el NBT de una estructura de aldea generada) y
 * addDuringTeleport para no-jugadores. Enganchar solo addFreshEntity (como
 * hacia la version anterior) dejaba pasar los aldeanos de aldeas generadas
 * porque esos entran por addWithUUID, no por addFreshEntity -- este mixin
 * cubre los tres caminos de una sola vez.
 */
@Mixin(ServerLevel.class)
public abstract class VillagerSpawnMixin {

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void hardmod$blockVillager(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (VillagerSpawnControl.shouldBlock(entity)) {
            entity.discard();
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
