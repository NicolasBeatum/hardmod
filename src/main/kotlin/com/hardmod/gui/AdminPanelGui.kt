package com.hardmod.gui

import com.hardmod.announce.Announcer
import com.hardmod.config.HardModConfig
import com.hardmod.feature.EnchantTableLock
import com.hardmod.feature.PvpScheduler
import com.hardmod.feature.RaidControl
import com.hardmod.feature.ServerShutdownScheduler
import com.hardmod.feature.TridentMode
import com.hardmod.feature.trialrewards.TrialRewardEditorGui
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.item.Items

/**
 * Panel central de administracion: cada item es un toggle/ciclo que
 * alterna en un click y anuncia el cambio -- todo lo mismo que hacen los
 * comandos /hardmod, para admins que prefieran clickear en vez de tipear.
 * Fondo de vidrio rojo/negro (estilo menu de plugin, ver GuiItems).
 */
object AdminPanelGui {

    private val MOBCAP_STEPS = listOf(1.0, 1.5, 2.0, 3.0, 5.0, 8.0)
    private val TOTEM_CHANCE_STEPS = listOf(0, 25, 50, 75, 100)

    private const val SLOT_ENCHANT_TABLE = 10
    private const val SLOT_VILLAGERS = 12
    private const val SLOT_TRIDENT = 14
    private const val SLOT_BURN = 16
    private const val SLOT_NETHER = 19
    private const val SLOT_END = 21
    private const val SLOT_TOTEM = 23
    private const val SLOT_RAIDS = 25
    private const val SLOT_MOBCAP_MONSTER = 28
    private const val SLOT_MOBCAP_CREATURE = 30
    private const val SLOT_REWARDS_NORMAL = 38
    private const val SLOT_REWARDS_OMINOUS = 40
    private const val SLOT_STATUS = 42

    private val OCCUPIED_SLOTS = setOf(
        SLOT_ENCHANT_TABLE, SLOT_VILLAGERS, SLOT_TRIDENT, SLOT_BURN, SLOT_NETHER, SLOT_END, SLOT_TOTEM, SLOT_RAIDS,
        SLOT_MOBCAP_MONSTER, SLOT_MOBCAP_CREATURE,
        SLOT_REWARDS_NORMAL, SLOT_REWARDS_OMINOUS, SLOT_STATUS
    )

    fun open(player: ServerPlayer) {
        val inv = SimpleContainer(54)
        refreshItems(inv)

        val provider = MenuConstructor { syncId, playerInv, _ ->
            val menu = ChestGuiMenu(syncId, playerInv, inv, 6)

            menu.onSlot(SLOT_ENCHANT_TABLE) { p, _, _ ->
                if (HardModConfig.enchantTableLocked) EnchantTableLock.unlock(p.level().server, null) else EnchantTableLock.lock(p.level().server, null)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_VILLAGERS) { p, _, _ ->
                val next = !HardModConfig.blockVillagers
                HardModConfig.setBlockVillagers(next)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_TRIDENT) { p, _, button ->
                val modes = TridentMode.entries
                val current = modes.indexOf(HardModConfig.tridentMode)
                val next = if (button == 1) (current - 1 + modes.size) % modes.size else (current + 1) % modes.size
                HardModConfig.setTridentMode(modes[next])
                refreshItems(inv)
            }
            menu.onSlot(SLOT_BURN) { p, _, _ ->
                val next = !HardModConfig.permanentBurn
                HardModConfig.setPermanentBurn(next)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_NETHER) { _, _, _ ->
                HardModConfig.setNetherLocked(!HardModConfig.netherLocked)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_END) { _, _, _ ->
                HardModConfig.setEndLocked(!HardModConfig.endLocked)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_TOTEM) { _, _, button ->
                val current = TOTEM_CHANCE_STEPS.indexOf(HardModConfig.totemActivationChance).let { if (it < 0) 0 else it }
                val next = if (button == 1) {
                    (current - 1 + TOTEM_CHANCE_STEPS.size) % TOTEM_CHANCE_STEPS.size
                } else {
                    (current + 1) % TOTEM_CHANCE_STEPS.size
                }
                HardModConfig.setTotemActivationChance(TOTEM_CHANCE_STEPS[next])
                refreshItems(inv)
            }
            menu.onSlot(SLOT_RAIDS) { p, _, _ ->
                RaidControl.setEnabled(p.level().server, !HardModConfig.raidsEnabled)
                refreshItems(inv)
            }
            menu.onSlot(SLOT_MOBCAP_MONSTER) { p, _, button -> cycleMobcap(p, inv, MobCategory.MONSTER, button) }
            menu.onSlot(SLOT_MOBCAP_CREATURE) { p, _, button -> cycleMobcap(p, inv, MobCategory.CREATURE, button) }
            menu.onSlot(SLOT_REWARDS_NORMAL) { p, _, _ -> TrialRewardEditorGui.open(p, "minecraft:chests/trial_chambers/reward") }
            menu.onSlot(SLOT_REWARDS_OMINOUS) { p, _, _ -> TrialRewardEditorGui.open(p, "minecraft:chests/trial_chambers/reward_ominous") }
            menu.onSlot(SLOT_STATUS) { p, _, _ -> announceStatus(p.level().server) }

            menu
        }

        player.openMenu(SimpleMenuProvider(provider, Announcer.colorize("&4&l☠ &c&lHardDeath &8» &7Panel de Admin")))
    }

    private fun cycleMobcap(player: ServerPlayer, inv: SimpleContainer, category: MobCategory, button: Int) {
        val current = HardModConfig.mobcapMultiplierFor(category.serializedName)
        val idx = MOBCAP_STEPS.indexOf(current).let { if (it < 0) 0 else it }
        val next = if (button == 1) (idx - 1 + MOBCAP_STEPS.size) % MOBCAP_STEPS.size else (idx + 1) % MOBCAP_STEPS.size
        HardModConfig.setMobcapMultiplier(category.serializedName, MOBCAP_STEPS[next])
        refreshItems(inv)
    }

    private fun announceStatus(server: MinecraftServer) {
        val lines = listOf(
            "&7Mesa de encantamientos: ${if (HardModConfig.enchantTableLocked) "&cbloqueada" else "&adisponible"}",
            "&7Aldeanos: ${if (HardModConfig.blockVillagers) "&celiminados" else "&aactivos"}",
            "&7Tridente: &f${tridentModeLabel(HardModConfig.tridentMode)}",
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
    }

    private fun tridentModeLabel(mode: TridentMode): String = when (mode) {
        TridentMode.OFF -> "mesa sin restriccion"
        TridentMode.BLOCK_ALL -> "mesa bloqueada"
        TridentMode.MAX_UNBREAKING_III -> "mesa solo Unbreaking III maximo; yunque libre"
    }

    private fun refreshItems(inv: SimpleContainer) {
        GuiItems.fillBackground(inv, 6, OCCUPIED_SLOTS)

        inv.setItem(SLOT_ENCHANT_TABLE, GuiItems.stack(
            Items.ENCHANTING_TABLE, "&c&lMesa de Encantamientos",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.enchantTableLocked) "&c&lBLOQUEADA" else "&a&lDISPONIBLE"}",
                "&7Si esta bloqueada, nadie puede",
                "&7usar la mesa hasta que la abras.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_VILLAGERS, GuiItems.stack(
            Items.VILLAGER_SPAWN_EGG, "&c&lAldeanos",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.blockVillagers) "&c&lELIMINADOS" else "&a&lACTIVOS"}",
                "&7Si estan eliminados, no aparecen",
                "&7aldeanos nuevos en ninguna aldea.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_TRIDENT, GuiItems.stack(
            Items.TRIDENT, "&c&lEncantamientos de Tridente",
            listOf(
                "&8▪ &7Modo actual: &f${tridentModeLabel(HardModConfig.tridentMode)}",
                "&7La restriccion solo afecta la mesa.",
                "&7El yunque permite Riptide y los demas.",
                "",
                "&e▶ Click izq/der para ciclar"
            )
        ))
        inv.setItem(SLOT_BURN, GuiItems.stack(
            Items.FLINT_AND_STEEL, "&c&lQuemadura Permanente",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.permanentBurn) "&4&lACTIVADA" else "&a&lAPAGADA"}",
                "&7Si esta activada, el fuego de los",
                "&7jugadores no se apaga solo --",
                "&7solo se apaga tocando agua.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_NETHER, GuiItems.stack(
            Items.OBSIDIAN, "&c&lNether",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.netherLocked) "&c&lSELLADO" else "&a&lDISPONIBLE"}",
                "&7Si esta sellado, el mechero no",
                "&7enciende el portal sobre obsidiana.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_END, GuiItems.stack(
            Items.ENDER_EYE, "&c&lEnd",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.endLocked) "&c&lSELLADO" else "&a&lDISPONIBLE"}",
                "&7Si esta sellado, el ojo de ender",
                "&7no hace nada en el marco de portal.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_TOTEM, GuiItems.stack(
            Items.TOTEM_OF_UNDYING, "&c&lTotems",
            listOf(
                "&8▪ &7Probabilidad: &f${HardModConfig.totemActivationChance}%",
                "&70% los desactiva por completo;",
                "&7100% conserva el comportamiento vanilla.",
                "",
                "&e▶ Click izq/der para subir/bajar"
            )
        ))
        inv.setItem(SLOT_RAIDS, GuiItems.stack(
            Items.OMINOUS_BOTTLE, "&c&lRaids",
            listOf(
                "&8▪ &7Estado: ${if (HardModConfig.raidsEnabled) "&a&lACTIVADAS" else "&c&lDESACTIVADAS"}",
                "&7Al desactivarlas se detienen las",
                "&7raids activas y no comienzan nuevas.",
                "",
                "&e▶ Click para alternar"
            )
        ))
        inv.setItem(SLOT_MOBCAP_MONSTER, GuiItems.stack(
            Items.ZOMBIE_SPAWN_EGG, "&c&lMobcap: Monster",
            listOf(
                "&8▪ &7Multiplicador: &fx${HardModConfig.mobcapMultiplierFor(MobCategory.MONSTER.serializedName)}",
                "&7Sube el limite de mobs hostiles",
                "&7que pueden existir a la vez.",
                "",
                "&e▶ Click izq/der para subir/bajar"
            )
        ))
        inv.setItem(SLOT_MOBCAP_CREATURE, GuiItems.stack(
            Items.COW_SPAWN_EGG, "&c&lMobcap: Creature",
            listOf(
                "&8▪ &7Multiplicador: &fx${HardModConfig.mobcapMultiplierFor(MobCategory.CREATURE.serializedName)}",
                "&7Sube el limite de mobs pasivos",
                "&7que pueden existir a la vez.",
                "",
                "&e▶ Click izq/der para subir/bajar"
            )
        ))
        inv.setItem(SLOT_REWARDS_NORMAL, GuiItems.stack(
            Items.TRIAL_KEY, "&c&lRecompensas: Llave de Desafio",
            listOf(
                "&8▪ &7Editor de recompensas de vaults",
                "&7abiertos con llave normal.",
                "",
                "&e▶ Click para editar"
            )
        ))
        inv.setItem(SLOT_REWARDS_OMINOUS, GuiItems.stack(
            Items.OMINOUS_TRIAL_KEY, "&c&lRecompensas: Llave Ominosa",
            listOf(
                "&8▪ &7Editor de recompensas de vaults",
                "&7abiertos con llave ominosa.",
                "",
                "&e▶ Click para editar"
            )
        ))
        inv.setItem(SLOT_STATUS, GuiItems.stack(
            Items.BELL, "&c&lRecordar Estado Actual",
            listOf(
                "&7Anuncia en el chat, para todos,",
                "&7el estado de todos los ajustes.",
                "",
                "&e▶ Click para anunciar"
            )
        ))
    }
}
