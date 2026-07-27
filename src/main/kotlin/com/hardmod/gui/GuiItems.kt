package com.hardmod.gui

import com.hardmod.announce.Announcer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.ItemLike

/**
 * Helper para armar los items "entry" de los paneles chest UI de este mod
 * (nombre + lore, ver chest-ui.md). name/lore soportan codigos '&' (misma
 * paleta que Announcer.colorize) para que los paneles se vean consistentes
 * con los anuncios de chat. Tambien arma el fondo/borde de vidrio de color
 * tipico de menus de plugin (rojo en el borde, negro de relleno).
 */
object GuiItems {

    fun stack(item: ItemLike, name: String, lore: List<String> = emptyList(), count: Int = 1): ItemStack {
        val stack = ItemStack(item, count)
        stack.set(DataComponents.CUSTOM_NAME, Announcer.colorize(name))
        if (lore.isNotEmpty()) {
            stack.set(DataComponents.LORE, ItemLore(lore.map { Announcer.colorize(it) }))
        }
        return stack
    }

    /** Panel de vidrio de color sin nombre visible (relleno decorativo, no interactuable). */
    fun filler(color: DyeColor): ItemStack {
        val stack = ItemStack(Items.STAINED_GLASS_PANE.pick(color))
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "))
        return stack
    }

    /**
     * Rellena todo slot vacio de [inv] (fuera de [occupied]) con vidrio: rojo
     * en el borde de la grilla (fila superior/inferior, columnas de los
     * costados) y negro en el resto -- el look "menu de plugin" tipico.
     */
    fun fillBackground(inv: SimpleContainer, rows: Int, occupied: Set<Int>) {
        for (slot in 0 until rows * 9) {
            if (slot in occupied) continue
            val row = slot / 9
            val col = slot % 9
            val isBorder = row == 0 || row == rows - 1 || col == 0 || col == 8
            inv.setItem(slot, filler(if (isBorder) DyeColor.RED else DyeColor.BLACK))
        }
    }
}
