package io.github.autyism.easysuppressions.vanilla;

import io.github.autyism.easysuppressions.SuppressorKind;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/**
 * What players without the mod see. Full, solid, unlit vanilla blocks in the blocks' colours (the
 * real blocks are full solid cubes too, so collisions and light occlusion match), and plain items.
 */
public final class StandIns {
	private StandIns() {
	}

	public static BlockState block(SuppressorKind kind) {
		return (kind == SuppressorKind.PORTAL_CUTTER ? Blocks.PURPLE_GLAZED_TERRACOTTA : Blocks.ORANGE_GLAZED_TERRACOTTA).getDefaultState();
	}

	public static Item blockItem(SuppressorKind kind) {
		return kind == SuppressorKind.PORTAL_CUTTER ? Items.PURPLE_GLAZED_TERRACOTTA : Items.ORANGE_GLAZED_TERRACOTTA;
	}

	public static Item selector(SuppressorKind kind) {
		return kind == SuppressorKind.PORTAL_CUTTER ? Items.AMETHYST_SHARD : Items.BLAZE_ROD;
	}

	public static Item pickaxe() {
		return Items.GOLDEN_PICKAXE;
	}

	/**
	 * Selectors and the portal pickaxe never break blocks on the client (the pickaxe's portal
	 * mining runs on the server): no mining speed, no creative breaking.
	 */
	public static void noClientMining(ItemStack out) {
		out.set(DataComponentTypes.TOOL, new ToolComponent(List.of(), 0.0F, 0, false));
	}

	/** Name and lore in the player's language (the client has no translations for the mod's keys). */
	public static void translate(ItemStack out, String language) {
		Text name = out.get(DataComponentTypes.ITEM_NAME);
		if (name != null) {
			out.set(DataComponentTypes.ITEM_NAME, EsLang.translate(name, language));
		}
		Text custom = out.get(DataComponentTypes.CUSTOM_NAME);
		if (custom != null) {
			out.set(DataComponentTypes.CUSTOM_NAME, EsLang.translate(custom, language));
		}
		LoreComponent lore = out.get(DataComponentTypes.LORE);
		if (lore != null) {
			out.set(DataComponentTypes.LORE, new LoreComponent(lore.lines().stream().map(t -> EsLang.translate(t, language)).toList()));
		}
	}
}
