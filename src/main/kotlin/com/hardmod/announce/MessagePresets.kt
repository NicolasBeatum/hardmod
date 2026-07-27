package com.hardmod.announce

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Un preset: el mensaje que se anuncia + comandos opcionales que el servidor ejecuta al dispararlo (con permiso de consola, ej. gamerule/difficulty/dar efectos). */
data class MessagePreset(val message: String, val commands: List<String> = emptyList())

/**
 * Presets de anuncio editables a mano en config/hardmod/presets.json --
 * pensados para cambios que este mod NO gestiona (mobs mas fuertes, una
 * mecanica nueva de otro mod, un cambio de dificultad puntual, etc.), sin
 * tener que escribir el mensaje completo cada vez. Ademas del mensaje,
 * cada preset puede traer una lista de comandos que el servidor ejecuta
 * al dispararlo -- asi un mismo preset puede avisar Y aplicar el cambio
 * (ej. subir la dificultad, activar mobs con /summon, etc), segun como lo
 * configure el admin. Trae ejemplos por defecto la primera vez que arranca.
 */
object MessagePresets {

    private val LOGGER = LoggerFactory.getLogger("hardmod")
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private var presets: Map<String, MessagePreset> = emptyMap()

    private fun configFile(): Path =
        FabricLoader.getInstance().configDir.resolve("hardmod").resolve("presets.json")

    fun all(): Map<String, MessagePreset> = presets

    fun get(name: String): MessagePreset? = presets[name]

    fun reload() {
        val file = configFile()
        writeDefaultIfMissing(file)
        presets = try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                val obj = json.getAsJsonObject("presets") ?: return@use emptyMap()
                obj.keySet().associateWith { key -> parsePreset(obj.get(key)) }
            }
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudieron leer los presets de {}: {}", file, e.toString())
            emptyMap()
        }
        LOGGER.info("[hardmod] Presets de anuncio cargados: {}", presets.keys)
    }

    /** Soporta el formato viejo (valor = string plano, sin comandos) para no romper presets.json ya existentes. */
    private fun parsePreset(element: com.google.gson.JsonElement): MessagePreset {
        if (element.isJsonPrimitive) return MessagePreset(element.asString)
        val obj = element.asJsonObject
        val message = obj.get("message")?.asString ?: ""
        val commands = if (obj.has("commands")) obj.getAsJsonArray("commands").map { it.asString } else emptyList()
        return MessagePreset(message, commands)
    }

    private fun writeDefaultIfMissing(file: Path) {
        if (Files.exists(file)) return
        try {
            Files.createDirectories(file.parent)
            val json = JsonObject()
            json.addProperty(
                "_comment",
                "Presets de anuncio para /hardmod announce preset <nombre>. 'message' soporta colores estilo '&' " +
                    "(ej. &c=rojo, &a=verde, &e=amarillo, &l=negrita, &r=reset). 'commands' es opcional -- lista de " +
                    "comandos de servidor (sin '/') que se ejecutan con permiso de consola al disparar el preset, " +
                    "ej. cambiar dificultad, gamerules, o /summon un mob. Pensado para cambios que este mod no " +
                    "controla directo (mobs mas fuertes, mecanicas de otro mod, etc). Edita/agrega libremente."
            )
            val presetsJson = JsonObject()
            presetsJson.add("mobs_mas_fuertes", presetJson("&c⚔ Los mobs de esta partida ahora son mas fuertes de lo normal. &7¡Cuidado!", emptyList()))
            presetsJson.add("nueva_mecanica", presetJson("&d✦ Se activo una nueva mecanica especial en el servidor. &7Revisa el anuncio fijado para detalles.", emptyList()))
            presetsJson.add(
                "modo_dificil",
                presetJson(
                    "&4☠ La dificultad del servidor acaba de subir a &c&lHARD&4. &7¡Andate con cuidado!",
                    listOf("difficulty hard")
                )
            )
            json.add("presets", presetsJson)
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer -> GSON.toJson(json, writer) }
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudieron crear los presets por defecto en {}", file, e)
        }
    }

    private fun presetJson(message: String, commands: List<String>): JsonObject {
        val o = JsonObject()
        o.addProperty("message", message)
        if (commands.isNotEmpty()) {
            val arr = JsonArray()
            commands.forEach { arr.add(it) }
            o.add("commands", arr)
        }
        return o
    }
}
