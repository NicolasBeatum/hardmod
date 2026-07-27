package com.hardmod.mixin;

import com.hardmod.feature.TridentEnchantControl;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ItemStack.set(DataComponentType, Object) es el punto de escritura comun
 * de TODOS los caminos que aplican encantamientos (mesa via
 * ItemStack.enchant -> EnchantmentHelper.updateEnchantments, yunque via
 * EnchantmentHelper.setEnchantments, /enchant, y las funciones de loot
 * table EnchantRandomlyFunction/EnchantWithLevelsFunction) -- todas
 * terminan escribiendo el componente ENCHANTMENTS con este mismo metodo,
 * asi que enganchar aca (en vez de cada camino por separado) es lo unico
 * que realmente cubre todos los casos.
 *
 * El re-llamado a self.set(...) con el valor ya filtrado vuelve a pasar por
 * este mismo inject, pero como el filtro es idempotente (filtrar un valor
 * ya filtrado da el mismo valor) la segunda vuelta no cambia nada y deja
 * que el metodo original siga su curso -- no es recursion infinita.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void hardmod$filterTridentEnchant(DataComponentType<?> type, Object value, CallbackInfoReturnable<Object> cir) {
        if (type != DataComponents.ENCHANTMENTS) return;
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.TRIDENT)) return;

        ItemEnchantments original = (ItemEnchantments) value;
        ItemEnchantments filtered = TridentEnchantControl.filter(original);
        if (filtered.equals(original)) return;

        cir.setReturnValue(self.set(DataComponents.ENCHANTMENTS, filtered));
        cir.cancel();
    }
}
