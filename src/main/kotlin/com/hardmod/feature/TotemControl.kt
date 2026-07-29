package com.hardmod.feature

import com.hardmod.config.HardModConfig
import kotlin.random.Random

/** Decide server-side si un intento de activacion de totem tiene exito. */
object TotemControl {

    @JvmStatic
    fun shouldActivate(): Boolean {
        val chance = HardModConfig.totemActivationChance
        return chance >= 100 || chance > 0 && Random.nextInt(100) < chance
    }
}
