package io.github.autyism.easysuppressions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.autyism.easysuppressions.EasySuppressions;
import io.netty.buffer.ByteBuf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings ({@code config/easysuppressions.json}). The server's values are what count;
 * they are sent to every client so the client's break progress bar matches the server.
 */
public final class EsConfig {
	public static final double MAX_SECONDS = 3600;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record Values(double blockBreakSeconds, double pickaxeSeconds) {
		public static final Values DEFAULT = new Values(10.0, 1.0);
		public static final PacketCodec<ByteBuf, Values> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.DOUBLE, Values::blockBreakSeconds,
				PacketCodecs.DOUBLE, Values::pickaxeSeconds,
				Values::new);

		public Values {
			blockBreakSeconds = clamp(blockBreakSeconds);
			pickaxeSeconds = clamp(pickaxeSeconds);
		}

		private static double clamp(double v) {
			if (Double.isNaN(v)) {
				return 0;
			}
			return Math.max(0, Math.min(MAX_SECONDS, Math.round(v * 20.0) / 20.0));
		}
	}

	/** Values of the server running in this process (dedicated or integrated). */
	private static volatile Values local = Values.DEFAULT;
	/** Values received from the server we are connected to; null when not connected. */
	private static volatile Values synced;

	private EsConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("easysuppressions.json");
	}

	public static Values local() {
		return local;
	}

	public static Values synced() {
		return synced;
	}

	public static void setSynced(Values values) {
		synced = values;
	}

	/** Values that apply on the given logical side. */
	public static Values forSide(boolean client) {
		if (client) {
			Values s = synced;
			return s != null ? s : local;
		}
		return local;
	}

	public static int ticks(double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	/**
	 * Break progress per tick for a fixed break time. A tiny bit is added so float rounding never
	 * needs one extra tick: after exactly {@code ticks} ticks the progress is above 1.
	 */
	public static float deltaFor(double seconds) {
		int ticks = ticks(seconds);
		if (ticks <= 0) {
			return 2.0F;
		}
		return 1.0F / ticks + 1.0E-5F;
	}

	public static void load() {
		Path path = file();
		Values values = Values.DEFAULT;
		if (Files.exists(path)) {
			try {
				JsonObject json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
				double breakSeconds = json.has("blockBreakSeconds") ? json.get("blockBreakSeconds").getAsDouble() : Values.DEFAULT.blockBreakSeconds();
				double pickaxeSeconds = json.has("portalPickaxeSeconds") ? json.get("portalPickaxeSeconds").getAsDouble() : Values.DEFAULT.pickaxeSeconds();
				values = new Values(breakSeconds, pickaxeSeconds);
			} catch (Exception e) {
				EasySuppressions.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		local = values;
		save();
	}

	public static void set(Values values) {
		local = values;
		save();
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("_comment", "Survival break time of the two blocks and portal pickaxe mining time, in seconds (0 = instant). Changes made in game are sent to every player.");
		json.addProperty("blockBreakSeconds", local.blockBreakSeconds());
		json.addProperty("portalPickaxeSeconds", local.pickaxeSeconds());
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			EasySuppressions.LOGGER.warn("Could not write {}", file(), e);
		}
	}
}
