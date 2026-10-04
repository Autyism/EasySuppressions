package io.github.autyism.easysuppressions.item;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import net.minecraft.block.Block;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

/** Block item that may carry a bound zone; refuses to be placed outside that zone. */
public class SuppressionBlockItem extends BlockItem {
	private final SuppressorKind kind;

	public SuppressionBlockItem(Block block, SuppressorKind kind, Settings settings) {
		super(block, settings);
		this.kind = kind;
	}

	public SuppressorKind kind() {
		return kind;
	}

	@Override
	public ActionResult place(ItemPlacementContext context) {
		BoundZone bound = context.getStack().get(ModContent.BOUND_ZONE);
		if (bound != null) {
			BlockPos pos = context.getBlockPos();
			boolean sameDimension = bound.dimension().equals(context.getWorld().getRegistryKey());
			if (!sameDimension || !bound.zone().contains(pos)) {
				if (context.getPlayer() instanceof ServerPlayerEntity player) {
					player.sendMessage(Text.translatable(sameDimension ? "easysuppressions.message.place_outside_zone"
							: "easysuppressions.message.place_wrong_dimension", Tooltips.dimensionName(bound.dimension())).formatted(Formatting.RED), true);
				}
				return ActionResult.FAIL;
			}
		}
		return super.place(context);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> tooltip, TooltipType type) {
		BoundZone bound = stack.get(ModContent.BOUND_ZONE);
		if (bound == null) {
			tooltip.accept(Text.translatable("easysuppressions.tooltip.unbound").formatted(Formatting.GRAY));
		} else {
			tooltip.accept(Text.translatable("easysuppressions.tooltip.bound").formatted(Formatting.AQUA));
			tooltip.accept(Tooltips.range(bound.zone()));
			tooltip.accept(Tooltips.size(bound.zone()));
			tooltip.accept(Tooltips.dimension(bound.dimension()));
		}
		tooltip.accept(Text.translatable(kind == SuppressorKind.PORTAL_CUTTER ? "easysuppressions.tooltip.portal_cutter_block"
				: "easysuppressions.tooltip.light_suppressor_block").formatted(Formatting.DARK_GRAY));
	}
}
