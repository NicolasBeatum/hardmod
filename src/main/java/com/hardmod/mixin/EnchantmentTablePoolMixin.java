package com.hardmod.mixin;

import com.hardmod.feature.TridentEnchantControl;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

/**
 * Filtra el pool de encantamientos candidatos ANTES de que la mesa haga el
 * roll (selectEnchantment es lo que EnchantmentMenu.getEnchantmentList usa
 * para calcular las 3 opciones que se muestran/aplican) -- asi, para un
 * tridente en modo MAX_UNBREAKING_III, las demas opciones nunca llegan a
 * estar en la lista candidata; en BLOCK_ALL, la lista queda vacia y la
 * mesa no ofrece nada para ese item.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentTablePoolMixin {

    @ModifyVariable(method = "selectEnchantment", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> hardmod$filterTridentPool(
        Stream<Holder<Enchantment>> possibleEnchantments,
        RandomSource random,
        ItemStack stack,
        int cost
    ) {
        return TridentEnchantControl.filterPool(stack, possibleEnchantments);
    }
}
