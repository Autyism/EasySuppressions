package io.github.autyism.easysuppressions.vanilla;

import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import io.github.autyism.easysuppressions.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtInt;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Who has the mod. Players with it get the real blocks, items and screens; everybody else gets
 * vanilla stand-ins (through Polymer) and the server-side dialogs of the vanilla package.
 */
public final class EsClients {
	/** Sent by Polymer on the client while logging in; the raw block state ids need it. */
	private static final Identifier POLYMER_BLOCKSTATE_BITS = Identifier.of("polymer", "core/blockstate_bits");
	private static final boolean INSIDE_GAME = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

	private EsClients() {
	}

	/** For gameplay: does this player's client have the mod (its screens, selectors, zone display)? */
	public static boolean has(ServerPlayerEntity player) {
		return player.networkHandler != null && ServerPlayNetworking.canSend(player, Payloads.CutterZones.ID);
	}

	/**
	 * For packets: may the real blocks and items be sent to this connection? Needs the mod (its
	 * channels) and Polymer on the client, which turns the server's ids back into the real entries.
	 * Without a known connection: the real ones inside the game (singleplayer), stand-ins on a dedicated server.
	 */
	public static boolean has(@Nullable PacketContext context) {
		if (context == null) {
			return false;
		}
		ClientConnection connection = context.getClientConnection();
		if (connection == null) {
			// no connection known: inside the game itself (singleplayer, the client's own use) the real ones;
			// on a dedicated server the stand-ins
			return INSIDE_GAME;
		}
		// singleplayer host: the same game on both ends (Polymer sends no metadata there)
		if (connection.isLocal()) {
			return true;
		}
		if (PolymerServerNetworking.getMetadata(connection, POLYMER_BLOCKSTATE_BITS, NbtInt.TYPE) == null) {
			return false;
		}
		PacketListener listener = context.getBackingPacketListener();
		if (listener instanceof ServerPlayNetworkHandler play) {
			return ServerPlayNetworking.canSend(play, Payloads.CutterZones.ID);
		}
		if (listener instanceof ServerConfigurationNetworkHandler configuration) {
			return ServerConfigurationNetworking.canSend(configuration, Payloads.Hello.ID);
		}
		return false;
	}

	/** The language of the player's client (en_us when unknown). */
	public static String language(@Nullable PacketContext context) {
		if (context != null && context.getClientOptions() != null) {
			return context.getClientOptions().language();
		}
		return "en_us";
	}

	public static String language(ServerPlayerEntity player) {
		return player.getClientOptions().language();
	}
}
