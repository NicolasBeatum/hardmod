package com.hardmod.feature

import com.hardmod.announce.Announcer
import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EndPortalFrameBlock
import kotlin.random.Random

/**
 * Bloqueo de acceso a Nether y End (bloqueados por defecto, hasta que un
 * admin los active): no se puede poner un ojo de ender en un marco de
 * portal del End, ni encender un portal del Nether sobre obsidiana --
 * ni con el mechero (pedernal y eslabon) ni con una carga de fuego, las
 * dos formas vanilla de prenderlo a mano. Ambos casos son interacciones
 * de "usar item sobre bloque", asi que se enganchan con el mismo
 * UseBlockCallback que ya usa EnchantTableLock.
 */
object DimensionLock {

    private val END_EXCUSES = listOf(
        "&8[&5✦&8] &7El End permanece sellado -- el ojo no reacciona.",
        "&8[&5✦&8] &7Una fuerza desconocida rechaza el ojo de ender.",
        "&8[&5✦&8] &7El portal del End no responde. Algo lo mantiene cerrado."
    )
    private val NETHER_EXCUSES = listOf(
        "&8[&4✦&8] &7El portal no enciende -- el Nether esta sellado.",
        "&8[&4✦&8] &7Las llamas se apagan solas. Algo bloquea el Nether.",
        "&8[&4✦&8] &7Una fuerza infernal impide abrir el portal."
    )

    fun register() {
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, hitResult ->
            if (level.isClientSide) return@UseBlockCallback InteractionResult.PASS
            val state = level.getBlockState(hitResult.blockPos)
            val heldStack = player.getItemInHand(hand)

            if (HardModConfig.endLocked && state.block is EndPortalFrameBlock && heldStack.`is`(Items.ENDER_EYE)) {
                warn(player, END_EXCUSES)
                return@UseBlockCallback InteractionResult.FAIL
            }

            val isIgnitionItem = heldStack.`is`(Items.FLINT_AND_STEEL) || heldStack.`is`(Items.FIRE_CHARGE)
            if (HardModConfig.netherLocked && state.`is`(Blocks.OBSIDIAN) && isIgnitionItem) {
                warn(player, NETHER_EXCUSES)
                return@UseBlockCallback InteractionResult.FAIL
            }

            InteractionResult.PASS
        })
    }

    private fun warn(player: net.minecraft.world.entity.player.Player, excuses: List<String>) {
        if (player is ServerPlayer) {
            player.sendSystemMessage(Announcer.colorize(excuses[Random.nextInt(excuses.size)]), true)
        }
    }
}
