package com.hardmod.feature.trialrewards

import com.hardmod.announce.Announcer
import com.hardmod.gui.ChestGuiMenu
import com.hardmod.gui.GuiItems
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

/**
 * Sub-pantalla para editar el pool de encantamiento aleatorio de UN libro
 * puntual (ver TrialRewardEntry.enchantOptions) -- se abre desde
 * BookLibraryGui. Cada encantamiento es un item que se prende/apaga con
 * un click; "Volver" llama a [onBack] (quien abrio esta pantalla decide a
 * donde volver -- normalmente a BookLibraryGui) -- los cambios ya quedan
 * aplicados en memoria sobre la misma lista [working], falta "Guardar" en
 * el editor principal para persistirlos y aplicarlos de verdad.
 */
object BookPoolEditorGui {

    private const val SLOT_BACK = 49

    /** Catalogo curado de encantamientos disponibles para armar/ampliar pools -- incluye los que ya usan las tablas de trials (riptide, loyalty, sharpness, breach, wind_burst, etc.) mas el resto de encantamientos comunes para variedad. */
    private val ALL_ENCHANTMENTS: List<ResourceKey<Enchantment>> = listOf(
        Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.BLAST_PROTECTION,
        Enchantments.PROJECTILE_PROTECTION, Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.THORNS,
        Enchantments.DEPTH_STRIDER, Enchantments.FROST_WALKER, Enchantments.BINDING_CURSE, Enchantments.SOUL_SPEED,
        Enchantments.SWIFT_SNEAK, Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
        Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.SWEEPING_EDGE,
        Enchantments.EFFICIENCY, Enchantments.SILK_TOUCH, Enchantments.UNBREAKING, Enchantments.FORTUNE,
        Enchantments.POWER, Enchantments.PUNCH, Enchantments.FLAME, Enchantments.INFINITY,
        Enchantments.LUCK_OF_THE_SEA, Enchantments.LURE, Enchantments.LOYALTY, Enchantments.IMPALING,
        Enchantments.RIPTIDE, Enchantments.CHANNELING, Enchantments.MULTISHOT, Enchantments.QUICK_CHARGE,
        Enchantments.PIERCING, Enchantments.DENSITY, Enchantments.BREACH, Enchantments.WIND_BURST,
        Enchantments.MENDING, Enchantments.VANISHING_CURSE
    )

    fun open(player: ServerPlayer, working: MutableList<TrialRewardEntry>, index: Int, onBack: (ServerPlayer) -> Unit) {
        val inv = SimpleContainer(54)
        redraw(inv, working, index)

        val provider = MenuConstructor { syncId, playerInv, _ ->
            val menu = ChestGuiMenu(syncId, playerInv, inv, 6)

            for ((slot, key) in ALL_ENCHANTMENTS.withIndex()) {
                menu.onSlot(slot) { p, _, _ ->
                    toggleEnchant(working, index, key)
                    redraw(inv, working, index)
                }
            }
            menu.onSlot(SLOT_BACK) { p, _, _ -> onBack(p) }

            menu
        }

        player.openMenu(SimpleMenuProvider(provider, Announcer.colorize("&4&l☠ &c&lHardDeath &8» &7Pool de Libro")))
    }

    private fun toggleEnchant(working: MutableList<TrialRewardEntry>, index: Int, key: ResourceKey<Enchantment>) {
        if (index >= working.size) return
        val id = key.identifier().toString()
        val entry = working[index]
        val current = entry.enchantOptions
        val updated = if (current.contains(id)) current - id else current + id
        working[index] = entry.copy(enchantOptions = updated)
    }

    private fun displayName(key: ResourceKey<Enchantment>): String =
        key.identifier().path.split("_").joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun redraw(inv: SimpleContainer, working: List<TrialRewardEntry>, index: Int) {
        val occupied = (0 until ALL_ENCHANTMENTS.size).toSet() + SLOT_BACK
        GuiItems.fillBackground(inv, 6, occupied)

        val entry = working.getOrNull(index)
        val current = entry?.enchantOptions ?: emptyList()
        for ((slot, key) in ALL_ENCHANTMENTS.withIndex()) {
            val id = key.identifier().toString()
            val inPool = current.contains(id)
            val name = displayName(key)
            inv.setItem(
                slot,
                GuiItems.stack(
                    if (inPool) Items.ENCHANTED_BOOK else Items.BOOK,
                    if (inPool) "&a&l$name" else "&7$name",
                    listOf(
                        if (inPool) "&8▪ &aEn el pool de este libro" else "&8▪ &7No esta en el pool",
                        "",
                        "&e▶ Click para alternar"
                    )
                )
            )
        }
        inv.setItem(
            SLOT_BACK,
            GuiItems.stack(
                Items.ARROW, "&c&l« Volver a la Biblioteca",
                listOf("&7Los cambios ya quedan en memoria.", "&7Toca &aGuardar&7 en Recompensas para aplicarlos.")
            )
        )
    }
}
