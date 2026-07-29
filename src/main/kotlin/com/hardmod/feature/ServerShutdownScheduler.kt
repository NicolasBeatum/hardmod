package com.hardmod.feature

import com.hardmod.announce.Announcer
import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.world.BossEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Cierre diario del servidor por defecto a las 02:00 (hora local de la maquina). La
 * ultimas dos horas antes del cierre se avisan con una bossbar de cuenta
 * regresiva. /hardmod server extend <minutos> corre el cierre hacia
 * adelante (ej. si cerraba a las 02:00 y se extiende 30m, pasa a cerrar
 * a las 02:30). La hora de cierre diaria se configura con /hardmod server time <hora> [minuto].
 */
object ServerShutdownScheduler {

    private const val WARNING_MINUTES = 120L
    private const val CHECK_INTERVAL_TICKS = 20

    private val zone = ZoneId.systemDefault()

    private var targetEpochMillis: Long = 0
    private var ticksSinceCheck = 0
    private var stopping = false

    private val bossBar = ServerBossEvent(
        UUID.randomUUID(),
        Announcer.colorize("&6&l⏳ Cierre del servidor"),
        BossEvent.BossBarColor.YELLOW,
        BossEvent.BossBarOverlay.PROGRESS
    ).also { it.setVisible(false) }

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticksSinceCheck++
            if (ticksSinceCheck >= CHECK_INTERVAL_TICKS) {
                ticksSinceCheck = 0
                tick(server)
            }
        }
    }

    /** Reinicia el objetivo de cierre para que se recalcule con la configuracion actual de hora/minuto. */
    fun resetTarget() {
        targetEpochMillis = 0L
    }

    /** Suma minutos al cierre programado. Devuelve la nueva hora de cierre ya formateada, para el feedback del admin. */
    fun extend(minutes: Int): String {
        ensureTarget(System.currentTimeMillis())
        targetEpochMillis += minutes * 60_000L
        return targetLabel()
    }

    fun targetLabel(): String {
        ensureTarget(System.currentTimeMillis())
        val target = LocalDateTime.ofInstant(Instant.ofEpochMilli(targetEpochMillis), zone)
        return "%02d:%02d".format(target.hour, target.minute)
    }

    private fun ensureTarget(nowMillis: Long) {
        if (targetEpochMillis != 0L) return
        targetEpochMillis = nextShutdownTarget(nowMillis)
    }

    private fun nextShutdownTarget(nowMillis: Long): Long {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(HardModConfig.shutdownHour, HardModConfig.shutdownMinute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(zone).toInstant().toEpochMilli()
    }

    private fun tick(server: MinecraftServer) {
        if (stopping) return
        val now = System.currentTimeMillis()
        ensureTarget(now)
        val remaining = targetEpochMillis - now

        if (remaining <= 0) {
            stopping = true
            hideBossBar()
            server.halt(false)
            return
        }

        if (remaining <= WARNING_MINUTES * 60_000L) {
            updateBossBar(server, remaining)
        }
    }

    private fun updateBossBar(server: MinecraftServer, remainingMillis: Long) {
        val totalSeconds = remainingMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val label = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
        bossBar.setName(Announcer.colorize("&6&l⏳ El servidor cierra en &f$label"))
        bossBar.setProgress((remainingMillis.toDouble() / (WARNING_MINUTES * 60_000L)).toFloat().coerceIn(0f, 1f))
        bossBar.setVisible(true)

        val online = server.playerList.players.toSet()
        for (player in bossBar.players.toList()) {
            if (player !in online) bossBar.removePlayer(player)
        }
        for (player in online) {
            if (player !in bossBar.players) bossBar.addPlayer(player)
        }
    }

    private fun hideBossBar() {
        bossBar.setVisible(false)
        bossBar.removeAllPlayers()
    }
}
