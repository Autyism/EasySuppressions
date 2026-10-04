package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.ModContent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Selectors and the portal pickaxe do not go into the enchanting table. Its item slot is an
 * anonymous class inside EnchantmentScreenHandler that does not override canInsert, so the check
 * sits in Slot and only applies to slots declared there. (The grindstone already refuses them:
 * they have no durability and no enchantments.)
 */
@Mixin(Slot.class)
abstract class SlotMixin {
	@Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$noEnchanting(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (ModContent.isRestrictedTool(stack) && ((Object) this).getClass().getEnclosingClass() == EnchantmentScreenHandler.class) {
			cir.setReturnValue(false);
		}
	}
}
