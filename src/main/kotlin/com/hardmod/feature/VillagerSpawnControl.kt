package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.npc.villager.Villager

/**
 * Filtro de aldeanos -- dos predicados distintos para dos caminos
 * distintos, porque las excepciones que aplican a uno NO aplican al otro:
 *
 * [shouldBlock] -- para ServerLevel.addEntity (VillagerSpawnMixin), el
 * camino que usan la reproduccion y la cura de zombie aldeano en runtime.
 * Bloquea Villager ADULTO, con dos excepciones:
 *   1. Bebes (crias de VillagerMakeLove) -- una cria de reproduccion
 *      siempre nace bebe (Entity.isBaby), asi que ese flag la distingue
 *      de un aldeano normal. Una vez que crece no vuelve a pasar por
 *      addEntity (es la MISMA entidad, no una nueva), asi que nunca la
 *      bloquea despues.
 *   2. Zombie aldeano curado -- Mob.convertTo tambien agrega el Villager
 *      resultante como adulto por este mismo camino, asi que isBaby no lo
 *      distingue de uno de aldea. [curingInProgress] (ver
 *      MobConversionMixin) es lo que permite dejarlo pasar.
 *
 * [shouldBlockWorldGen] -- para ServerLevel.addWorldGenChunkEntities
 * (VillagerWorldGenSpawnMixin), el camino especifico de entidades que
 * vienen con la GENERACION de una estructura/chunk (aldeas). Por este
 * camino NUNCA pasan crias de reproduccion real ni curas de zombie --
 * son directamente parte de como vanilla puebla la aldea al generarla,
 * asi que bloquea CUALQUIER Villager, sea bebe o adulto, sin excepcion.
 *
 * Ninguno de los dos afecta a WanderingTrader (no es un Villager).
 */
object VillagerSpawnControl {

    @Volatile
    private var curingInProgress = false

    @JvmStatic
    fun setCuringInProgress(value: Boolean) {
        curingInProgress = value
    }

    @JvmStatic
    fun shouldBlock(entity: Entity): Boolean =
        HardModConfig.blockVillagers && entity is Villager && !entity.isBaby && !curingInProgress

    @JvmStatic
    fun shouldBlockWorldGen(entity: Entity): Boolean =
        HardModConfig.blockVillagers && entity is Villager
}
