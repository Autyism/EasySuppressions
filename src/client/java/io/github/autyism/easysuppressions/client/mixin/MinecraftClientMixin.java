package io.github.autyism.easysuppressions.client.mixin;

import io.github.autyism.easysuppressions.client.SelectorInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes left / right clicks with a selector in the main hand to the selector. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@Shadow
	private int itemUseCooldown;
	@Shadow
	@Nullable
	public ClientPlayerInteractionManager interactionManager;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$selectorCorner1(CallbackInfoReturnable<Boolean> cir) {
		if (SelectorInput.click((MinecraftClient) (Object) this, 1)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$selectorCorner2(CallbackInfo ci) {
		if (SelectorInput.click((MinecraftClient) (Object) this, 2)) {
			itemUseCooldown = 4;
			ci.cancel();
		}
	}

	/** Self test only: behave as if the attack key were held with the mouse captured. */
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "handleInputEvents",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleBlockBreaking(Z)V"))
	private boolean easysuppressions$testHoldAttack(boolean breaking) {
		return breaking || (io.github.autyism.easysuppressions.client.TestHooks.forceAttackHeld && ((MinecraftClient) (Object) this).currentScreen == null);
	}

	/** Holding the attack key with a selector must not start mining either. */
	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$noMiningWithSelector(boolean breaking, CallbackInfo ci) {
		if (SelectorInput.holdsSelector((MinecraftClient) (Object) this)) {
			if (interactionManager != null) {
				interactionManager.cancelBlockBreaking();
			}
			ci.cancel();
		}
	}
}
