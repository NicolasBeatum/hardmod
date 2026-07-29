package com.hardmod.feature

import com.hardmod.announce.Announcer
import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * PVP apagado por defecto. Desde las 19:00 hasta el cierre diario del servidor,
 * cada 15 minutos hace un sorteo de 25% para activarse por una duracion
 * aleatoria de 15 a 60 minutos. Al terminar exige 30 minutos de enfriamiento
 * antes de poder participar en otro sorteo.
 * Mientras dura la sesion activa se muestra una bossbar de cuenta
 * regresiva, desde el momento en que se activa hasta que termina.
 */
object PvpScheduler {

    private const val START_HOUR = 19
    private const val ROLL_INTERVAL_MINUTES = 15L
    private const val ACTIVATION_CHANCE_PERCENT = 25
    const val MIN_DURATION_MINUTES = 15L
    const val MAX_DURATION_MINUTES = 60L
    private const val COOLDOWN_MINUTES = 30L
    private const val CHECK_INTERVAL_TICKS = 20

    private val zone = ZoneId.systemDefault()

    @Volatile var active: Boolean = false
        private set

    private var activeUntilEpochMillis: Long = 0
    private var activeDurationMillis: Long = 0
    private var cooldownUntilEpochMillis: Long = 0
    private var lastRollSlot: Long = Long.MIN_VALUE

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

        if (active) {
            val remainingMillis = activeUntilEpochMillis - now
            if (remainingMillis <= 0) {
                deactivate(now)
            } else {
                updateBossBar(server, remainingMillis)
            }
            return
        }

        val localNow = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        if (!isAutomaticPeriod(localNow.toLocalTime())) return
        if (localNow.minute % ROLL_INTERVAL_MINUTES.toInt() != 0) return

        val rollIntervalMillis = ROLL_INTERVAL_MINUTES * 60_000L
        val currentRollSlot = now / rollIntervalMillis
        if (currentRollSlot == lastRollSlot) return
        lastRollSlot = currentRollSlot

        if (now < cooldownUntilEpochMillis || Random.nextInt(100) >= ACTIVATION_CHANCE_PERCENT) return

        val durationMinutes = Random.nextLong(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES + 1)
        activate(now, durationMinutes * 60_000L)
    }

    private fun isAutomaticPeriod(now: LocalTime): Boolean {
        val start = LocalTime.of(START_HOUR, 0)
        val shutdown = LocalTime.of(HardModConfig.shutdownHour, HardModConfig.shutdownMinute)
        return if (shutdown <= start) {
            now >= start || now < shutdown
        } else {
            now >= start && now < shutdown
        }
    }

    private fun activate(nowMillis: Long, durationMillis: Long) {
        active = true
        activeUntilEpochMillis = nowMillis + durationMillis
        activeDurationMillis = durationMillis
    }

    private fun deactivate(nowMillis: Long) {
        active = false
        cooldownUntilEpochMillis = nowMillis + COOLDOWN_MINUTES * 60_000L
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
