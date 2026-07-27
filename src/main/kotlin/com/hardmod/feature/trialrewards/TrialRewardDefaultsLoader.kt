package com.hardmod.feature.trialrewards

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import kotlin.math.round

/**
 * Lee las loot tables vanilla de recompensas de trial chambers (JSON crudo,
 * via ResourceManager -- no la API de LootTable en runtime) y las aplana a
 * una lista simple de item+peso+cantidad para el editor.
 *
 * Las tablas de vanilla (reward.json / reward_ominous.json) no listan items
 * directo: referencian otras tablas (reward_common/rare/unique, con pesos y
 * "rolls" propios, algunas detras de un random_chance). Este resolver baja
 * recursivamente por esas referencias y calcula, para cada item hoja, el
 * VALOR ESPERADO real (cuantas veces en promedio sale ese item por cofre,
 * segun la probabilidad de cada pool/condicion/roll de vanilla) -- es
 * matematicamente exacto (la esperanza es lineal, promediar "rolls" no es
 * una aproximacion), no solo "parecido". El peso se guarda con precision
 * decimal (no se redondea a entero aca -- ver TrialRewardEntry) para no
 * perder esa fidelidad; el redondeo a enteros solo pasa al construir el
 * LootPool real (TrialRewardLootModifier), que es la unica parte de la
 * API vanilla que de verdad exige un peso entero.
 */
object TrialRewardDefaultsLoader {

    private val LOGGER = LoggerFactory.getLogger("hardmod")

    /** Rango de tiradas (items por apertura) de la tabla completa -- ver computeRollRange. */
    data class RollRange(val min: Int, val max: Int)

    /**
     * Cuenta cuantos items en total puede tirar la tabla por apertura,
     * mirando SOLO los pools de nivel superior de la tabla (reward.json /
     * reward_ominous.json) -- no hace falta bajar a las tablas anidadas
     * porque cada "roll" de un pool de nivel superior, sea un item directo
     * o una referencia a otra loot table, siempre termina soltando
     * exactamente 1 item (las tablas anidadas -- reward_common/rare/unique
     * -- tienen un unico pool con rolls=1 cada una). Un pool con condicion
     * random_chance suma 0 al minimo (a veces no tira nada) y su maximo
     * normal al maximo (cuando si tira).
     */
    fun computeRollRange(resourceManager: ResourceManager, tableId: String): RollRange {
        val json = readTableJson(resourceManager, tableId) ?: return RollRange(1, 1)
        val pools = json.getAsJsonArray("pools") ?: return RollRange(1, 1)
        var totalMin = 0
        var totalMax = 0
        for (poolElement in pools) {
            val pool = poolElement.asJsonObject
            val gated = pool.getAsJsonArray("conditions")?.any { c ->
                c.asJsonObject.get("condition")?.asString == "minecraft:random_chance"
            } ?: false
            val (poolMin, poolMax) = rollsRangeOfNumberProvider(pool.get("rolls"))
            totalMin += if (gated) 0 else poolMin
            totalMax += poolMax
        }
        return RollRange(totalMin.coerceAtLeast(1), totalMax.coerceAtLeast(totalMin.coerceAtLeast(1)))
    }

    private fun rollsRangeOfNumberProvider(element: com.google.gson.JsonElement?): Pair<Int, Int> {
        if (element == null || element.isJsonNull) return 1 to 1
        if (element.isJsonPrimitive) {
            val v = Math.round(element.asDouble).toInt()
            return v to v
        }
        val obj = element.asJsonObject
        return when (obj.get("type")?.asString) {
            "minecraft:uniform" -> {
                val min = obj.get("min")?.asDouble ?: 0.0
                val max = obj.get("max")?.asDouble ?: min
                Math.floor(min).toInt() to Math.ceil(max).toInt()
            }
            "minecraft:constant" -> {
                val v = Math.round(obj.get("value")?.asDouble ?: 1.0).toInt()
                v to v
            }
            else -> 1 to 1
        }
    }

    private data class RawLeaf(
        val itemId: String,
        val expectedWeight: Double,
        val minCount: Int,
        val maxCount: Int,
        val enchantOptions: List<String> = emptyList()
    )

    /** Clave de agrupacion: dos entries del mismo item se combinan en una sola fila SOLO si tambien tienen el mismo pool de encantamiento (asi los dos/tres pools de libro distintos no se mezclan en uno). */
    private data class GroupKey(val itemId: String, val minCount: Int, val maxCount: Int, val enchantOptions: List<String>)

    fun resolveFlat(resourceManager: ResourceManager, tableId: String): List<TrialRewardEntry> {
        val raw = resolve(resourceManager, tableId, emptySet())
        return raw
            .groupBy { GroupKey(it.itemId, it.minCount, it.maxCount, it.enchantOptions) }
            .map { (key, group) ->
                val totalWeight = group.sumOf { it.expectedWeight }
                TrialRewardEntry(key.itemId, roundTo(totalWeight, 6), key.minCount, key.maxCount, key.enchantOptions)
            }
            .sortedByDescending { it.weight }
    }

    private fun roundTo(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return round(value * factor) / factor
    }

    /**
     * [visited] es el camino de tablas ya abiertas EN ESTA RAMA (no
     * global): antes se usaba un solo Set mutable compartido entre todas
     * las ramas de la recursion, lo que hacia que si dos pools distintos
     * de la MISMA tabla referenciaban la misma tabla anidada (ej.
     * reward.json referencia reward_common desde su pool 1 Y desde su
     * pool 2), la segunda referencia se descartara entera por "ya
     * visitada" -- aunque no fuera un ciclo real, solo la misma tabla
     * usada dos veces de forma legitima. Eso hacia que items comunes
     * (ej. diamond, que sale de reward_common) quedaran MUY sub-pesados
     * frente a items raros. Por eso aca se pasa una copia nueva (visited +
     * tableId) en cada llamada recursiva, no se muta un set compartido --
     * asi cada rama de la recursion tiene su propio historial, y solo se
     * corta ante un ciclo real (A referencia a B que referencia a A).
     */
    private fun resolve(resourceManager: ResourceManager, tableId: String, visited: Set<String>): List<RawLeaf> {
        if (tableId in visited) return emptyList()
        val nextVisited = visited + tableId
        val json = readTableJson(resourceManager, tableId) ?: return emptyList()
        val pools = json.getAsJsonArray("pools") ?: return emptyList()

        val leaves = mutableListOf<RawLeaf>()
        for (poolElement in pools) {
            val pool = poolElement.asJsonObject
            val poolChance = pool.getAsJsonArray("conditions")?.let { conditions ->
                var chance = 1.0
                for (c in conditions) {
                    val condObj = c.asJsonObject
                    if (condObj.get("condition")?.asString == "minecraft:random_chance") {
                        chance *= condObj.get("chance")?.asDouble ?: 1.0
                    }
                }
                chance
            } ?: 1.0
            val avgRolls = averageOfNumberProvider(pool.get("rolls"))
            val entries = pool.getAsJsonArray("entries") ?: continue
            val totalWeight = entries.sumOf { entryWeight(it.asJsonObject) }
            if (totalWeight <= 0) continue

            for (entryElement in entries) {
                val entry = entryElement.asJsonObject
                val share = poolChance * avgRolls * entryWeight(entry) / totalWeight
                when (entry.get("type")?.asString) {
                    "minecraft:item" -> {
                        val itemId = entry.get("name")?.asString ?: continue
                        val functions = entry.getAsJsonArray("functions")
                        val (min, max) = countRangeFromFunctions(functions)
                        val enchantOptions = enchantOptionsFromFunctions(functions)
                        leaves.add(RawLeaf(itemId, share, min, max, enchantOptions))
                    }
                    "minecraft:loot_table" -> {
                        val nestedId = entry.get("value")?.asString ?: continue
                        val nested = resolve(resourceManager, nestedId, nextVisited)
                        for (leaf in nested) {
                            leaves.add(RawLeaf(leaf.itemId, leaf.expectedWeight * share, leaf.minCount, leaf.maxCount, leaf.enchantOptions))
                        }
                    }
                    else -> Unit // minecraft:empty u otros tipos no soportados por el modelo simplificado -- se ignoran
                }
            }
        }
        return leaves
    }

    private fun entryWeight(entry: JsonObject): Int = entry.get("weight")?.asInt ?: 1

    private fun averageOfNumberProvider(element: com.google.gson.JsonElement?): Double {
        if (element == null || element.isJsonNull) return 1.0
        if (element.isJsonPrimitive) return element.asDouble
        val obj = element.asJsonObject
        return when (obj.get("type")?.asString) {
            "minecraft:uniform" -> {
                val min = obj.get("min")?.asDouble ?: 0.0
                val max = obj.get("max")?.asDouble ?: min
                (min + max) / 2.0
            }
            "minecraft:constant" -> obj.get("value")?.asDouble ?: 1.0
            else -> 1.0
        }
    }

    /**
     * Pool de encantamiento de un item (tipicamente un libro): soporta las
     * dos formas que usan las tablas de trials --
     * minecraft:enchant_randomly (lista explicita de opciones, ej. los dos
     * pools de reward_rare y dos de los tres de reward_ominous_rare) y
     * minecraft:set_enchantments (mapa fijo encantamiento->nivel, ej. el
     * Wind Burst nivel 1 de reward_ominous_rare -- se toma solo la clave,
     * queda como pool de una sola opcion que el admin puede ampliar).
     * Ignora "#tag" (options por tag, no por lista explicita) porque no
     * aparecen en los pools de libro reales de estas tablas.
     */
    private fun enchantOptionsFromFunctions(functions: com.google.gson.JsonArray?): List<String> {
        if (functions == null) return emptyList()
        for (fnElement in functions) {
            val fn = fnElement.asJsonObject
            when (fn.get("function")?.asString) {
                "minecraft:enchant_randomly" -> {
                    val options = fn.getAsJsonArray("options") ?: continue
                    return options.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                }
                "minecraft:set_enchantments" -> {
                    val enchantments = fn.getAsJsonObject("enchantments") ?: continue
                    return enchantments.keySet().toList()
                }
            }
        }
        return emptyList()
    }

    private fun countRangeFromFunctions(functions: com.google.gson.JsonArray?): Pair<Int, Int> {
        if (functions == null) return 1 to 1
        for (fnElement in functions) {
            val fn = fnElement.asJsonObject
            if (fn.get("function")?.asString == "minecraft:set_count") {
                val countElement = fn.get("count") ?: return 1 to 1
                return if (countElement.isJsonPrimitive) {
                    val v = countElement.asInt
                    v to v
                } else {
                    val obj = countElement.asJsonObject
                    val min = obj.get("min")?.asInt ?: 1
                    val max = obj.get("max")?.asInt ?: min
                    min to max
                }
            }
        }
        return 1 to 1
    }

    private fun readTableJson(resourceManager: ResourceManager, tableId: String): JsonObject? {
        val id = Identifier.tryParse(tableId) ?: return null
        val resourcePath = Identifier.fromNamespaceAndPath(id.namespace, "loot_table/${id.path}.json")
        return try {
            resourceManager.openAsReader(resourcePath).use { reader -> JsonParser.parseReader(reader).asJsonObject }
        } catch (e: Exception) {
            LOGGER.warn("[hardmod] No se pudo leer la loot table {}: {}", tableId, e.toString())
            null
        }
    }
}
