package com.hardmod.feature

import com.hardmod.announce.Announcer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * PVP apagado por defecto. Un trigger aleatorio lo activa una vez al dia,
 * en un instante al azar entre las 19:00 y las 19:30 (hora local del
 * servidor), por una duracion aleatoria de 15 a 90 minutos. Al terminar
 * exige minimo 1 hora de enfriamiento antes de poder dispararse de nuevo.
 * Mientras dura la sesion activa se muestra una bossbar de cuenta
 * regresiva, desde el momento en que se activa hasta que termina.
 */
object PvpScheduler {

    private const val WINDOW_START_HOUR = 19
    private const val WINDOW_LENGTH_MINUTES = 30L
    const val MIN_DURATION_MINUTES = 15L
    const val MAX_DURATION_MINUTES = 90L
    private const val COOLDOWN_MINUTES = 60L
    private const val CHECK_INTERVAL_TICKS = 20

    private val zone = ZoneId.systemDefault()

    @Volatile var active: Boolean = false
        private set

    private var activeUntilEpochMillis: Long = 0
    private var activeDurationMillis: Long = 0
    private var cooldownUntilEpochMillis: Long = 0
    private var scheduledTriggerEpochMillis: Long? = null
    private var scheduledForDate: LocalDate? = null

    private var ticksSinceCheck = 0

    private val bossBar = ServerBossEvent(
        UUID.randomUUID(),
        Announcer.colorize("&c&l⚔ PVP activo"),
        BossEvent.BossBarColor.RED,
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

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            val victim = entity as? ServerPlayer ?: return@register true
            val attacker = source.entity as? ServerPlayer ?: return@register true
            if (attacker === victim) return@register true
            if (active) return@register true
            attacker.sendSystemMessage(Announcer.colorize("&7⚔ El PVP esta desactivado por ahora."), true)
            false
        }
    }

    /** Activa PVP manualmente (comando de admin), ignorando el cooldown. */
    fun activateManual(durationMinutes: Long?): Long {
        val minutes = durationMinutes ?: Random.nextLong(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES + 1)
        activate(System.currentTimeMillis(), minutes * 60_000L)
        return minutes
    }

    /** Cancela PVP manualmente (comando de admin) y arranca el cooldown normal. */
    fun deactivateManual() {
        deactivate(System.currentTimeMillis())
    }

    fun statusLabel(): String {
        if (!active) return "&cinactivo"
        val remainingMinutes = ((activeUntilEpochMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        return "&aactivo &7(quedan ${remainingMinutes}m)"
    }

    private fun tick(server: MinecraftServer) {
        val now = System.currentTimeMillis()
        ensureScheduledTrigger(now)

        if (active) {
            val remainingMillis = activeUntilEpochMillis - now
            if (remainingMillis <= 0) {
                deactivate(now)
            } else {
                updateBossBar(server, remainingMillis)
            }
            return
        }

        val trigger = scheduledTriggerEpochMillis ?: return
        if (now >= trigger) {
            if (now >= cooldownUntilEpochMillis) {
                val durationMinutes = Random.nextLong(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES + 1)
                activate(now, durationMinutes * 60_000L)
            } else {
                // No alcanzo el cooldown a tiempo para la ventana de hoy --
                // se descarta, no se reintenta hasta mañana.
                scheduledTriggerEpochMillis = null
            }
        }
    }

    private fun ensureScheduledTrigger(nowMillis: Long) {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val today = now.toLocalDate()
        if (scheduledForDate == today) return

        val windowStart = LocalDateTime.of(today, LocalTime.of(WINDOW_START_HOUR, 0))
        if (now.isBefore(windowStart)) return

        val windowEnd = windowStart.plusMinutes(WINDOW_LENGTH_MINUTES)
        scheduledForDate = today
        if (!now.isBefore(windowEnd)) {
            // La ventana de hoy ya paso (ej. el server no estaba corriendo a
            // esa hora) -- no se dispara con retraso, se descarta hasta mañana.
            scheduledTriggerEpochMillis = null
            return
        }

        // Punto al azar entre "ahora" y el cierre de la ventana, nunca en el
        // pasado -- si arrancas a las 19:20 el disparo cae entre 19:20 y 19:30.
        val remainingMinutes = java.time.Duration.between(now, windowEnd).toMinutes().coerceAtLeast(1)
        val offsetMinutes = Random.nextLong(0, remainingMinutes)
        val triggerAt = now.plusMinutes(offsetMinutes)
        scheduledTriggerEpochMillis = triggerAt.atZone(zone).toInstant().toEpochMilli()
    }

    private fun activate(nowMillis: Long, durationMillis: Long) {
        active = true
        activeUntilEpochMillis = nowMillis + durationMillis
        activeDurationMillis = durationMillis
    }

    private fun deactivate(nowMillis: Long) {
        active = false
        cooldownUntilEpochMillis = nowMillis + COOLDOWN_MINUTES * 60_000L
        scheduledTriggerEpochMillis = null
        hideBossBar()
    }

    private fun updateBossBar(server: MinecraftServer, remainingMillis: Long) {
        val totalSeconds = remainingMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        bossBar.setName(Announcer.colorize("&c&l⚔ PVP activo &7- termina en &f%02d:%02d".format(minutes, seconds)))
        val total = activeDurationMillis.coerceAtLeast(1)
        bossBar.setProgress((remainingMillis.toDouble() / total).toFloat().coerceIn(0f, 1f))
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
