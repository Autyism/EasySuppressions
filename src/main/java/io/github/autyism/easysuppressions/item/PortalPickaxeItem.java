package io.github.autyism.easysuppressions.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * Mines nether portal blocks only (fixed time from the config, see AbstractBlockStateMixin) and
 * nothing else, not even in creative mode. Unbreakable and not enchantable (no durability or
 * enchantable component).
 */
public class PortalPickaxeItem extends Item {
	public PortalPickaxeItem(Settings settings) {
		super(settings);
	}

	public static boolean isMineable(BlockState state) {
		return state.isOf(Blocks.NETHER_PORTAL);
	}

	@Override
	public boolean canMine(ItemStack stack, BlockState state, World world, BlockPos pos, LivingEntity user) {
		return isMineable(state);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.translatable("easysuppressions.tooltip.portal_pickaxe").formatted(Formatting.GRAY));
	}
}
