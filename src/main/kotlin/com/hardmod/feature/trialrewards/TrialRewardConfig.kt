package com.hardmod.feature.trialrewards

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Una entrada de recompensa simplificada: un item, su peso relativo y un
 * rango de cantidad. El peso es decimal (no el int de vanilla) porque
 * representa la probabilidad esperada REAL de la tabla vanilla (ver
 * TrialRewardDefaultsLoader) -- redondear a entero a esta altura perdia
 * precision. Al aplicar el override de verdad (TrialRewardLootModifier)
 * se escala a enteros para el LootPool real, sin perder proporciones de
 * forma perceptible.
 *
 * [enchantOptions]: solo para libros con encantamiento aleatorio (ej. en
 * reward_rare hay un pool de encantamientos de combate y otro de
 * encantamientos acuaticos/tridente -- en reward_ominous_rare hay tres:
 * Breach/Density, Knockback/Punch/Smite/Looting/Multishot, y Wind Burst).
 * Vacia = item plano sin encantamiento aleatorio. Editable por libro
 * puntual desde el editor (ver BookPoolEditorGui).
 */
data class TrialRewardEntry(
    val itemId: String,
    val weight: Double,
    val minCount: Int,
    val maxCount: Int,
    val enchantOptions: List<String> = emptyList()
)

/**
 * Overrides de recompensas de trial chambers, por loot table id (ej.
 * "minecraft:chests/trial_chambers/reward" y "...reward_ominous"). Mientras
 * una tabla no tenga override guardado, TrialRewardLootModifier deja pasar
 * la tabla vanilla sin tocarla -- el editor (TrialRewardEditorGui) recien
 * activa el override la primera vez que el admin guarda cambios.
 */
object TrialRewardConfig {

    private val LOGGER = LoggerFactory.getLogger("hardmod")
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val overrides = ConcurrentHashMap<String, MutableList<TrialRewardEntry>>()

    /**
     * Rango de tiradas (items por apertura) por tabla -- separado de
     * [overrides] a proposito para no tocar esa estructura. Mientras no
     * haya uno guardado para una tabla, TrialRewardLootModifier cae a
     * 1 tirada fija (comportamiento previo a este campo).
     */
    private val rollRanges = ConcurrentHashMap<String, TrialRewardDefaultsLoader.RollRange>()

    private fun configFile(): Path =
        FabricLoader.getInstance().configDir.resolve("hardmod").resolve("trial_rewards.json")

    fun hasOverride(tableId: String): Boolean = overrides.containsKey(tableId)

    fun getOverride(tableId: String): List<TrialRewardEntry> = overrides[tableId] ?: emptyList()

    fun setOverride(tableId: String, entries: List<TrialRewardEntry>) {
        overrides[tableId] = entries.toMutableList()
        save()
    }

    fun clearOverride(tableId: String) {
        overrides.remove(tableId)
        rollRanges.remove(tableId)
        save()
    }

    fun getRollRange(tableId: String): TrialRewardDefaultsLoader.RollRange? = rollRanges[tableId]

    fun setRollRange(tableId: String, range: TrialRewardDefaultsLoader.RollRange) {
        rollRanges[tableId] = range
        save()
    }

    fun reload() {
        val file = configFile()
        if (!Files.exists(file)) return
        try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                overrides.clear()
                val tables = json.getAsJsonObject("overrides") ?: return@use
                for (tableId in tables.keySet()) {
                    val entriesJson = tables.getAsJsonArray(tableId)
                    val entries = entriesJson.map { e ->
                        val o = e.asJsonObject
                        val enchantOptions = if (o.has("enchantOptions")) {
                            o.getAsJsonArray("enchantOptions").map { it.asString }
                        } else emptyList()
                        TrialRewardEntry(
                            itemId = o.get("item").asString,
                            weight = o.get("weight").asDouble,
                            minCount = o.get("minCount").asInt,
                            maxCount = o.get("maxCount").asInt,
                            enchantOptions = enchantOptions
                        )
                    }.toMutableList()
                    overrides[tableId] = entries
                }
                rollRanges.clear()
                val rangesJson = json.getAsJsonObject("rollRanges")
                if (rangesJson != null) {
                    for (tableId in rangesJson.keySet()) {
                        val o = rangesJson.getAsJsonObject(tableId)
                        rollRanges[tableId] = TrialRewardDefaultsLoader.RollRange(o.get("min").asInt, o.get("max").asInt)
                    }
                }
            }
            LOGGER.info("[hardmod] Overrides de recompensas de trials cargados: {}", overrides.keys)
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudo leer {}: {}", file, e.toString())
        }
    }

    fun save() {
        val file = configFile()
        try {
            Files.createDirectories(file.parent)
            val json = JsonObject()
            val tables = JsonObject()
            for ((tableId, entries) in overrides) {
                val arr = JsonArray()
                for (entry in entries) {
                    val o = JsonObject()
                    o.addProperty("item", entry.itemId)
                    o.addProperty("weight", entry.weight)
                    o.addProperty("minCount", entry.minCount)
                    o.addProperty("maxCount", entry.maxCount)
                    if (entry.enchantOptions.isNotEmpty()) {
                        val enchantArr = JsonArray()
                        entry.enchantOptions.forEach { enchantArr.add(it) }
                        o.add("enchantOptions", enchantArr)
                    }
                    arr.add(o)
                }
                tables.add(tableId, arr)
            }
            json.add("overrides", tables)
            val rangesJson = JsonObject()
            for ((tableId, range) in rollRanges) {
                val o = JsonObject()
                o.addProperty("min", range.min)
                o.addProperty("max", range.max)
                rangesJson.add(tableId, o)
            }
            json.add("rollRanges", rangesJson)
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer -> GSON.toJson(json, writer) }
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudo guardar {}: {}", file, e.toString())
        }
    }
}
