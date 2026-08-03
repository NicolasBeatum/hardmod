package com.hardmod.feature

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.Optional
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Sidebar tipo "brujula" hacia un boss detectado en el mundo (ver
 * BossArenaCompass, que la alimenta puramente observando el mundo -- este
 * objeto solo sabe mandar/actualizar/sacar la sidebar, no sabe nada de
 * arenas). Cada jugador ve una flecha DISTINTA, relativa a hacia donde
 * esta mirando el (no un rumbo fijo), mas la distancia -- por eso no se usa
 * el Scoreboard vanilla compartido (que manda el mismo texto a todos): se
 * arma un Objective "suelto" (nunca registrado en `server.scoreboard`) y se
 * le mandan los paquetes de scoreboard a mano, personalizados, a cada
 * jugador conectado.
 */
object BossCompassSidebar {

    private const val OBJECTIVE_NAME = "hardmod_boss_compass"
    private const val UPDATE_INTERVAL_TICKS = 10

    data class CompassTarget(
        val label: String,
        val pos: Vec3,
        val arenaPos: Vec3,
        val healthPercent: Int?,
        val spawnAtEpochMillis: Long?,
        val dimensionId: String?
    )

    data class SidebarNotice(
        val label: String,
        val endsAtEpochMillis: Long
    )

    private val targets: MutableMap<String, CompassTarget> = mutableMapOf()
    private val notices: MutableMap<String, SidebarNotice> = mutableMapOf()
    private val removedTargetKeys: MutableSet<String> = mutableSetOf()
    private val playersShown: MutableSet<UUID> = mutableSetOf()
    private var ticksSinceUpdate = 0

    /** Nunca se registra en un Scoreboard real -- el constructor solo pide una instancia para la referencia interna. */
    private val objective = Objective(
        Scoreboard(),
        OBJECTIVE_NAME,
        ObjectiveCriteria.DUMMY,
        Component.literal("✦ Eventos Activos"),
        ObjectiveCriteria.RenderType.INTEGER,
        false,
        null
    )

    fun tickIfDue(server: MinecraftServer) {
        ticksSinceUpdate++
        if (ticksSinceUpdate >= UPDATE_INTERVAL_TICKS) {
            ticksSinceUpdate = 0
            tick(server)
        }
    }

    fun showActive(
        key: String,
        label: String,
        pos: Vec3,
        arenaPos: Vec3,
        healthPercent: Int,
        dimensionId: String
    ) {
        targets[key] = CompassTarget(label, pos, arenaPos, healthPercent, null, dimensionId)
    }

    fun showUpcoming(
        key: String,
        label: String,
        arenaPos: Vec3,
        spawnAtEpochMillis: Long,
        dimensionId: String
    ) {
        targets[key] = CompassTarget(label, arenaPos, arenaPos, null, spawnAtEpochMillis, dimensionId)
    }

    fun showNotice(key: String, label: String, endsAtEpochMillis: Long) {
        notices[key] = SidebarNotice(label, endsAtEpochMillis)
    }

    fun hide(key: String) {
        val removedTarget = targets.remove(key) != null
        val removedNotice = notices.remove(key) != null
        if (removedTarget || removedNotice) {
            removedTargetKeys.add(key)
        }
    }

    private fun tick(server: MinecraftServer) {
        val online = server.playerList.players

        if (targets.isEmpty() && notices.isEmpty()) {
            if (playersShown.isNotEmpty()) {
                for (player in online) {
                    if (player.uuid in playersShown) removeFor(player)
                }
                playersShown.clear()
            }
            removedTargetKeys.clear()
            return
        }

        for (player in online) {
            if (playersShown.add(player.uuid)) {
                addFor(player)
            }
            for (key in removedTargetKeys) {
                resetTargetLines(player, key)
            }
            updateLinesFor(player)
        }
        removedTargetKeys.clear()
        playersShown.retainAll(online.map { it.uuid }.toSet())
    }

    private fun addFor(player: ServerPlayer) {
        player.connection.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD))
        player.connection.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective))
    }

    private fun removeFor(player: ServerPlayer) {
        player.connection.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
    }

    private fun resetTargetLines(player: ServerPlayer, key: String) {
        player.connection.send(ClientboundResetScorePacket("hardmod_${key}_coords", OBJECTIVE_NAME))
        player.connection.send(ClientboundResetScorePacket("hardmod_${key}_dir", OBJECTIVE_NAME))
        player.connection.send(ClientboundResetScorePacket("hardmod_${key}_hp", OBJECTIVE_NAME))
        player.connection.send(ClientboundResetScorePacket("hardmod_${key}_notice", OBJECTIVE_NAME))
        player.connection.send(ClientboundResetScorePacket("hardmod_${key}_timer", OBJECTIVE_NAME))
    }

    private fun updateLinesFor(player: ServerPlayer) {
        val blank: Optional<NumberFormat> = Optional.of(FixedFormat(Component.empty()))
        val entries = targets.entries.toList()
        val noticeEntries = notices.entries.toList()
        var score = entries.size * 3 + noticeEntries.size * 2
        for ((key, target) in entries) {
            player.connection.send(
                ClientboundSetScorePacket(
                    "hardmod_${key}_coords", OBJECTIVE_NAME, score--,
                    Optional.of(buildCoordsLine(target)), blank
                )
            )
            player.connection.send(
                ClientboundSetScorePacket(
                    "hardmod_${key}_dir", OBJECTIVE_NAME, score--,
                    Optional.of(buildDirectionLine(player, target)), blank
                )
            )
            player.connection.send(
                ClientboundSetScorePacket(
                    "hardmod_${key}_hp", OBJECTIVE_NAME, score--,
                    Optional.of(buildStatusLine(target)), blank
                )
            )
        }
        for ((key, notice) in noticeEntries) {
            player.connection.send(
                ClientboundSetScorePacket(
                    "hardmod_${key}_notice", OBJECTIVE_NAME, score--,
                    Optional.of(Component.literal(notice.label).withStyle(ChatFormatting.LIGHT_PURPLE)), blank
                )
            )
            player.connection.send(
                ClientboundSetScorePacket(
                    "hardmod_${key}_timer", OBJECTIVE_NAME, score--,
                    Optional.of(buildNoticeTimerLine(notice)), blank
                )
            )
        }
    }

    private fun buildCoordsLine(target: CompassTarget): Component {
        return Component.literal(
            "X:${target.arenaPos.x.toInt()} Y:${target.arenaPos.y.toInt()} Z:${target.arenaPos.z.toInt()}"
        )
            .withStyle(ChatFormatting.DARK_GRAY)
    }

    private fun buildDirectionLine(player: ServerPlayer, target: CompassTarget): Component {
        val playerDimension = player.level().dimension().identifier().toString()
        if (target.dimensionId != null && target.dimensionId != playerDimension) {
            return Component.literal("◈ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(target.label).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" otra dimension").withStyle(ChatFormatting.GRAY))
        }

        val dx = target.pos.x - player.x
        val dz = target.pos.z - player.z
        val distance = sqrt(dx * dx + dz * dz).toInt()
        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        val relative = normalizeAngle(targetYaw - player.yRot)
        val arrow = arrowFor(relative)
        return Component.literal("$arrow ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(target.label).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ${distance}m").withStyle(ChatFormatting.GRAY))
    }

    private fun buildStatusLine(target: CompassTarget): Component {
        val spawnAt = target.spawnAtEpochMillis
        if (spawnAt != null) {
            val remainingSeconds = ((spawnAt - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
            val minutes = remainingSeconds / 60L
            val seconds = remainingSeconds % 60L
            return Component.literal("  ⏳ El jefe spawneará en %02d:%02d".format(minutes, seconds))
                .withStyle(ChatFormatting.GOLD)
        }

        val healthPercent = target.healthPercent ?: return Component.empty()
        val color = when {
            healthPercent <= 25 -> ChatFormatting.RED
            healthPercent <= 60 -> ChatFormatting.GOLD
            else -> ChatFormatting.GREEN
        }
        return Component.literal("  ❤ $healthPercent%").withStyle(color)
    }

    private fun buildNoticeTimerLine(notice: SidebarNotice): Component {
        val remainingSeconds = ((notice.endsAtEpochMillis - System.currentTimeMillis() + 999L) / 1000L)
            .coerceAtLeast(0L)
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L
        return Component.literal("  Desaparece en %02d:%02d".format(minutes, seconds))
            .withStyle(ChatFormatting.GOLD)
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }

    /** 8 direcciones relativas a hacia donde mira el jugador (no un rumbo N/S/E/O fijo). */
    private fun arrowFor(relativeDegrees: Double): String = when {
        relativeDegrees < -157.5 || relativeDegrees >= 157.5 -> "↓"
        relativeDegrees < -112.5 -> "↙"
        relativeDegrees < -67.5 -> "←"
        relativeDegrees < -22.5 -> "↖"
        relativeDegrees < 22.5 -> "↑"
        relativeDegrees < 67.5 -> "↗"
        relativeDegrees < 112.5 -> "→"
        else -> "↘"
    }
}
