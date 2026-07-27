package com.hardmod.mixin;

import com.hardmod.feature.VillagerSpawnControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

/**
 * Los aldeanos que vienen con una aldea GENERADA no pasan por
 * ServerLevel.addFreshEntity/addWithUUID/addEntity (lo que cubre
 * VillagerSpawnMixin) -- entran por este otro camino, especifico de
 * entidades creadas durante la generacion del mundo/chunk, que
 * PersistentEntitySectionManager expone aparte (addWorldGenChunkEntities)
 * precisamente para no pasar por el camino runtime normal. Filtramos el
 * stream antes de que se agreguen, mismo patron que
 * EnchantmentTablePoolMixin (@ModifyVariable sobre un Stream).
 *
 * Usa shouldBlockWorldGen (NO shouldBlock): por este camino no pasan
 * crias de reproduccion real ni curas de zombie, asi que no corresponde
 * la excepcion de bebe -- las aldeas generadas pueblan aldeanos bebes Y
 * adultos por igual, y hay que sacar los dos.
 */
@Mixin(ServerLevel.class)
public abstract class VillagerWorldGenSpawnMixin {

    @ModifyVariable(method = "addWorldGenChunkEntities", at = @At("HEAD"), argsOnly = true)
    private Stream<Entity> hardmod$filterVillagers(Stream<Entity> entities) {
        return entities.filter(entity -> !VillagerSpawnControl.shouldBlockWorldGen(entity));
    }
}
