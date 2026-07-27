package com.hardmod.feature.trialrewards

import com.hardmod.announce.Announcer
import com.hardmod.gui.ChestGuiMenu
import com.hardmod.gui.GuiItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.item.Items

/**
 * Biblioteca: lista SOLO los libros con pool de encantamiento de la tabla
 * actual (2 en la normal -- combate/utilidad y acuatico/tridente -- o 3
 * en la ominosa -- Breach/Density, Knockback/Punch/Smite/Looting/
 * Multishot, y Wind Burst) como items separados, para verlos de un
 * vistazo y abrir cada uno en BookPoolEditorGui. Tambien permite agregar
 * un libro nuevo (con pool vacio para configurar) directo desde aca.
 */
object BookLibraryGui {

    private const val SLOT_ADD = 45
    private const val SLOT_BACK = 49

    fun open(player: ServerPlayer, tableId: String, working: MutableList<TrialRewardEntry>, rolls: RollsState) {
        val inv = SimpleContainer(54)
        redraw(inv, working)

        val provider = MenuConstructor { syncId, playerInv, _ ->
            val menu = ChestGuiMenu(syncId, playerInv, inv, 6)
            val bookIdx = bookIndices(working)

            for ((slot, index) in bookIdx.withIndex()) {
                menu.onSlot(slot) { p, _, _ ->
                    BookPoolEditorGui.open(p, working, index) { back -> open(back, tableId, working, rolls) }
                }
            }
            menu.onSlot(SLOT_ADD) { p, _, _ ->
                working.add(TrialRewardEntry("minecraft:book", 1.0, 1, 1, emptyList()))
                Announcer.broadcast(p.level().server, "&a+ &7Nuevo libro agregado a la lista (sin guardar todavia) -- abrilo aca para configurar su pool.")
                open(p, tableId, working, rolls)
            }
            menu.onSlot(SLOT_BACK) { p, _, _ -> TrialRewardEditorGui.openWithWorking(p, tableId, working, rolls) }

            menu
        }

        player.openMenu(SimpleMenuProvider(provider, Announcer.colorize("&4&l☠ &c&lHardDeath &8» &7Biblioteca")))
    }

    private fun bookIndices(working: List<TrialRewardEntry>): List<Int> =
        working.indices.filter { working[it].itemId == "minecraft:book" }

    private fun redraw(inv: SimpleContainer, working: List<TrialRewardEntry>) {
        val bookIdx = bookIndices(working)
        val occupied = bookIdx.indices.toSet() + setOf(SLOT_ADD, SLOT_BACK)
        GuiItems.fillBackground(inv, 6, occupied)

        for ((slot, index) in bookIdx.withIndex()) {
            val entry = working[index]
            val lore = mutableListOf<String>()
            if (entry.enchantOptions.isEmpty()) {
                lore.add("&8▪ &7Pool de encantamiento: &cvacio")
            } else {
                lore.add("&8▪ &7Pool de encantamiento (&f${entry.enchantOptions.size}&7):")
                entry.enchantOptions.forEach { lore.add("&8   &f${enchantDisplayName(it)}") }
            }
            lore.add("")
            lore.add("&e▶ Click para abrir/editar")
            inv.setItem(
                slot,
                GuiItems.stack(
                    if (entry.enchantOptions.isEmpty()) Items.BOOK else Items.ENCHANTED_BOOK,
                    "&c&lLibro #${slot + 1}",
                    lore
                )
            )
        }

        inv.setItem(
            SLOT_ADD,
            GuiItems.stack(
                Items.BOOKSHELF, "&a&l+ Nuevo Libro",
                listOf(
                    "&7Agrega un libro nuevo a la lista",
                    "&7de recompensas, con pool vacio",
                    "&7para que lo configures.",
                    "",
                    "&e▶ Click para agregar"
                )
            )
        )
        inv.setItem(
            SLOT_BACK,
            GuiItems.stack(
                Items.ARROW, "&c&l« Volver a Recompensas",
                listOf("&7Los cambios ya quedan en memoria.", "&7Toca &aGuardar&7 alla para aplicarlos.")
            )
        )
    }

    private fun enchantDisplayName(id: String): String {
        val path = id.substringAfter(':')
        return path.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
