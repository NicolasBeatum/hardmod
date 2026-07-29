package com.hardmod.command

import com.hardmod.announce.Announcer
import com.hardmod.announce.MessagePresets
import com.hardmod.config.HardModConfig
import com.hardmod.feature.BossArenaCompass
import com.hardmod.feature.EnchantTableLock
import com.hardmod.feature.PvpScheduler
import com.hardmod.feature.RaidControl
import com.hardmod.feature.ServerShutdownScheduler
import com.hardmod.feature.TridentMode
import com.hardmod.feature.trialrewards.TrialRewardEditorGui
import com.hardmod.gui.AdminPanelGui
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.entity.MobCategory
import java.util.concurrent.CompletableFuture

/** Comandos de admin de HardMod (requieren COMMANDS_GAMEMASTER, igual que mision-admin en mision-mod). */
object HardModCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("hardmod")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                    .then(Commands.literal("panel").executes(::openPanel))
                    .then(
                        Commands.literal("mobcap")
                            .then(
                                Commands.argument("categoria", StringArgumentType.word())
                                    .suggests(::suggestCategories)
                                    .then(
                                        Commands.argument("multiplicador", DoubleArgumentType.doubleArg(0.1))
                                            .executes(::setMobcap)
                                    )
                            )
                    )
                    .then(
                        Commands.literal("enchanttable")
                            .then(
                                Commands.literal("lock")
                                    .executes { lockEnchantTable(it, null) }
                                    .then(
                                        Commands.argument("segundos", IntegerArgumentType.integer(1))
                                            .executes { lockEnchantTable(it, IntegerArgumentType.getInteger(it, "segundos")) }
                                    )
                            )
                            .then(
                                Commands.literal("unlock")
                                    .executes { unlockEnchantTable(it, null) }
                                    .then(
                                        Commands.argument("segundos", IntegerArgumentType.integer(1))
                                            .executes { unlockEnchantTable(it, IntegerArgumentType.getInteger(it, "segundos")) }
                                    )
                            )
                    )
                    .then(
                        Commands.literal("villagers")
                            .then(Commands.literal("on").executes { setVillagers(it, false) })
                            .then(Commands.literal("off").executes { setVillagers(it, true) })
                    )
                    .then(
                        Commands.literal("trident")
                            .then(Commands.literal("off").executes { setTrident(it, TridentMode.OFF) })
                            .then(Commands.literal("block").executes { setTrident(it, TridentMode.BLOCK_ALL) })
                            .then(Commands.literal("unbreaking3").executes { setTrident(it, TridentMode.MAX_UNBREAKING_III) })
                    )
                    .then(
                        Commands.literal("totem")
                            .then(
                                Commands.literal("chance")
                                    .then(
                                        Commands.argument("porcentaje", IntegerArgumentType.integer(0, 100))
                                            .executes(::setTotemChance)
                                    )
                            )
                    )
                    .then(
                        Commands.literal("raids")
                            .then(Commands.literal("on").executes { setRaids(it, true) })
                            .then(Commands.literal("off").executes { setRaids(it, false) })
                    )
                    .then(
                        Commands.literal("burn")
                            .then(Commands.literal("on").executes { setBurn(it, true) })
                            .then(Commands.literal("off").executes { setBurn(it, false) })
                    )
                    .then(
                        Commands.literal("nether")
                            .then(Commands.literal("lock").executes { setNetherLocked(it, true) })
                            .then(Commands.literal("unlock").executes { setNetherLocked(it, false) })
                    )
                    .then(
                        Commands.literal("end")
                            .then(Commands.literal("lock").executes { setEndLocked(it, true) })
                            .then(Commands.literal("unlock").executes { setEndLocked(it, false) })
                    )
                    .then(
                        Commands.literal("pvp")
                            .then(
                                Commands.literal("on")
                                    .executes { pvpOn(it, null) }
                                    .then(
                                        Commands.argument("minutos", IntegerArgumentType.integer(1))
                                            .executes { pvpOn(it, IntegerArgumentType.getInteger(it, "minutos")) }
                                    )
                            )
                            .then(Commands.literal("off").executes(::pvpOff))
                    )
                    .then(
                        Commands.literal("server")
                            .then(
                                Commands.literal("extend")
                                    .then(
                                        Commands.argument("minutos", IntegerArgumentType.integer(1))
                                            .executes(::extendServer)
                                    )
                            )
                            .then(
                                Commands.literal("time")
                                    .then(
                                        Commands.argument("hora", IntegerArgumentType.integer(0, 23))
                                            .executes { setServerTime(it, IntegerArgumentType.getInteger(it, "hora"), 0) }
                                            .then(
                                                Commands.argument("minuto", IntegerArgumentType.integer(0, 59))
                                                    .executes { setServerTime(it, IntegerArgumentType.getInteger(it, "hora"), IntegerArgumentType.getInteger(it, "minuto")) }
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("arenas")
                            .then(Commands.literal("reload").executes(::reloadArenas))
                    )
                    .then(
                        Commands.literal("rewards")
                            .then(Commands.literal("normal").executes { openRewards(it, "minecraft:chests/trial_chambers/reward") })
                            .then(Commands.literal("ominous").executes { openRewards(it, "minecraft:chests/trial_chambers/reward_ominous") })
                    )
                    .then(
                        Commands.literal("announce")
                            .then(Commands.literal("status").executes(::announceStatus))
                            .then(
                                Commands.literal("preset")
                                    .then(
                                        Commands.argument("nombre", StringArgumentType.word())
                                            .suggests(::suggestPresets)
                                            .executes(::announcePreset)
                                    )
                            )
                            .then(
                                Commands.argument("mensaje", StringArgumentType.greedyString())
                                    .executes(::announceCustom)
                            )
                    )
            )
        }
    }

    private fun suggestCategories(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(MobCategory.entries.map { it.serializedName }, builder)

    private fun suggestPresets(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        SharedSuggestionProvider.suggest(MessagePresets.all().keys, builder)

    private fun openPanel(ctx: CommandContext<CommandSourceStack>): Int {
        AdminPanelGui.open(ctx.source.playerOrException)
        return 1
    }

    private fun setMobcap(ctx: CommandContext<CommandSourceStack>): Int {
        val category = StringArgumentType.getString(ctx, "categoria")
        if (MobCategory.entries.none { it.serializedName == category }) {
            ctx.source.sendFailure(Component.literal("Categoria invalida: '$category'."))
            return 0
        }
        val multiplier = DoubleArgumentType.getDouble(ctx, "multiplicador")
        HardModConfig.setMobcapMultiplier(category, multiplier)
        ctx.source.sendSuccess({ Component.literal("Mobcap de '$category' en x$multiplier.") }, true)
        return 1
    }

    private fun lockEnchantTable(ctx: CommandContext<CommandSourceStack>, seconds: Int?): Int {
        EnchantTableLock.lock(ctx.source.server, seconds)
        return 1
    }

    private fun unlockEnchantTable(ctx: CommandContext<CommandSourceStack>, seconds: Int?): Int {
        EnchantTableLock.unlock(ctx.source.server, seconds)
        return 1
    }

    private fun setVillagers(ctx: CommandContext<CommandSourceStack>, block: Boolean): Int {
        HardModConfig.setBlockVillagers(block)
        ctx.source.sendSuccess({ Component.literal(if (block) "Aldeanos eliminados." else "Aldeanos activos.") }, true)
        return 1
    }

    private fun setTrident(ctx: CommandContext<CommandSourceStack>, mode: TridentMode): Int {
        HardModConfig.setTridentMode(mode)
        ctx.source.sendSuccess({ Component.literal("Mesa de encantamientos para tridente: ${tridentLabel(mode)}") }, true)
        return 1
    }

    private fun setTotemChance(ctx: CommandContext<CommandSourceStack>): Int {
        val chance = IntegerArgumentType.getInteger(ctx, "porcentaje")
        HardModConfig.setTotemActivationChance(chance)
        ctx.source.sendSuccess({ Component.literal("Probabilidad de activacion de totem: $chance%.") }, true)
        return 1
    }

    private fun setRaids(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        RaidControl.setEnabled(ctx.source.server, enabled)
        ctx.source.sendSuccess({ Component.literal(if (enabled) "Raids activadas." else "Raids desactivadas.") }, true)
        return 1
    }

    private fun setBurn(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        HardModConfig.setPermanentBurn(enabled)
        ctx.source.sendSuccess({ Component.literal(if (enabled) "Quemadura permanente activada." else "Quemadura permanente apagada.") }, true)
        return 1
    }

    private fun setNetherLocked(ctx: CommandContext<CommandSourceStack>, locked: Boolean): Int {
        HardModConfig.setNetherLocked(locked)
        ctx.source.sendSuccess({ Component.literal(if (locked) "Nether sellado." else "Nether disponible.") }, true)
        return 1
    }

    private fun setEndLocked(ctx: CommandContext<CommandSourceStack>, locked: Boolean): Int {
        HardModConfig.setEndLocked(locked)
        ctx.source.sendSuccess({ Component.literal(if (locked) "End sellado." else "End disponible.") }, true)
        return 1
    }

    private fun pvpOn(ctx: CommandContext<CommandSourceStack>, minutes: Int?): Int {
        val applied = PvpScheduler.activateManual(minutes?.toLong())
        ctx.source.sendSuccess({ Component.literal("PVP activado por ${applied}m.") }, true)
        return 1
    }

    private fun pvpOff(ctx: CommandContext<CommandSourceStack>): Int {
        PvpScheduler.deactivateManual()
        ctx.source.sendSuccess({ Component.literal("PVP cancelado.") }, true)
        return 1
    }

    private fun extendServer(ctx: CommandContext<CommandSourceStack>): Int {
        val minutes = IntegerArgumentType.getInteger(ctx, "minutos")
        val newTarget = ServerShutdownScheduler.extend(minutes)
        ctx.source.sendSuccess({ Component.literal("Cierre del servidor extendido: ahora cierra a las $newTarget.") }, true)
        return 1
    }

    private fun setServerTime(ctx: CommandContext<CommandSourceStack>, hour: Int, minute: Int): Int {
        HardModConfig.setShutdownTime(hour, minute)
        val formatted = "%02d:%02d".format(hour, minute)
        val newTarget = ServerShutdownScheduler.targetLabel()
        ctx.source.sendSuccess({ Component.literal("Hora de cierre del servidor configurada a las $formatted (proximo cierre: $newTarget).") }, true)
        return 1
    }

    private fun reloadArenas(ctx: CommandContext<CommandSourceStack>): Int {
        val count = BossArenaCompass.reload()
        ctx.source.sendSuccess({ Component.literal("Brujula de boss: $count arena(s) recargadas desde disco.") }, true)
        return 1
    }

    private fun openRewards(ctx: CommandContext<CommandSourceStack>, tableId: String): Int {
        TrialRewardEditorGui.open(ctx.source.playerOrException, tableId)
        return 1
    }

    private fun announceStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val server = ctx.source.server
        val lines = listOf(
            "&7Mesa de encantamientos: ${if (HardModConfig.enchantTableLocked) "&cbloqueada" else "&adisponible"}",
            "&7Aldeanos: ${if (HardModConfig.blockVillagers) "&celiminados" else "&aactivos"}",
            "&7Tridente: &f${tridentLabel(HardModConfig.tridentMode)}",
            "&7Totems: &f${HardModConfig.totemActivationChance}% de activacion",
            "&7Raids: ${if (HardModConfig.raidsEnabled) "&aactivadas" else "&cdesactivadas"}",
            "&7Quemadura permanente: ${if (HardModConfig.permanentBurn) "&4activada" else "&aapagada"}",
            "&7Nether: ${if (HardModConfig.netherLocked) "&csellado" else "&adisponible"}",
            "&7End: ${if (HardModConfig.endLocked) "&csellado" else "&adisponible"}",
            "&7Mobcap monster: &fx${HardModConfig.mobcapMultiplierFor(MobCategory.MONSTER.serializedName)}",
            "&7Mobcap creature: &fx${HardModConfig.mobcapMultiplierFor(MobCategory.CREATURE.serializedName)}",
            "&7PVP: ${PvpScheduler.statusLabel()}",
            "&7Cierre del servidor: &f${ServerShutdownScheduler.targetLabel()}"
        )
        Announcer.broadcast(server, lines.joinToString("\n"))
        return 1
    }

    private fun tridentLabel(mode: TridentMode): String = when (mode) {
        TridentMode.OFF -> "mesa sin restriccion"
        TridentMode.BLOCK_ALL -> "mesa bloqueada"
        TridentMode.MAX_UNBREAKING_III -> "mesa solo Unbreaking III maximo; yunque libre"
    }

    private fun announcePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "nombre")
        val preset = MessagePresets.get(name)
        if (preset == null) {
            ctx.source.sendFailure(Component.literal("No existe el preset '$name'."))
            return 0
        }
        val server = ctx.source.server
        Announcer.broadcast(server, preset.message)
        if (preset.commands.isNotEmpty()) {
            // Permiso de consola (no el del jugador que dispara el preset) --
            // los comandos los escribio el admin en presets.json de antemano,
            // asi que son confiables, y necesitan poder tocar cosas como
            // difficulty/gamerule que un jugador comun no podria.
            val commandSource = server.createCommandSourceStack()
            for (command in preset.commands) {
                server.commands.performPrefixedCommand(commandSource, command)
            }
        }
        return 1
    }

    private fun announceCustom(ctx: CommandContext<CommandSourceStack>): Int {
        val message = StringArgumentType.getString(ctx, "mensaje")
        Announcer.broadcast(ctx.source.server, message)
        return 1
    }
}
