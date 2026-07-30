package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Escala el cap de spawn por categoria (ver MobCategoryMixin, que engancha
 * MobCategory.getMaxInstancesPerChunk -- ese numero alimenta tanto el cap
 * global como el cap por-jugador via LocalMobCapCalculator, asi que un solo
 * hook sube ambos).
 */
object MobcapControl {

    @JvmStatic
    fun scale(category: MobCategory, base: Int): Int {
        val multiplier = HardModConfig.mobcapMultiplierFor(category.serializedName)
        if (multiplier == 1.0) return base
        return max(1, (base * multiplier).roundToInt())
    }

    /**
     * Cuenta las entidades cargadas con el mismo criterio base que usa
     * NaturalSpawner para poblar la mobcap: ignora MISC y mobs persistentes.
     */
    fun loadedSnapshot(server: MinecraftServer): Map<MobCategory, Map<String, Int>> {
        val counts = mutableMapOf<MobCategory, MutableMap<String, Int>>()
        for (level in server.allLevels) {
            for (entity in level.allEntities) {
                val mob = entity as? Mob
                if (mob != null && (mob.isPersistenceRequired || mob.requiresCustomPersistence())) continue

                val category = entity.type.category
                if (category == MobCategory.MISC) continue

                val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
                val byType = counts.getOrPut(category) { mutableMapOf() }
                byType[typeId] = (byType[typeId] ?: 0) + 1
            }
        }
        return counts
    }
}
