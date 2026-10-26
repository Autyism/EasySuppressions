package io.github.autyism.easysuppressions.item;

import io.github.autyism.easysuppressions.vanilla.EsClients;
import io.github.autyism.easysuppressions.vanilla.EsPolymerItem;
import io.github.autyism.easysuppressions.vanilla.StandIns;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Consumer;

/**
 * Mines nether portal blocks only (fixed time from the config, see AbstractBlockStateMixin) and
 * nothing else, not even in creative mode. Unbreakable and not enchantable (no durability or
 * enchantable component). Without the mod the client cannot mine portals at all, so the server
 * mines them for those players (Polymer's server-side mining).
 */
public class PortalPickaxeItem extends Item implements EsPolymerItem {
	public PortalPickaxeItem(Settings settings) {
		super(settings);
	}

	public static boolean isMineable(BlockState state) {
		return state.isOf(Blocks.NETHER_PORTAL);
	}

	@Override
	public Item standIn() {
		return StandIns.pickaxe();
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context) {
		StandIns.noClientMining(out);
	}

	@Override
	public boolean handleMiningOnServer(ItemStack tool, BlockState targetBlock, BlockPos pos, ServerPlayerEntity player) {
		return !EsClients.has(player) && isMineable(targetBlock);
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
