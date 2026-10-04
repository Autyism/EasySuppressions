package io.github.autyism.easysuppressions.client;

import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Selector clicks (main hand only): left click = corner 1, right click = corner 2, on the block
 * the crosshair points at within 64 blocks. Nothing happens when no block is hit. The vanilla
 * attack / use is cancelled, so the selector never breaks or places anything, also in creative.
 */
public final class SelectorInput {
	public static final double RANGE = 64.0;

	private SelectorInput() {
	}

	public static boolean holdsSelector(MinecraftClient client) {
		return client.player != null && !client.player.isSpectator()
				&& client.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof SelectorItem;
	}

	/** Returns true when the click was taken by a selector. */
	public static boolean click(MinecraftClient client, int corner) {
		if (!holdsSelector(client)) {
			return false;
		}
		HitResult hit = client.player.raycast(RANGE, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			if (ClientPlayNetworking.canSend(Payloads.SelectorCorner.ID)) {
				ClientPlayNetworking.send(new Payloads.SelectorCorner(corner, blockHit.getBlockPos()));
			} else {
				client.player.sendMessage(Text.translatable("easysuppressions.message.server_missing_mod").formatted(Formatting.RED), true);
			}
			client.player.swingHand(Hand.MAIN_HAND);
		}
		return true;
	}
}
