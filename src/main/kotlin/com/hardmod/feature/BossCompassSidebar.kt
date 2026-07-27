package com.hardmod.feature

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
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

    data class CompassTarget(val label: String, val pos: Vec3)

    private val targets: MutableMap<String, CompassTarget> = mutableMapOf()
    private val playersShown: MutableSet<UUID> = mutableSetOf()
    private var ticksSinceUpdate = 0

    /** Nunca se registra en un Scoreboard real -- el constructor solo pide una instancia para la referencia interna. */
    private val objective = Objective(
        Scoreboard(),
        OBJECTIVE_NAME,
        ObjectiveCriteria.DUMMY,
        Component.literal("⚔ Boss Cercano"),
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

    fun show(key: String, label: String, pos: Vec3) {
        targets[key] = CompassTarget(label, pos)
    }

    fun hide(key: String) {
        targets.remove(key)
    }

    private fun tick(server: MinecraftServer) {
        val online = server.playerList.players

        if (targets.isEmpty()) {
            if (playersShown.isNotEmpty()) {
                for (player in online) {
                    if (player.uuid in playersShown) removeFor(player)
                }
                playersShown.clear()
            }
            return
        }

        for (player in online) {
            if (playersShown.add(player.uuid)) {
                addFor(player)
            }
            updateLinesFor(player)
        }
        playersShown.retainAll(online.map { it.uuid }.toSet())
    }

    private fun addFor(player: ServerPlayer) {
        player.connection.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD))
        player.connection.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective))
    }

    private fun removeFor(player: ServerPlayer) {
        player.connection.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
    }

    private fun updateLinesFor(player: ServerPlayer) {
        val blank: Optional<NumberFormat> = Optional.of(FixedFormat(Component.empty()))
        var index = 0
        for (target in targets.values) {
            val line = "hardmod_boss_$index"
            val component: Optional<Component> = Optional.of(buildLine(player, target))
            player.connection.send(
                ClientboundSetScorePacket(line, OBJECTIVE_NAME, targets.size - index, component, blank)
            )
            index++
        }
    }

    private fun buildLine(player: ServerPlayer, target: CompassTarget): Component {
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
