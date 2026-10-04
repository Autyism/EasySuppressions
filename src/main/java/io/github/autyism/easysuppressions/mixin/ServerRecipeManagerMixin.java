package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.ModContent;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Selectors and the portal pickaxe are never a recipe ingredient: any recipe input containing one
 * matches nothing (crafting table, inventory grid, crafter, smithing, furnaces, ...).
 */
@Mixin(ServerRecipeManager.class)
abstract class ServerRecipeManagerMixin {
	@Unique
	private static boolean easysuppressions$hasTool(RecipeInput input) {
		for (int i = 0; i < input.size(); i++) {
			if (ModContent.isRestrictedTool(input.getStackInSlot(i))) {
				return true;
			}
		}
		return false;
	}

	@Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;",
			at = @At("HEAD"), cancellable = true)
	private <I extends RecipeInput, T extends Recipe<I>> void easysuppressions$noTools(RecipeType<T> type, I input, World world,
			CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
		if (easysuppressions$hasTool(input)) {
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;",
			at = @At("HEAD"), cancellable = true)
	private <I extends RecipeInput, T extends Recipe<I>> void easysuppressions$noToolsCached(RecipeType<T> type, I input, World world,
			RecipeEntry<T> recipe, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
		if (easysuppressions$hasTool(input)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
