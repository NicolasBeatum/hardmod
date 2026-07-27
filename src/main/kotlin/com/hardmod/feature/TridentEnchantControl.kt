package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.minecraft.core.Holder
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments
import java.util.stream.Stream

/** Modo de restriccion de encantamientos para el tridente. */
enum class TridentMode { OFF, BLOCK_ALL, MAX_UNBREAKING_III }

/**
 * Restringe los encantamientos del tridente en dos capas:
 *
 * 1. [filterPool] (via EnchantmentTablePoolMixin sobre
 *    EnchantmentHelper.selectEnchantment): filtra el POOL de candidatos
 *    antes de que la mesa haga el roll -- asi las otras opciones ni
 *    siquiera aparecen como posibilidad en los 3 slots (no es "se aplico
 *    y despues se corrigio", es "nunca estuvo en la lista").
 * 2. [filter] (via ItemStackMixin sobre ItemStack.set): red de seguridad
 *    final sobre CUALQUIER camino que escriba el componente ENCHANTMENTS
 *    (yunque, /enchant, loot functions) -- cubre lo que el filtro de pool
 *    no ve porque no pasa por selectEnchantment.
 */
object TridentEnchantControl {

    @JvmStatic
    fun filterPool(stack: ItemStack, pool: Stream<Holder<Enchantment>>): Stream<Holder<Enchantment>> {
        if (!stack.`is`(Items.TRIDENT)) return pool
        return when (HardModConfig.tridentMode) {
            TridentMode.OFF -> pool
            TridentMode.BLOCK_ALL -> Stream.empty()
            TridentMode.MAX_UNBREAKING_III -> pool.filter { it.`is`(Enchantments.UNBREAKING) }
        }
    }

    @JvmStatic
    fun filter(enchantments: ItemEnchantments): ItemEnchantments {
        if (enchantments.isEmpty) return enchantments
        return when (HardModConfig.tridentMode) {
            TridentMode.OFF -> enchantments
            TridentMode.BLOCK_ALL -> ItemEnchantments.EMPTY
            TridentMode.MAX_UNBREAKING_III -> {
                val mutable = ItemEnchantments.Mutable(ItemEnchantments.EMPTY)
                for (entry in enchantments.entrySet()) {
                    if (entry.key.`is`(Enchantments.UNBREAKING)) {
                        mutable.set(entry.key, minOf(entry.intValue, 3))
                    }
                }
                mutable.toImmutable()
            }
        }
    }
}
