package com.hardmod.feature

import com.hardmod.announce.Announcer
import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.EnchantingTableBlock
import kotlin.random.Random

/**
 * Bloqueo (temporal u indefinido) de la mesa de encantamientos: bloqueada
 * por defecto (ver HardModConfig). Con duracion, revierte sola via el
 * mismo patron de chequeo por tick que MandatoryMissionService.
 */
object EnchantTableLock {

    private const val CHECK_INTERVAL_TICKS = 20
    private var ticksSinceCheck = 0

    /** Excusas tematicas que se le muestran al jugador (action bar) cuando intenta usar la mesa bloqueada -- una al azar por intento, para que no se sienta repetitivo. */
    private val LOCKED_EXCUSES = listOf(
        "&8[&4✦&8] &7Una fuerza arácnida no te deja usar la mesa...",
        "&8[&4✦&8] &7Algo en las sombras bloquea el encantamiento.",
        "&8[&4✦&8] &7La mesa esta sellada por ahora.",
        "&8[&4✦&8] &7Una presencia hostil impide que enchantes aca."
    )

    fun register() {
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, _, hitResult ->
            if (!level.isClientSide && HardModConfig.enchantTableLocked) {
                val state = level.getBlockState(hitResult.blockPos)
                if (state.block is EnchantingTableBlock) {
                    if (player is ServerPlayer) {
                        val excuse = LOCKED_EXCUSES[Random.nextInt(LOCKED_EXCUSES.size)]
                        player.sendSystemMessage(Announcer.colorize(excuse), true)
                    }
                    return@UseBlockCallback InteractionResult.FAIL
                }
            }
            InteractionResult.PASS
        })

        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticksSinceCheck++
            if (ticksSinceCheck >= CHECK_INTERVAL_TICKS) {
                ticksSinceCheck = 0
                checkAutoRevert(server)
            }
        }
    }

    private fun checkAutoRevert(server: MinecraftServer) {
        val revertAt = HardModConfig.enchantTableAutoRevertAtEpochMillis ?: return
        if (System.currentTimeMillis() < revertAt) return
        val wasLocked = HardModConfig.enchantTableLocked
        HardModConfig.setEnchantTableLocked(!wasLocked, null)
    }

    fun lock(server: MinecraftServer, durationSeconds: Int?) {
        val revertAt = durationSeconds?.let { System.currentTimeMillis() + it * 1000L }
        HardModConfig.setEnchantTableLocked(true, revertAt)
    }

    fun unlock(server: MinecraftServer, durationSeconds: Int?) {
        val revertAt = durationSeconds?.let { System.currentTimeMillis() + it * 1000L }
        HardModConfig.setEnchantTableLocked(false, revertAt)
    }
}
