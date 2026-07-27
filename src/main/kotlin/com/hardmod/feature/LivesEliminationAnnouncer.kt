package com.hardmod.feature

import com.hardmod.announce.Announcer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.ScoreHolder

/**
 * Anuncia en el chat, con el banner de [Announcer], cuando un jugador
 * llega a 0 en el objective de scoreboard "hdc_lives" (sistema de vidas de
 * hard-death-core-mod, sincronizado por LivesScoreboard) -- sin acoplarse
 * al codigo de ese mod, solo observando el scoreboard vanilla.
 *
 * Se dispara con el evento de muerte (no con un sondeo por tick): al morir
 * un jugador se encola su nombre y se revisa su score recien al terminar
 * ESE mismo tick, para darle tiempo al listener de hard-death-core-mod
 * (que decrementa la vida en su propio AFTER_DEATH) a correr primero --
 * el orden entre listeners de distintos mods en el mismo evento no esta
 * garantizado, pero para cuando termina el tick ya corrieron todos.
 */
object LivesEliminationAnnouncer {

    private const val OBJECTIVE_NAME = "hdc_lives"

    private val pendingChecks: MutableSet<String> = mutableSetOf()

    fun register() {
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) {
                pendingChecks.add(entity.gameProfile.name)
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pendingChecks.isEmpty()) return@register
            val objective = server.scoreboard.getObjective(OBJECTIVE_NAME)
            if (objective != null) {
                for (name in pendingChecks) {
                    val info = server.scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective)
                    if (info != null && info.value() <= 0) {
                        Announcer.broadcast(server, "&4💀 &c$name &7ha sido eliminado permanentemente.")
                    }
                }
            }
            pendingChecks.clear()
        }
    }
}
