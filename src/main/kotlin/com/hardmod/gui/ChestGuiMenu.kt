package com.hardmod.gui

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType

/**
 * Chest UI reutilizable para los paneles de admin de este mod: cada slot
 * puede registrar una accion que se ejecuta en vez de dejar que vanilla
 * mueva/saque el item del slot (ver referencia chest-ui.md de la skill de
 * modding). button es 0 para click izquierdo, 1 para click derecho
 * (ContainerInput.PICKUP); ContainerInput.QUICK_MOVE es shift-click.
 *
 * Estilo "menu de plugin": cualquier slot NUESTRO (0 hasta rows*9-1) que
 * no tenga una accion registrada ni este en [allowVanillaInteraction]
 * queda completamente bloqueado -- no se puede sacar, mover, ni swapear
 * nada ahi (ni con click normal, shift-click, drag, tecla de hotbar, ni
 * Q para tirar). Los slots del inventario del propio jugador (fuera de
 * nuestro rango) se comportan normal. [allowVanillaInteraction] es para
 * casos como la fila de "arrastra un item nuevo aca" de
 * TrialRewardEditorGui, donde SI queremos que el jugador pueda soltar/
 * sacar items libremente.
 */
open class ChestGuiMenu(
    syncId: Int,
    playerInventory: Inventory,
    container: SimpleContainer,
    rows: Int,
    private val allowVanillaInteraction: Set<Int> = emptySet()
) : ChestMenu(if (rows >= 6) MenuType.GENERIC_9x6 else MenuType.GENERIC_9x3, syncId, playerInventory, container, rows) {

    private val containerSize = rows * 9
    private val slotActions = mutableMapOf<Int, (ServerPlayer, ContainerInput, Int) -> Unit>()

    fun onSlot(slot: Int, action: (ServerPlayer, ContainerInput, Int) -> Unit) {
        slotActions[slot] = action
    }

    override fun clicked(slotIndex: Int, button: Int, clickType: ContainerInput, player: Player) {
        val action = slotActions[slotIndex]
        if (action != null && player is ServerPlayer) {
            player.level().server.execute { action(player, clickType, button) }
            return
        }
        if (slotIndex in 0 until containerSize && slotIndex !in allowVanillaInteraction) {
            return // slot nuestro sin accion y no habilitado -- bloqueado del todo, no dejamos pasar a vanilla
        }
        super.clicked(slotIndex, button, clickType, player)
    }
}
