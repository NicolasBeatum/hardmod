package com.hardmod.feature

import com.hardmod.config.HardModConfig
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
}
