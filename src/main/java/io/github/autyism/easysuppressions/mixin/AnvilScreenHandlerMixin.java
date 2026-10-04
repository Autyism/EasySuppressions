package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.item.BoundZone;
import io.github.autyism.easysuppressions.item.PortalPickaxeItem;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.item.SuppressionBlockItem;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil binding: block item on the left, a selector of the same kind with a full selection on the
 * right gives one block bound to that zone, for 1 level. Taking it uses up one block; the selector
 * stays in the right slot (it goes back to the inventory when the anvil is closed) so the rest of
 * the stack can be bound too. Selectors cannot be renamed or combined; the portal pickaxe takes no
 * enchanted books.
 */
@Mixin(AnvilScreenHandler.class)
abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
	@Shadow
	@Final
	private Property levelCost;
	@Shadow
	private int repairItemUsage;

	@Unique
	private boolean easysuppressions$binding;

	private AnvilScreenHandlerMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory inventory, ScreenHandlerContext context, ForgingSlotsManager slots) {
		super(type, syncId, inventory, context, slots);
	}

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$updateResult(CallbackInfo ci) {
		easysuppressions$binding = false;
		ItemStack left = input.getStack(0);
		ItemStack right = input.getStack(1);
		if (left.getItem() instanceof SuppressionBlockItem block && right.getItem() instanceof SelectorItem selector) {
			Selection selection = right.get(ModContent.SELECTION);
			if (block.kind() == selector.kind() && selection != null && selection.isComplete()) {
				ItemStack result = left.copyWithCount(1);
				result.set(ModContent.BOUND_ZONE, new BoundZone(selection.dimension(), selection.zone().orElseThrow()));
				output.setStack(0, result);
				levelCost.set(1);
				repairItemUsage = 0;
				easysuppressions$binding = true;
			} else {
				easysuppressions$noResult();
			}
			sendContentUpdates();
			ci.cancel();
			return;
		}
		boolean selectorInvolved = left.getItem() instanceof SelectorItem || right.getItem() instanceof SelectorItem;
		boolean pickaxeWithBook = (left.getItem() instanceof PortalPickaxeItem && !right.isEmpty()) || right.getItem() instanceof PortalPickaxeItem;
		if (selectorInvolved || pickaxeWithBook) {
			easysuppressions$noResult();
			sendContentUpdates();
			ci.cancel();
		}
	}

	@Unique
	private void easysuppressions$noResult() {
		output.setStack(0, ItemStack.EMPTY);
		levelCost.set(0);
		repairItemUsage = 0;
	}

	@Inject(method = "onTakeOutput", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$onTakeBound(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (!easysuppressions$binding) {
			return;
		}
		easysuppressions$binding = false;
		if (!player.isInCreativeMode()) {
			player.addExperienceLevels(-levelCost.get());
		}
		levelCost.set(0);
		ItemStack left = input.getStack(0);
		left.decrement(1);
		input.setStack(0, left.isEmpty() ? ItemStack.EMPTY : left);
		context.run((world, pos) -> {
			BlockState state = world.getBlockState(pos);
			if (!player.isInCreativeMode() && state.isIn(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
				BlockState damaged = AnvilBlock.getLandingState(state);
				if (damaged == null) {
					world.removeBlock(pos, false);
					world.syncWorldEvent(WorldEvents.ANVIL_DESTROYED, pos, 0);
				} else {
					world.setBlockState(pos, damaged, Block.NOTIFY_LISTENERS);
					world.syncWorldEvent(WorldEvents.ANVIL_USED, pos, 0);
				}
			} else {
				world.syncWorldEvent(WorldEvents.ANVIL_USED, pos, 0);
			}
		});
		ci.cancel();
	}
}
