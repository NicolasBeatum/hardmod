package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.gamerules.GameRules

/** Sincroniza el toggle persistente de raids con la gamerule vanilla. */
object RaidControl {

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register(::apply)
    }

    fun setEnabled(server: MinecraftServer, enabled: Boolean) {
        HardModConfig.setRaidsEnabled(enabled)
        apply(server)
    }

    private fun apply(server: MinecraftServer) {
        server.gameRules.set(GameRules.RAIDS, HardModConfig.raidsEnabled, server)
    }
}
