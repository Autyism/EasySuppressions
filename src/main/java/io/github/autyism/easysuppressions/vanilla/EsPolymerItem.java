package io.github.autyism.easysuppressions.vanilla;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.core.api.utils.PolymerKeepModel;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Items of the mod: the real item for players with the mod, a vanilla stand-in with the real name
 * and tooltip (in the player's language) for everybody else.
 */
public interface EsPolymerItem extends PolymerItem, PolymerClientDecoded, PolymerKeepModel {
	Item standIn();

	/** Also what the item's id is sent as (Polymer replaces registry ids with this). */
	@Override
	default Item getPolymerItem(ItemStack stack, PacketContext context) {
		return EsClients.has(context) ? (Item) this : standIn();
	}

	@Override
	default ItemStack getPolymerItemStack(ItemStack stack, TooltipType tooltipType, PacketContext context) {
		if (EsClients.has(context)) {
			return stack;
		}
		ItemStack out = PolymerItemUtils.createItemStack(stack, tooltipType, context);
		StandIns.translate(out, EsClients.language(context));
		return out;
	}

	/** The stand-in's own model: clients without the mod have none of the mod's models. */
	@Override
	@Nullable
	default Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return null;
	}

	@Override
	default boolean canSyncRawToClient(PacketContext context) {
		return EsClients.has(context);
	}
}
