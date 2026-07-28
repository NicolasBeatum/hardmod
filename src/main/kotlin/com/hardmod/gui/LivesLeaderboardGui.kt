package com.hardmod.gui

import com.hardmod.announce.Announcer
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items

/**
 * Leaderboard publico de vidas (`/lifestop`): lee el objective de scoreboard
 * vanilla "hdc_lives" (el mismo que sincroniza hard-death-core-mod, ver
 * LivesEliminationAnnouncer) SIN acoplarse a ese mod, y muestra una cabeza
 * por jugador con su skin real + cuantas vidas le quedan, ordenado de mas a
 * menos. Solo lectura -- ningun slot es interactuable, es un tablero para
 * mirar (mismo estilo "plugin" que el resto de las chest GUI de este mod).
 */
object LivesLeaderboardGui {

    private const val OBJECTIVE_NAME = "hdc_lives"
    private const val ENTRY_SLOTS = 45
    private const val SLOT_PAGE_PREV = 45
    private const val SLOT_PAGE_INFO = 49
    private const val SLOT_PAGE_NEXT = 53

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(Commands.literal("lifestop").executes(::openCommand))
        }
    }

    private fun openCommand(ctx: CommandContext<CommandSourceStack>): Int {
        open(ctx.source.playerOrException)
        return 1
    }

    fun open(player: ServerPlayer, page: Int = 0) {
        val server = player.level().server
        val objective = server.scoreboard.getObjective(OBJECTIVE_NAME)
        val entries = if (objective == null) emptyList() else server.scoreboard.listPlayerScores(objective)
            .sortedByDescending { it.value() }

        val inv = SimpleContainer(54)
        redraw(inv, server, entries, page)

        val provider = MenuConstructor { syncId, playerInv, _ ->
            val menu = ChestGuiMenu(syncId, playerInv, inv, 6)
            menu.onSlot(SLOT_PAGE_PREV) { p, _, _ -> if (page > 0) open(p, page - 1) }
            menu.onSlot(SLOT_PAGE_NEXT) { p, _, _ -> if ((page + 1) * ENTRY_SLOTS < entries.size) open(p, page + 1) }
            menu
        }

        player.openMenu(SimpleMenuProvider(provider, Announcer.colorize("&4&l☠ &c&lHardDeath &8» &7Top de Vidas")))
    }

    private fun redraw(inv: SimpleContainer, server: net.minecraft.server.MinecraftServer, entries: List<net.minecraft.world.scores.PlayerScoreEntry>, page: Int) {
        val totalPages = maxOf(1, (entries.size + ENTRY_SLOTS - 1) / ENTRY_SLOTS)
        val occupied = (0 until ENTRY_SLOTS).toSet() + setOf(SLOT_PAGE_PREV, SLOT_PAGE_INFO, SLOT_PAGE_NEXT)
        GuiItems.fillBackground(inv, 6, occupied)

        val pageStart = page * ENTRY_SLOTS
        for (slot in 0 until ENTRY_SLOTS) {
            val index = pageStart + slot
            if (index >= entries.size) {
                inv.setItem(slot, GuiItems.stack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), "&8(vacio)"))
                continue
            }
            val entry = entries[index]
            val rank = index + 1
            val lives = entry.value()
            val livesColor = when {
                lives <= 0 -> "&4"
                lives == 1 -> "&c"
                lives <= 3 -> "&e"
                else -> "&a"
            }
            val onlineProfile = server.playerList.getPlayerByName(entry.owner())?.gameProfile
            inv.setItem(
                slot,
                GuiItems.playerHead(
                    entry.owner(),
                    onlineProfile,
                    "&e#$rank &f${entry.owner()}",
                    listOf("&8▪ &7Vidas: $livesColor$lives")
                )
            )
        }

        inv.setItem(SLOT_PAGE_PREV, GuiItems.stack(
            Items.ARROW, "&c&l« Pagina Anterior",
            listOf(if (page > 0) "&e▶ Click para ir atras" else "&8Ya estas en la primera pagina")
        ))
        inv.setItem(SLOT_PAGE_INFO, GuiItems.stack(
            Items.BOOK, "&c&lPagina ${page + 1} / $totalPages",
            listOf("&8▪ &7${entries.size} jugador(es) registrados")
        ))
        inv.setItem(SLOT_PAGE_NEXT, GuiItems.stack(
            Items.ARROW, "&c&l» Pagina Siguiente",
            listOf(if ((page + 1) * ENTRY_SLOTS < entries.size) "&e▶ Click para ir adelante" else "&8Ya estas en la ultima pagina")
        ))
    }
}
