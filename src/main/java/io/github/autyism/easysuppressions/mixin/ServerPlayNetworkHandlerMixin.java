package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.vanilla.VanillaSupport;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks of players without the mod: see VanillaSupport. All on the main thread (after forceMainThread). */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onHandSwing", at = @At("TAIL"))
	private void easysuppressions$swing(HandSwingC2SPacket packet, CallbackInfo ci) {
		VanillaSupport.onSwing(player, packet.getHand());
	}

	@Inject(method = "onPlayerInteractBlock", at = @At("TAIL"))
	private void easysuppressions$placed(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
		VanillaSupport.afterBlockAction(player, packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide()));
		VanillaSupport.afterBlockAction(player, packet.getBlockHitResult().getBlockPos());
	}

	@Inject(method = "onPlayerAction", at = @At("TAIL"))
	private void easysuppressions$broke(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK || packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
			VanillaSupport.afterBlockAction(player, packet.getPos());
		}
	}
}
