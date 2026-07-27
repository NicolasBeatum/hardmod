package com.hardmod.feature.trialrewards

import com.hardmod.announce.Announcer
import com.hardmod.gui.ChestGuiMenu
import com.hardmod.gui.GuiItems
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Rango de tiradas editable en memoria durante la sesion del editor (ver TrialRewardDefaultsLoader.RollRange, que es la version inmutable que se guarda). */
class RollsState(var min: Int, var max: Int)

/** Pagina actual del listado de entradas (27 por pagina) -- editable en memoria durante la sesion. */
class PageState(var page: Int = 0)

/** Id sentinela para una entrada "sin recompensa" (minecraft:empty de vanilla) -- se muestra con structure_void porque air no se puede representar bien como item en un slot. */
const val AIR_ENTRY_ID = "minecraft:air"

/**
 * Editor de recompensas de trial chambers (chest UI, simplificado): carga
 * el override guardado si existe, o si no la composicion default de
 * vanilla (aplanada con precision decimal, ver TrialRewardDefaultsLoader).
 * Slots 0-26 muestran las entradas actuales (click izq = +0.1 peso, click
 * der = -0.1 peso, shift click = quitar). Slots 27-35 son para arrastrar
 * items nuevos desde el inventario del jugador. "Guardar" persiste y
 * aplica; "Restaurar Default" descarta el override y vuelve a la
 * composicion de vanilla (items Y rango de tiradas).
 */
object TrialRewardEditorGui {

    private const val ENTRY_SLOTS = 27
    private const val STAGING_START = 27
    private const val STAGING_END = 35
    private const val SLOT_PAGE_PREV = 36
    private const val SLOT_PAGE_INFO = 37
    private const val SLOT_PAGE_NEXT = 38
    private const val SLOT_ADD_AIR = 40
    private const val SLOT_INFO = 45
    private const val SLOT_ROLLS_MIN = 46
    private const val SLOT_ROLLS_MAX = 48
    private const val SLOT_SAVE = 49
    private const val SLOT_RESTORE_DEFAULT = 51
    private const val SLOT_LIBRARY = 53
    private const val WEIGHT_STEP = 0.1

    fun open(player: ServerPlayer, tableId: String) {
        val resourceManager = player.level().server.resourceManager
        val working: MutableList<TrialRewardEntry> = (
            if (TrialRewardConfig.hasOverride(tableId)) TrialRewardConfig.getOverride(tableId)
            else TrialRewardDefaultsLoader.resolveFlat(resourceManager, tableId)
        ).toMutableList()
        val savedRange = TrialRewardConfig.getRollRange(tableId)
        val range = savedRange ?: TrialRewardDefaultsLoader.computeRollRange(resourceManager, tableId)
        openWithWorking(player, tableId, working, RollsState(range.min, range.max), PageState())
    }

    /** Reabre el editor con una lista [working], un rango de tiradas [rolls] y una pagina [page] ya existentes (en vez de recargarlos) -- lo usan BookLibraryGui y los botones de pagina para "volver"/navegar sin perder cambios en memoria que todavia no se guardaron. */
    fun openWithWorking(player: ServerPlayer, tableId: String, working: MutableList<TrialRewardEntry>, rolls: RollsState, page: PageState = PageState()) {
        val inv = SimpleContainer(54)
        redraw(inv, working, rolls, page)

        val provider = MenuConstructor { syncId, playerInv, _ ->
            val menu = ChestGuiMenu(syncId, playerInv, inv, 6, allowVanillaInteraction = (STAGING_START..STAGING_END).toSet())

            for (slot in 0 until ENTRY_SLOTS) {
                menu.onSlot(slot) { p, clickType, button ->
                    val index = page.page * ENTRY_SLOTS + slot
                    if (index >= working.size) return@onSlot
                    handleEntryClick(p, working, index, clickType, button)
                    clampPage(working, page)
                    redraw(inv, working, rolls, page)
                }
            }
            menu.onSlot(SLOT_PAGE_PREV) { p, _, _ ->
                if (page.page > 0) page.page--
                redraw(inv, working, rolls, page)
            }
            menu.onSlot(SLOT_PAGE_NEXT) { p, _, _ ->
                if ((page.page + 1) * ENTRY_SLOTS < working.size) page.page++
                redraw(inv, working, rolls, page)
            }
            menu.onSlot(SLOT_ADD_AIR) { p, _, _ ->
                working.add(TrialRewardEntry(AIR_ENTRY_ID, 1.0, 1, 1))
                page.page = (working.size - 1) / ENTRY_SLOTS
                Announcer.broadcast(p.level().server, "&7+ &fAire&7 (sin recompensa) agregado a la lista (sin guardar todavia).")
                redraw(inv, working, rolls, page)
            }
            menu.onSlot(SLOT_LIBRARY) { p, _, _ -> BookLibraryGui.open(p, tableId, working, rolls) }
            menu.onSlot(SLOT_ROLLS_MIN) { _, _, button ->
                rolls.min = (rolls.min + if (button == 1) -1 else 1).coerceAtLeast(0)
                if (rolls.min > rolls.max) rolls.max = rolls.min
                redraw(inv, working, rolls, page)
            }
            menu.onSlot(SLOT_ROLLS_MAX) { _, _, button ->
                rolls.max = (rolls.max + if (button == 1) -1 else 1).coerceAtLeast(rolls.min).coerceAtLeast(1)
                redraw(inv, working, rolls, page)
            }
            menu.onSlot(SLOT_SAVE) { p, _, _ -> save(p, tableId, working, rolls, page, inv) }
            menu.onSlot(SLOT_RESTORE_DEFAULT) { p, _, _ -> restoreDefault(p, tableId, working, rolls, page, inv) }

            menu
        }

        player.openMenu(SimpleMenuProvider(provider, Announcer.colorize("&4&l☠ &c&lHardDeath &8» &7Recompensas")))
    }

    /** Si se borro/agrego algo y la pagina actual quedo "fuera de rango" (ej. se borro el ultimo item de la ultima pagina), la trae de vuelta a una pagina valida. */
    private fun clampPage(working: List<TrialRewardEntry>, page: PageState) {
        val maxPage = maxOf(0, (working.size - 1) / ENTRY_SLOTS)
        if (page.page > maxPage) page.page = maxPage
    }

    private fun handleEntryClick(player: ServerPlayer, working: MutableList<TrialRewardEntry>, index: Int, clickType: ContainerInput, button: Int) {
        val entry = working[index]
        when {
            clickType == ContainerInput.QUICK_MOVE -> {
                working.removeAt(index)
                Announcer.broadcast(player.level().server, "&7Se quito &f${entry.itemId}&7 de las recompensas (sin guardar todavia).")
            }
            button == 1 -> {
                val newWeight = entry.weight - WEIGHT_STEP
                if (newWeight <= 0.0) working.removeAt(index) else working[index] = entry.copy(weight = roundWeight(newWeight))
            }
            else -> {
                working[index] = entry.copy(weight = roundWeight(entry.weight + WEIGHT_STEP))
            }
        }
    }

    private fun collectStaged(inv: SimpleContainer): List<TrialRewardEntry> {
        val staged = mutableListOf<TrialRewardEntry>()
        for (slot in STAGING_START..STAGING_END) {
            val stack = inv.getItem(slot)
            if (!stack.isEmpty) {
                val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                staged.add(TrialRewardEntry(itemId, 1.0, stack.count, stack.count))
                inv.setItem(slot, ItemStack.EMPTY)
            }
        }
        return staged
    }

    private fun save(player: ServerPlayer, tableId: String, working: MutableList<TrialRewardEntry>, rolls: RollsState, page: PageState, inv: SimpleContainer) {
        val staged = collectStaged(inv)
        working.addAll(staged)
        TrialRewardConfig.setOverride(tableId, working)
        TrialRewardConfig.setRollRange(tableId, TrialRewardDefaultsLoader.RollRange(rolls.min, rolls.max))
        reloadResources(player)
        Announcer.broadcast(player.level().server, "&a✔ Recompensas de &f$tableId&a guardadas (${working.size} items, ${rolls.min}-${rolls.max} tiradas).")
        redraw(inv, working, rolls, page)
    }

    private fun restoreDefault(player: ServerPlayer, tableId: String, working: MutableList<TrialRewardEntry>, rolls: RollsState, page: PageState, inv: SimpleContainer) {
        TrialRewardConfig.clearOverride(tableId)
        reloadResources(player)
        val resourceManager = player.level().server.resourceManager
        working.clear()
        working.addAll(TrialRewardDefaultsLoader.resolveFlat(resourceManager, tableId))
        val range = TrialRewardDefaultsLoader.computeRollRange(resourceManager, tableId)
        rolls.min = range.min
        rolls.max = range.max
        page.page = 0
        Announcer.broadcast(player.level().server, "&e↺ Recompensas de &f$tableId&e restauradas al default de Minecraft.")
        redraw(inv, working, rolls, page)
    }

    private fun reloadResources(player: ServerPlayer) {
        val server = player.level().server
        server.reloadResources(server.packRepository.selectedIds)
    }

    private fun roundWeight(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private fun redraw(inv: SimpleContainer, working: List<TrialRewardEntry>, rolls: RollsState, page: PageState) {
        // Filas 4-5 (bordes/relleno rojo-negro, estilo menu de plugin) -- filas 0-2
        // (entradas) y fila 3 (staging de arrastre) se manejan aparte abajo.
        val lowerRowsOccupied = (0..STAGING_END).toSet() +
            setOf(SLOT_PAGE_PREV, SLOT_PAGE_INFO, SLOT_PAGE_NEXT, SLOT_ADD_AIR, SLOT_INFO, SLOT_ROLLS_MIN, SLOT_ROLLS_MAX, SLOT_SAVE, SLOT_RESTORE_DEFAULT, SLOT_LIBRARY)
        GuiItems.fillBackground(inv, 6, lowerRowsOccupied)

        val totalPages = maxOf(1, (working.size + ENTRY_SLOTS - 1) / ENTRY_SLOTS)
        val totalWeight = working.sumOf { it.weight }.coerceAtLeast(0.0001)
        val pageStart = page.page * ENTRY_SLOTS
        for (slot in 0 until ENTRY_SLOTS) {
            val index = pageStart + slot
            if (index >= working.size) {
                inv.setItem(slot, GuiItems.stack(Items.DYE.pick(DyeColor.GRAY), "&8(vacio)", listOf("&8Arrastra un item aca abajo", "&8y toca Guardar para agregarlo")))
                continue
            }
            val entry = working[index]
            val isAir = entry.itemId == AIR_ENTRY_ID
            val itemId = Identifier.tryParse(entry.itemId)
            val item = when {
                isAir -> Items.STRUCTURE_VOID
                itemId != null -> BuiltInRegistries.ITEM.getValue(itemId)
                else -> Items.BARRIER
            }
            val percent = (entry.weight * 100.0 / totalWeight)
            val countLabel = if (entry.minCount == entry.maxCount) "${entry.minCount}" else "${entry.minCount}-${entry.maxCount}"
            val isBook = entry.itemId == "minecraft:book"
            val lore = mutableListOf(
                "&8▪ &7Peso: &f${"%.3f".format(entry.weight)} &8(&e~${"%.2f".format(percent)}%&8)"
            )
            if (isAir) {
                lore.add("&8▪ &7Sin recompensa (aire)")
            } else {
                lore.add("&8▪ &7Cantidad por recompensa: &f$countLabel")
            }
            if (isBook) {
                lore.add(
                    if (entry.enchantOptions.isEmpty()) "&8▪ &7Pool de encantamiento: &cvacio"
                    else "&8▪ &7Pool de encantamiento: &f${entry.enchantOptions.size} opciones &8(ver Biblioteca)"
                )
            }
            lore.add("")
            lore.add("&e▶ Izq/der: &f+/- ${"%.1f".format(WEIGHT_STEP)} peso")
            lore.add("&e▶ Shift-click: &fquitar de la lista")
            inv.setItem(
                slot,
                GuiItems.stack(
                    item, "&c&l${if (isAir) "Aire (sin recompensa)" else entry.itemId}",
                    lore
                )
            )
        }
        inv.setItem(SLOT_INFO, GuiItems.stack(
            Items.PAPER, "&c&lInstrucciones",
            listOf(
                "&8▪ &7Entradas actuales: &f${working.size}",
                "&8▪ &7Tiradas por cofre: &f${rolls.min}-${rolls.max}",
                "",
                "&7Arriba: recompensas actuales.",
                "&7Fila de abajo (slots libres): arrastra",
                "&7items nuevos desde tu inventario ahi.",
                "&7Despues toca &aGuardar&7 para aplicarlos."
            )
        ))
        inv.setItem(SLOT_PAGE_PREV, GuiItems.stack(
            Items.ARROW, "&c&l« Pagina Anterior",
            listOf(if (page.page > 0) "&e▶ Click para ir atras" else "&8Ya estas en la primera pagina")
        ))
        inv.setItem(SLOT_PAGE_INFO, GuiItems.stack(
            Items.BOOK, "&c&lPagina ${page.page + 1} / $totalPages",
            listOf("&8▪ &7$ENTRY_SLOTS entradas por pagina")
        ))
        inv.setItem(SLOT_PAGE_NEXT, GuiItems.stack(
            Items.ARROW, "&c&l» Pagina Siguiente",
            listOf(if ((page.page + 1) * ENTRY_SLOTS < working.size) "&e▶ Click para ir adelante" else "&8Ya estas en la ultima pagina")
        ))
        inv.setItem(SLOT_ADD_AIR, GuiItems.stack(
            Items.STRUCTURE_VOID, "&c&l+ Aire (sin recompensa)",
            listOf(
                "&8▪ &7Agrega una entrada que a veces",
                "&8▪ &7no da nada (minecraft:empty).",
                "",
                "&e▶ Click para agregar"
            )
        ))
        inv.setItem(SLOT_ROLLS_MIN, GuiItems.stack(
            Items.HOPPER, "&c&lTiradas Minimas",
            listOf("&8▪ &7Actual: &f${rolls.min}", "", "&e▶ Izq/der: &f+1/-1")
        ))
        inv.setItem(SLOT_ROLLS_MAX, GuiItems.stack(
            Items.HOPPER, "&c&lTiradas Maximas",
            listOf("&8▪ &7Actual: &f${rolls.max}", "", "&e▶ Izq/der: &f+1/-1")
        ))
        inv.setItem(SLOT_SAVE, GuiItems.stack(Items.EMERALD_BLOCK, "&a&lGuardar y Aplicar", listOf("&7Guarda esta lista y la aplica", "&7al mundo (recarga recursos).")))
        inv.setItem(SLOT_RESTORE_DEFAULT, GuiItems.stack(Items.BARRIER, "&c&lRestaurar Default de Minecraft", listOf("&7Descarta el override y vuelve", "&7a la composicion real de vanilla.")))
        val bookCount = working.count { it.itemId == "minecraft:book" }
        inv.setItem(SLOT_LIBRARY, GuiItems.stack(
            Items.BOOKSHELF, "&c&l📚 Biblioteca",
            listOf(
                "&8▪ &7Libros con pool de encantamiento: &f$bookCount",
                "",
                "&7Ve los pools de TODOS los libros",
                "&7de esta tabla de un vistazo, los",
                "&7edita, o agrega uno nuevo.",
                "",
                "&e▶ Click para abrir"
            )
        ))
    }
}
