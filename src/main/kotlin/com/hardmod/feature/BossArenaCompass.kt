package com.hardmod.feature

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Brujula hacia el boss de una arena de hard-death-mobs-mod -- SIN tocar
 * ese mod para nada. Lee directamente los archivos que ese mod ya escribe
 * en disco (config/harddeathmobs/arenas/, archivos .json, solo lectura) para saber
 * el "setpos" (x,y,z), horarios y estado de cada arena. Desde 30 minutos
 * antes de un horario pendiente muestra el punto donde aparecera el boss.
 * Al iniciar la pelea la detecta observando el mundo cerca de esa posicion:
 * si aparece algun ser vivo con
 * nombre personalizado visible (asi es como ese mod marca a sus bosses y
 * minions, y a nadie mas), la arena arranco.
 *
 * Una vez detectada, deja de importar la distancia al setpos -- se seguie
 * a esas entidades puntuales por su UUID (el boss se puede alejar del
 * spawn, teletransportarse, etc. durante la pelea) y la brujula se
 * actualiza con su posicion EN VIVO. Solo se oculta cuando esas entidades
 * de verdad mueren/desaparecen, nunca por alejarse.
 *
 * Costo: el escaneo de entidades es sobre chunks YA cargados (si el chunk
 * de la arena no esta cargado -- ej. nadie cerca y la pelea no empezo --
 * la busqueda no encuentra nada que iterar, practicamente gratis) y corre
 * cada [DETECTION_INTERVAL_TICKS] (no cada tick); detectar el disparo con
 * 3 segundos de margen es imperceptible para el jugador. La lista de
 * arenas se lee del disco al arrancar el servidor. Un admin puede forzar una
 * recarga inmediata con `/hardmod arenas reload`.
 */
object BossArenaCompass {

    private val LOGGER = LoggerFactory.getLogger("hardmod")
    private val gson = Gson()

    private const val DETECTION_RADIUS = 40.0
    private const val DETECTION_INTERVAL_TICKS = 60
    private const val PREVIEW_MINUTES = 30L

    private data class ScheduledTime(val raw: String, val time: LocalTime)
    private data class ArenaInfo(
        val id: String,
        val bossId: String,
        val pos: Vec3,
        val triggerTimes: List<ScheduledTime>,
        val enabled: Boolean,
        val firedTimesToday: Set<String>,
        val lastActiveDate: LocalDate?
    )
    private class TrackedFight(val bossUuids: MutableSet<UUID>)

    private val zone = ZoneId.systemDefault()
    private var arenas: List<ArenaInfo> = emptyList()
    private val tracked: MutableMap<String, TrackedFight> = mutableMapOf()
    private var ticksSinceDetect = 0

    fun register() {
        ServerLifecycleEvents.SERVER_STARTING.register { reload() }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            BossCompassSidebar.tickIfDue(server)

            ticksSinceDetect++
            if (ticksSinceDetect >= DETECTION_INTERVAL_TICKS) {
                ticksSinceDetect = 0
                tick(server)
            }
        }
    }

    /** Usado por `/hardmod arenas reload` -- devuelve cuantas arenas quedaron cargadas. */
    fun reload(): Int {
        val dir = FabricLoader.getInstance().configDir.resolve("harddeathmobs").resolve("arenas")
        if (!Files.isDirectory(dir)) {
            arenas.forEach { BossCompassSidebar.hide(it.id) }
            arenas = emptyList()
            tracked.clear()
            return 0
        }
        val loaded = mutableListOf<ArenaInfo>()
        Files.newDirectoryStream(dir, "*.json").use { stream ->
            for (path in stream) {
                try {
                    val json = Files.newBufferedReader(path).use { gson.fromJson(it, JsonObject::class.java) }
                    val id = path.fileName.toString().removeSuffix(".json")
                    val bossId = json.get("bossId")?.asString ?: id
                    val x = json.get("x")?.asDouble ?: continue
                    val y = json.get("y")?.asDouble ?: continue
                    val z = json.get("z")?.asDouble ?: continue
                    val triggerTimes = json.getAsJsonArray("triggerTimes")
                        ?.mapNotNull { element ->
                            val raw = element.asString
                            runCatching { ScheduledTime(raw, LocalTime.parse(raw)) }.getOrNull()
                        }
                        ?: emptyList()
                    val firedTimesToday = json.getAsJsonArray("firedTimesToday")
                        ?.map { it.asString }
                        ?.toSet()
                        ?: emptySet()
                    val lastActiveDate = json.get("lastActiveDate")?.asString
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    loaded.add(
                        ArenaInfo(
                            id = id,
                            bossId = bossId,
                            pos = Vec3(x, y, z),
                            triggerTimes = triggerTimes,
                            enabled = json.get("enabled")?.asBoolean ?: false,
                            firedTimesToday = firedTimesToday,
                            lastActiveDate = lastActiveDate
                        )
                    )
                } catch (e: Exception) {
                    LOGGER.warn("[hardmod] No se pudo leer la arena de boss '{}' para la brujula", path, e)
                }
            }
        }
        val removedIds = arenas.map { it.id }.toSet() - loaded.map { it.id }.toSet()
        for (id in removedIds) {
            tracked.remove(id)
            BossCompassSidebar.hide(id)
        }
        arenas = loaded
        LOGGER.info("[hardmod] Brujula de boss: {} arena(s) detectadas para observar", arenas.size)
        return arenas.size
    }

    private fun tick(server: MinecraftServer) {
        if (arenas.isEmpty()) return
        val level = server.overworld()

        for (arena in arenas) {
            val fight = tracked[arena.id]
            val label = arena.bossId.replaceFirstChar { it.uppercase() }

            if (fight == null) {
                val found = findQualifyingEntities(level, arena.pos)
                if (found.isNotEmpty()) {
                    tracked[arena.id] = TrackedFight(found.map { it.uuid }.toMutableSet())
                    val target = found.first()
                    BossCompassSidebar.showActive(arena.id, label, target.position(), arena.pos, healthPercentOf(target))
                } else {
                    val nextSpawn = nextUpcomingSpawn(arena, System.currentTimeMillis())
                    if (nextSpawn != null) {
                        BossCompassSidebar.showUpcoming(arena.id, label, arena.pos, nextSpawn)
                    } else {
                        BossCompassSidebar.hide(arena.id)
                    }
                }
                continue
            }

            val alive = fight.bossUuids.mapNotNull { uuid -> level.getEntity(uuid) as? LivingEntity }
                .filter { it.isAlive }
            if (alive.isEmpty()) {
                tracked.remove(arena.id)
                BossCompassSidebar.hide(arena.id)
            } else {
                val target = alive.first()
                BossCompassSidebar.showActive(arena.id, label, target.position(), arena.pos, healthPercentOf(target))
            }
        }
    }

    private fun nextUpcomingSpawn(arena: ArenaInfo, nowMillis: Long): Long? {
        if (!arena.enabled) return null

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val fired = if (arena.lastActiveDate == now.toLocalDate()) arena.firedTimesToday else emptySet()
        val previewMillis = PREVIEW_MINUTES * 60_000L

        return arena.triggerTimes.asSequence()
            .filter { it.raw !in fired }
            .map { scheduled ->
                LocalDateTime.of(now.toLocalDate(), scheduled.time)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }
            .filter { spawnAt -> spawnAt >= nowMillis && spawnAt - nowMillis <= previewMillis }
            .minOrNull()
    }

    private fun healthPercentOf(entity: LivingEntity): Int =
        if (entity.maxHealth <= 0f) 0 else ((entity.health / entity.maxHealth) * 100f).toInt().coerceIn(0, 100)

    private fun findQualifyingEntities(level: net.minecraft.server.level.ServerLevel, pos: Vec3): List<LivingEntity> {
        val aabb = AABB(
            pos.x - DETECTION_RADIUS, pos.y - DETECTION_RADIUS, pos.z - DETECTION_RADIUS,
            pos.x + DETECTION_RADIUS, pos.y + DETECTION_RADIUS, pos.z + DETECTION_RADIUS
        )
        return level.getEntitiesOfClass(LivingEntity::class.java, aabb) { entity ->
            entity !is Player && entity.isAlive && entity.isCustomNameVisible && entity.customName != null
        }
    }
}
