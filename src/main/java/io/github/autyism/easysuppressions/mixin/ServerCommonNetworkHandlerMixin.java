package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.vanilla.EsClients;
import io.github.autyism.easysuppressions.vanilla.EsLang;
import io.github.autyism.easysuppressions.vanilla.VanillaScreens;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players without the mod: the mod's messages get a fallback text in the player's language (the
 * client has no translations for the mod's keys), and the buttons of the block dialogs come back as
 * custom click actions, which the vanilla server only logs.
 */
@Mixin(ServerCommonNetworkHandler.class)
abstract class ServerCommonNetworkHandlerMixin {
	@ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true)
	private Packet<?> easysuppressions$translate(Packet<?> packet) {
		if (packet instanceof GameMessageS2CPacket message && (Object) this instanceof ServerPlayNetworkHandler play
				&& EsLang.mentions(message.content()) && !EsClients.has(play.player)) {
			return new GameMessageS2CPacket(EsLang.translate(message.content(), EsClients.language(play.player)), message.overlay());
		}
		return packet;
	}

	@Inject(method = "onCustomClickAction", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/util/Identifier;Ljava/util/Optional;)V"))
	private void easysuppressions$dialog(CustomClickActionC2SPacket packet, CallbackInfo ci) {
		if (packet.id().equals(VanillaScreens.ACTION) && (Object) this instanceof ServerPlayNetworkHandler play) {
			try {
				VanillaScreens.handle(play.player, packet.payload());
			} catch (RuntimeException e) {
				EasySuppressions.LOGGER.error("Block dialog action failed", e);
			}
		}
	}
}
