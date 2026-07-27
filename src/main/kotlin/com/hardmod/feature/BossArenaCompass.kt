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

/**
 * Brujula hacia el boss de una arena de hard-death-mobs-mod -- SIN tocar
 * ese mod para nada. Lee directamente los archivos que ese mod ya escribe
 * en disco (config/harddeathmobs/arenas/, archivos .json, solo lectura) para saber
 * el "setpos" (x,y,z) de cada arena, y detecta que una pelea esta en
 * curso observando el mundo: si hay algun ser vivo con nombre
 * personalizado visible cerca de esa posicion (asi es como ese mod marca
 * a sus bosses y minions, y a nadie mas), la arena esta activa y se
 * muestra la brujula (ver BossCompassSidebar); si no queda ninguno, la
 * pelea termino y se oculta.
 *
 * Costo: el escaneo de entidades es sobre chunks YA cargados (si el chunk
 * de la arena no esta cargado -- ej. nadie cerca y la pelea no empezo --
 * la busqueda no encuentra nada que iterar, practicamente gratis) y corre
 * cada [DETECTION_INTERVAL_TICKS] (no cada tick); detectar el disparo con
 * 3 segundos de margen es imperceptible para el jugador. La lista de
 * arenas se lee del disco al arrancar el server nada mas -- si se hace un
 * `/hdm arena <id> setpos` con el server prendido, hay que avisarle a
 * hardmod con `/hardmod arenas reload` (peor caso: la brujula apunta al
 * setpos viejo hasta que alguien corra ese comando o se reinicie).
 */
object BossArenaCompass {

    private val LOGGER = LoggerFactory.getLogger("hardmod")
    private val gson = Gson()

    private const val DETECTION_RADIUS = 40.0
    private const val DETECTION_INTERVAL_TICKS = 60

    private data class ArenaInfo(val id: String, val bossId: String, val pos: Vec3)

    private var arenas: List<ArenaInfo> = emptyList()
    private val activeArenaIds: MutableSet<String> = mutableSetOf()
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
            arenas = emptyList()
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
                    loaded.add(ArenaInfo(id, bossId, Vec3(x, y, z)))
                } catch (e: Exception) {
                    LOGGER.warn("[hardmod] No se pudo leer la arena de boss '{}' para la brujula", path, e)
                }
            }
        }
        arenas = loaded
        LOGGER.info("[hardmod] Brujula de boss: {} arena(s) detectadas para observar", arenas.size)
        return arenas.size
    }

    private fun tick(server: MinecraftServer) {
        if (arenas.isEmpty()) return
        val level = server.overworld()

        for (arena in arenas) {
            val aabb = AABB(
                arena.pos.x - DETECTION_RADIUS, arena.pos.y - DETECTION_RADIUS, arena.pos.z - DETECTION_RADIUS,
                arena.pos.x + DETECTION_RADIUS, arena.pos.y + DETECTION_RADIUS, arena.pos.z + DETECTION_RADIUS
            )
            val hasBoss = level.getEntitiesOfClass(LivingEntity::class.java, aabb) { entity ->
                entity !is Player && entity.isAlive && entity.isCustomNameVisible && entity.customName != null
            }.isNotEmpty()

            if (hasBoss) {
                if (activeArenaIds.add(arena.id)) {
                    BossCompassSidebar.show(arena.id, arena.bossId.replaceFirstChar { it.uppercase() }, arena.pos)
                }
            } else {
                if (activeArenaIds.remove(arena.id)) {
                    BossCompassSidebar.hide(arena.id)
                }
            }
        }
    }
}
