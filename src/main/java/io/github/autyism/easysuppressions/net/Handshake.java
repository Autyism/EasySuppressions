package io.github.autyism.easysuppressions.net;

import io.github.autyism.easysuppressions.EasySuppressions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * Turns away clients that have another version of the mod while they log in. Clients without the
 * mod may join: they get vanilla stand-ins (see the vanilla package).
 * The texts are built on the server and sent as plain text, because the client of another version
 * may lack them; they are written in Chinese and English.
 *
 * <p>The check runs in an early phase of BEFORE_CONFIGURE, ahead of Fabric's registry sync, so a
 * client with another version sees this message instead of a generic "missing registry entries" one.
 */
public final class Handshake {
	private static final Identifier EARLY = EasySuppressions.id("handshake");

	private Handshake() {
	}

	public static void init() {
		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(EARLY, (handler, server) -> {
			if (!ServerConfigurationNetworking.canSend(handler, Payloads.Hello.ID)) {
				// Without the mod: vanilla stand-ins and server-side dialogs (see the vanilla package)
				EasySuppressions.LOGGER.info("{} joins without {}", handler.getDebugProfile().name(), EasySuppressions.NAME);
				return;
			}
			handler.addTask(new VersionTask());
		});
		ServerConfigurationNetworking.registerGlobalReceiver(Payloads.HelloReply.ID, (payload, context) -> {
			String client = payload.version();
			if (!EasySuppressions.version().equals(client)) {
				EasySuppressions.LOGGER.info("Refusing {}: client has {} {}, server has {}", context.networkHandler().getDebugProfile().name(),
						EasySuppressions.NAME, client, EasySuppressions.version());
				context.networkHandler().disconnect(versionMismatchText(EasySuppressions.version(), client));
				return;
			}
			context.networkHandler().completeTask(VersionTask.KEY);
		});
	}

	public static Text versionMismatchText(String server, String client) {
		return Text.literal("").append(Text.literal(EasySuppressions.NAME + "\n\n").formatted(Formatting.GOLD, Formatting.BOLD))
				.append(Text.literal("模组版本不一致：服务端是 " + server + "，你的客户端是 " + client + "。\n"
						+ "请安装与服务端相同的版本后再连接，或者把这个模组移除（不装也能进）。\n\n"))
				.append(Text.literal("Mod version mismatch: the server has " + server + ", your client has " + client + ".\n"
						+ "Install the server's version, or remove the mod (it is optional), to join.").formatted(Formatting.GRAY));
	}

	private record VersionTask() implements ServerPlayerConfigurationTask {
		static final Key KEY = new Key(EasySuppressions.id("version_check").toString());

		@Override
		public void sendPacket(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createS2CPacket(new Payloads.Hello(EasySuppressions.version())));
		}

		@Override
		public Key getKey() {
			return KEY;
		}
	}
}
