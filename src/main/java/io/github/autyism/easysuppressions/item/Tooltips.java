package io.github.autyism.easysuppressions.item;

import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Shared tooltip / message lines. */
public final class Tooltips {
	private Tooltips() {
	}

	public static Text dimensionName(RegistryKey<World> dimension) {
		String id = dimension.getValue().toString();
		String key = switch (id) {
			case "minecraft:overworld" -> "easysuppressions.dimension.overworld";
			case "minecraft:the_nether" -> "easysuppressions.dimension.the_nether";
			case "minecraft:the_end" -> "easysuppressions.dimension.the_end";
			default -> null;
		};
		return key != null ? Text.translatable(key) : Text.literal(id);
	}

	public static Text dimension(RegistryKey<World> dimension) {
		return Text.translatable("easysuppressions.tooltip.dimension", dimensionName(dimension)).formatted(Formatting.GRAY);
	}

	public static Text range(Zone zone) {
		return Text.translatable("easysuppressions.tooltip.range", zone.describe()).formatted(Formatting.GRAY);
	}

	public static Text size(Zone zone) {
		return Text.translatable("easysuppressions.tooltip.size", zone.sizeX(), zone.sizeY(), zone.sizeZ()).formatted(Formatting.GRAY);
	}

	public static Text pos(net.minecraft.util.math.BlockPos pos) {
		return Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
	}
}
