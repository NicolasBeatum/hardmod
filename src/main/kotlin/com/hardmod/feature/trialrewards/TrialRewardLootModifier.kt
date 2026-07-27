package com.hardmod.feature.trialrewards

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction
import net.minecraft.world.level.storage.loot.functions.LootItemFunction
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

/** Loot tables de recompensa de trial chambers que este mod puede reemplazar (llave normal y llave ominosa). */
val MANAGED_TRIAL_REWARD_TABLES = listOf(
    "minecraft:chests/trial_chambers/reward",
    "minecraft:chests/trial_chambers/reward_ominous"
)

/**
 * Reemplaza por completo (LootTableEvents.REPLACE, no MODIFY -- MODIFY solo
 * permite agregar pools encima de los de vanilla, no sacarlos) la loot
 * table de recompensa por una version plana con los items+peso+cantidad
 * que el admin configuro en TrialRewardEditorGui. Mientras no haya
 * override guardado para una tabla, deja pasar la tabla original de
 * vanilla sin tocarla.
 */
object TrialRewardLootModifier {

    private val LOGGER = LoggerFactory.getLogger("hardmod")

    fun register() {
        LootTableEvents.REPLACE.register { key, original, _, registries ->
            val id = key.identifier().toString()
            if (id !in MANAGED_TRIAL_REWARD_TABLES || !TrialRewardConfig.hasOverride(id)) {
                return@register original
            }
            buildOverrideTable(id, registries) ?: original
        }
    }

    private fun buildOverrideTable(tableId: String, registries: HolderLookup.Provider): LootTable? {
        val entries = TrialRewardConfig.getOverride(tableId)
        if (entries.isEmpty()) return null

        // LootPoolSingletonContainer.Builder.setWeight solo acepta int -- pero
        // nuestros pesos son decimales (para no perder precision frente a los
        // valores reales de vanilla, ver TrialRewardDefaultsLoader). Escalamos
        // todo por un factor comun para que el peso mas chico quede en ~1000,
        // asi el redondeo a entero introduce, en el peor caso, un error
        // relativo de ~0.05% -- muy por debajo del 0.3% de tolerancia.
        val minWeight = entries.map { it.weight }.filter { it > 0.0 }.minOrNull() ?: 1.0
        val scale = (1000.0 / minWeight).coerceIn(1.0, 1_000_000.0)

        // Sin rango guardado (tabla vieja, de antes de que existiera este
        // campo), cae a 1 tirada fija -- comportamiento previo. Con rango
        // guardado, refleja las tiradas reales de la tabla de vanilla (ver
        // TrialRewardDefaultsLoader.computeRollRange): la ominosa, por
        // ejemplo, tira de 2 a 5 items por cofre, no 1 fijo.
        val rollRange = TrialRewardConfig.getRollRange(tableId)
        val rollsProvider: NumberProvider = when {
            rollRange == null -> ConstantValue.exactly(1.0f)
            rollRange.min == rollRange.max -> ConstantValue.exactly(rollRange.min.toFloat())
            else -> UniformGenerator.between(rollRange.min.toFloat(), rollRange.max.toFloat())
        }
        val pool = LootPool.lootPool().setRolls(rollsProvider)
        for (entry in entries) {
            val intWeight = (entry.weight * scale).roundToInt().coerceAtLeast(1)

            // "Aire" (ver TrialRewardEditorGui.AIR_ENTRY_ID): entrada minecraft:empty
            // real de vanilla -- a veces la tirada no da nada, no un item vacio.
            if (entry.itemId == AIR_ENTRY_ID) {
                pool.add(EmptyLootItem.emptyItem().setWeight(intWeight))
                continue
            }

            val itemId = Identifier.tryParse(entry.itemId) ?: continue
            val item: ItemLike = BuiltInRegistries.ITEM.getValue(itemId)
            val countProvider: NumberProvider = if (entry.minCount == entry.maxCount) {
                ConstantValue.exactly(entry.minCount.toFloat())
            } else {
                UniformGenerator.between(entry.minCount.toFloat(), entry.maxCount.toFloat())
            }
            val itemBuilder = LootItem.lootTableItem(item)
                .setWeight(intWeight)
                .apply(SetItemCountFunction.setCount(countProvider))
            val enchantFunction = buildEnchantFunction(entry.enchantOptions, registries)
            if (enchantFunction != null) {
                itemBuilder.apply(enchantFunction)
            }
            pool.add(itemBuilder)
        }
        LOGGER.info("[hardmod] Recompensas de {} reemplazadas por override ({} items)", tableId, entries.size)
        return LootTable.lootTable().withPool(pool).build()
    }

    /** Pool de encantamiento aleatorio de un libro (ver TrialRewardEntry.enchantOptions) -- null si el item no tiene pool configurado. */
    private fun buildEnchantFunction(enchantIds: List<String>, registries: HolderLookup.Provider): LootItemFunction.Builder? {
        if (enchantIds.isEmpty()) return null
        val enchantmentLookup = registries.lookupOrThrow(Registries.ENCHANTMENT)
        val holders = enchantIds.mapNotNull { idStr ->
            val id = Identifier.tryParse(idStr) ?: return@mapNotNull null
            enchantmentLookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null)
        }
        if (holders.isEmpty()) return null
        return EnchantRandomlyFunction.randomEnchantment().withOptions(HolderSet.direct(holders))
    }
}
