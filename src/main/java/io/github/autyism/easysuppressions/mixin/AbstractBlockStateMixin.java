package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.item.PortalPickaxeItem;
import io.github.autyism.easysuppressions.item.SelectorItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Break speed with the mod's tools in hand: the portal pickaxe mines nether portal blocks in the
 * configured time (no speed modifiers) and makes no progress on anything else; selectors never
 * make progress.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
abstract class AbstractBlockStateMixin {
	@Shadow
	protected abstract BlockState asBlockState();

	@Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$toolSpeed(PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isOf(ModContent.PORTAL_PICKAXE)) {
			cir.setReturnValue(PortalPickaxeItem.isMineable(asBlockState())
					? EsConfig.deltaFor(EsConfig.forSide(player.getEntityWorld().isClient()).pickaxeSeconds())
					: 0.0F);
		} else if (stack.getItem() instanceof SelectorItem) {
			cir.setReturnValue(0.0F);
		}
	}
}
