package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.minecraft.core.Holder
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import java.util.stream.Stream

/** Modo de restriccion del tridente exclusivamente en la mesa de encantamientos. */
enum class TridentMode { OFF, BLOCK_ALL, MAX_UNBREAKING_III }

/**
 * Filtra el pool de candidatos antes de que la mesa haga el roll. El yunque,
 * libros, loot y comandos quedan deliberadamente fuera de esta restriccion.
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
}
