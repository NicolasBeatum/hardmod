package com.hardmod

import com.hardmod.announce.MessagePresets
import com.hardmod.command.HardModCommands
import com.hardmod.config.HardModConfig
import com.hardmod.feature.BossArenaCompass
import com.hardmod.feature.BlackMarketSidebarTracker
import com.hardmod.feature.DimensionLock
import com.hardmod.feature.EnchantTableLock
import com.hardmod.feature.LivesEliminationAnnouncer
import com.hardmod.feature.PermanentBurn
import com.hardmod.feature.PvpScheduler
import com.hardmod.feature.RaidControl
import com.hardmod.feature.ServerShutdownScheduler
import com.hardmod.feature.trialrewards.TrialRewardConfig
import com.hardmod.feature.trialrewards.TrialRewardLootModifier
import com.hardmod.gui.LivesLeaderboardGui
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

/**
 * Entrypoint: mod 100% server-side, sin src/client. Todo lo que necesita
 * mostrarse al jugador (chat, sonidos, chest GUIs) se resuelve del lado del
 * servidor con las APIs vanilla/Fabric de siempre -- no hace falta ningun
 * codigo de cliente.
 */
object HardMod : ModInitializer {
    const val MOD_ID = "hardmod"

    override fun onInitialize() {
        EnchantTableLock.register()
        DimensionLock.register()
        PermanentBurn.register()
        PvpScheduler.register()
        RaidControl.register()
        ServerShutdownScheduler.register()
        LivesEliminationAnnouncer.register()
        BossArenaCompass.register()
        BlackMarketSidebarTracker.register()
        TrialRewardLootModifier.register()
        LivesLeaderboardGui.register()
        HardModCommands.register()

        ServerLifecycleEvents.SERVER_STARTING.register {
            HardModConfig.reload()
            MessagePresets.reload()
            TrialRewardConfig.reload()
        }
    }
}
