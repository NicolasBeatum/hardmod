package com.hardmod.mixin;

import com.hardmod.feature.VillagerSpawnControl;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob.convertTo(...) es lo que usa ZombieVillager.finishConversion (curar
 * un zombie aldeano con debilidad + manzana dorada) para crear el Villager
 * adulto resultante -- y ese Villager se agrega al mundo con
 * ServerLevel.addFreshEntity ANTES de que el metodo termine, o sea que
 * llega al mismo VillagerSpawnMixin/addEntity que un aldeano de aldea
 * generada, con el mismo isBaby=false. Para distinguir "cura" de
 * "generacion" marcamos una bandera mientras dura este convertTo puntual
 * (HEAD la prende si el que se esta convirtiendo es un ZombieVillager
 * hacia Villager, RETURN la apaga) -- VillagerSpawnControl.shouldBlock la
 * consulta para dejar pasar la cura aunque los aldeanos esten bloqueados.
 */
@Mixin(Mob.class)
public abstract class MobConversionMixin {

    private static final String CONVERT_TO_DESC =
        "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;"
            + "Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)"
            + "Lnet/minecraft/world/entity/Mob;";

    @Inject(method = CONVERT_TO_DESC, at = @At("HEAD"))
    private void hardmod$markZombieVillagerCure(
        EntityType<?> type, ConversionParams params, EntitySpawnReason reason,
        ConversionParams.AfterConversion<?> afterConversion, CallbackInfoReturnable<Mob> cir
    ) {
        Mob self = (Mob) (Object) this;
        VillagerSpawnControl.setCuringInProgress(self instanceof ZombieVillager && type == EntityTypes.VILLAGER);
    }

    @Inject(method = CONVERT_TO_DESC, at = @At("RETURN"))
    private void hardmod$clearZombieVillagerCure(
        EntityType<?> type, ConversionParams params, EntitySpawnReason reason,
        ConversionParams.AfterConversion<?> afterConversion, CallbackInfoReturnable<Mob> cir
    ) {
        VillagerSpawnControl.setCuringInProgress(false);
    }
}
