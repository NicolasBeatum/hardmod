package com.hardmod.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hardmod.feature.ServerShutdownScheduler
import com.hardmod.feature.TridentMode
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Config global del mod (no por-mundo): toggles y multiplicadores que un
 * admin ajusta en caliente con /hardmod. Persistido en
 * config/hardmod/config.json via JsonObject a mano (mismo estilo que
 * MissionRegistry/MissionSchedule de mision-mod) en vez de deserializar un
 * data class con Gson -- Gson no respeta los valores default de Kotlin
 * cuando falta un campo en el JSON, asi que leer/escribir a mano evita ese
 * problema y deja los defaults explicitos aca.
 *
 * Defaults pensados para un mundo hardcore recien empezado: mesa de
 * encantamientos BLOQUEADA, aldeanos ELIMINADOS, mobcap normal (x1),
 * tridente limitado a Unbreaking III, quemadura permanente apagada,
 * Nether y End BLOQUEADOS (hasta que un admin los active), totems al 100% vanilla,
 * raids desactivadas y hora de cierre 02:00.
 */
object HardModConfig {

    private val LOGGER = LoggerFactory.getLogger("hardmod")
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Categoria de mob (MobCategory.getSerializedName(), ej. "monster") -> multiplicador del cap. Ausente = x1. */
    val mobcapMultipliers: MutableMap<String, Double> = ConcurrentHashMap()

    @Volatile var enchantTableLocked: Boolean = true
        private set

    /** Si no es null, cuando pase este epoch millis se revierte el estado de [enchantTableLocked] (ver EnchantTableLock). */
    @Volatile var enchantTableAutoRevertAtEpochMillis: Long? = null
        private set

    @Volatile var blockVillagers: Boolean = true
        private set

    @Volatile var tridentMode: TridentMode = TridentMode.MAX_UNBREAKING_III
        private set

    @Volatile var permanentBurn: Boolean = false
        private set

    @Volatile var announceSoundId: String = "minecraft:entity.experience_orb.pickup"
        private set

    @Volatile var netherLocked: Boolean = true
        private set

    @Volatile var endLocked: Boolean = true
        private set

    /** Probabilidad porcentual de que un totem intente salvar a su portador. 0 = desactivado, 100 = vanilla. */
    @Volatile var totemActivationChance: Int = 100
        private set

    @Volatile var raidsEnabled: Boolean = false
        private set

    @Volatile var shutdownHour: Int = 2
        private set

    @Volatile var shutdownMinute: Int = 0
        private set

    private fun configFile(): Path =
        FabricLoader.getInstance().configDir.resolve("hardmod").resolve("config.json")

    fun reload() {
        val file = configFile()
        if (!Files.exists(file)) {
            save()
            return
        }
        try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                mobcapMultipliers.clear()
                if (json.has("mobcapMultipliers")) {
                    val obj = json.getAsJsonObject("mobcapMultipliers")
                    for (key in obj.keySet()) {
                        mobcapMultipliers[key] = obj.get(key).asDouble
                    }
                }
                enchantTableLocked = json.get("enchantTableLocked")?.asBoolean ?: true
                enchantTableAutoRevertAtEpochMillis = if (json.has("enchantTableAutoRevertAtEpochMillis") && !json.get("enchantTableAutoRevertAtEpochMillis").isJsonNull) {
                    json.get("enchantTableAutoRevertAtEpochMillis").asLong
                } else null
                blockVillagers = json.get("blockVillagers")?.asBoolean ?: true
                tridentMode = try {
                    TridentMode.valueOf(json.get("tridentMode")?.asString ?: TridentMode.MAX_UNBREAKING_III.name)
                } catch (e: IllegalArgumentException) {
                    TridentMode.MAX_UNBREAKING_III
                }
                permanentBurn = json.get("permanentBurn")?.asBoolean ?: false
                announceSoundId = json.get("announceSoundId")?.asString ?: "minecraft:entity.experience_orb.pickup"
                netherLocked = json.get("netherLocked")?.asBoolean ?: true
                endLocked = json.get("endLocked")?.asBoolean ?: true
                totemActivationChance = (json.get("totemActivationChance")?.asInt ?: 100).coerceIn(0, 100)
                raidsEnabled = json.get("raidsEnabled")?.asBoolean ?: false
                shutdownHour = json.get("shutdownHour")?.asInt ?: 2
                shutdownMinute = json.get("shutdownMinute")?.asInt ?: 0
            }
            LOGGER.info("[hardmod] Config cargada de {}", file)
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudo leer {}: {}", file, e.toString())
        }
    }

    fun save() {
        val file = configFile()
        try {
            Files.createDirectories(file.parent)
            val json = JsonObject()
            val mobcapJson = JsonObject()
            mobcapMultipliers.forEach { (k, v) -> mobcapJson.addProperty(k, v) }
            json.add("mobcapMultipliers", mobcapJson)
            json.addProperty("enchantTableLocked", enchantTableLocked)
            enchantTableAutoRevertAtEpochMillis?.let { json.addProperty("enchantTableAutoRevertAtEpochMillis", it) }
            json.addProperty("blockVillagers", blockVillagers)
            json.addProperty("tridentMode", tridentMode.name)
            json.addProperty("permanentBurn", permanentBurn)
            json.addProperty("announceSoundId", announceSoundId)
            json.addProperty("netherLocked", netherLocked)
            json.addProperty("endLocked", endLocked)
            json.addProperty("totemActivationChance", totemActivationChance)
            json.addProperty("raidsEnabled", raidsEnabled)
            json.addProperty("shutdownHour", shutdownHour)
            json.addProperty("shutdownMinute", shutdownMinute)
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer -> GSON.toJson(json, writer) }
        } catch (e: Exception) {
            LOGGER.error("[hardmod] No se pudo guardar {}: {}", file, e.toString())
        }
    }

    fun mobcapMultiplierFor(categorySerializedName: String): Double = mobcapMultipliers[categorySerializedName] ?: 1.0

    fun setMobcapMultiplier(categorySerializedName: String, multiplier: Double) {
        if (multiplier == 1.0) mobcapMultipliers.remove(categorySerializedName) else mobcapMultipliers[categorySerializedName] = multiplier
        save()
    }

    fun setEnchantTableLocked(locked: Boolean, autoRevertAtEpochMillis: Long?) {
        enchantTableLocked = locked
        enchantTableAutoRevertAtEpochMillis = autoRevertAtEpochMillis
        save()
    }

    fun setBlockVillagers(block: Boolean) {
        blockVillagers = block
        save()
    }

    fun setTridentMode(mode: TridentMode) {
        tridentMode = mode
        save()
    }

    fun setPermanentBurn(enabled: Boolean) {
        permanentBurn = enabled
        save()
    }

    fun setNetherLocked(locked: Boolean) {
        netherLocked = locked
        save()
    }

    fun setEndLocked(locked: Boolean) {
        endLocked = locked
        save()
    }

    fun setTotemActivationChance(chance: Int) {
        totemActivationChance = chance.coerceIn(0, 100)
        save()
    }

    fun setRaidsEnabled(enabled: Boolean) {
        raidsEnabled = enabled
        save()
    }

    fun setShutdownTime(hour: Int, minute: Int) {
        shutdownHour = hour.coerceIn(0, 23)
        shutdownMinute = minute.coerceIn(0, 59)
        ServerShutdownScheduler.resetTarget()
        save()
    }
}
