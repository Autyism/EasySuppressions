package io.github.autyism.easysuppressions.item;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.vanilla.EsPolymerItem;
import io.github.autyism.easysuppressions.vanilla.StandIns;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Consumer;

/**
 * Selects a zone like Litematica's stick, without needing Litematica: left click sets corner 1,
 * right click sets corner 2 (client input hook + server packet, see ServerNet; without the mod the
 * server reads the clicks itself, see VanillaSelectors). Never breaks blocks.
 */
public class SelectorItem extends Item implements EsPolymerItem {
	private final SuppressorKind kind;

	public SelectorItem(SuppressorKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public SuppressorKind kind() {
		return kind;
	}

	@Override
	public Item standIn() {
		return StandIns.selector(kind);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context) {
		StandIns.noClientMining(out);
	}

	@Override
	public boolean canMine(ItemStack stack, BlockState state, World world, BlockPos pos, LivingEntity user) {
		return false;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> tooltip, TooltipType type) {
		Selection selection = stack.get(ModContent.SELECTION);
		if (selection == null || (selection.pos1().isEmpty() && selection.pos2().isEmpty())) {
			tooltip.accept(Text.translatable("easysuppressions.tooltip.no_selection").formatted(Formatting.GRAY));
		} else {
			tooltip.accept(cornerLine(1, selection.pos1().orElse(null)));
			tooltip.accept(cornerLine(2, selection.pos2().orElse(null)));
			tooltip.accept(Tooltips.dimension(selection.dimension()));
			selection.zone().ifPresent(zone -> tooltip.accept(Tooltips.size(zone)));
		}
		tooltip.accept(Text.translatable("easysuppressions.tooltip.selector_usage").formatted(Formatting.DARK_GRAY));
	}

	private static Text cornerLine(int index, BlockPos pos) {
		Text value = pos == null ? Text.translatable("easysuppressions.tooltip.unset")
				: Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
		return Text.translatable("easysuppressions.tooltip.corner", index, value).formatted(Formatting.GRAY);
	}
}
