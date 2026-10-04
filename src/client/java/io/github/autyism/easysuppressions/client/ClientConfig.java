package io.github.autyism.easysuppressions.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.autyism.easysuppressions.EasySuppressions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-player client settings ({@code config/easysuppressions-client.json}):
 * whether light suppressor zones are drawn with the suppressed light (default) or the real light,
 * and which blocks' zone boxes the player switched off (default: every box is shown).
 */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static boolean displaySuppressedLight = true;
	private static final Set<String> hiddenZones = new LinkedHashSet<>();

	private ClientConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("easysuppressions-client.json");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
			if (json.has("displaySuppressedLight")) {
				displaySuppressedLight = json.get("displaySuppressedLight").getAsBoolean();
			}
			if (json.has("hiddenZones")) {
				for (JsonElement e : json.getAsJsonArray("hiddenZones")) {
					hiddenZones.add(e.getAsString());
				}
			}
		} catch (Exception e) {
			EasySuppressions.LOGGER.warn("Could not read {}", path, e);
		}
	}

	public static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("displaySuppressedLight", displaySuppressedLight);
		JsonArray hidden = new JsonArray();
		hiddenZones.forEach(hidden::add);
		json.add("hiddenZones", hidden);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (Exception e) {
			EasySuppressions.LOGGER.warn("Could not write {}", file(), e);
		}
	}

	public static boolean displaySuppressedLight() {
		return displaySuppressedLight;
	}

	public static void setDisplaySuppressedLight(boolean value) {
		if (displaySuppressedLight != value) {
			displaySuppressedLight = value;
			save();
			ClientZones.onDisplayModeChanged();
		}
	}

	/** Which world this is: the server address, or the save name in singleplayer. */
	private static String worldKey(MinecraftClient client) {
		if (client.getServer() != null) {
			return "sp:" + client.getServer().getSaveProperties().getLevelName();
		}
		ServerInfo info = client.getCurrentServerEntry();
		return info != null ? "mp:" + info.address : "unknown";
	}

	private static String zoneKey(BlockPos pos) {
		MinecraftClient client = MinecraftClient.getInstance();
		String dim = client.world != null ? client.world.getRegistryKey().getValue().toString() : "?";
		return worldKey(client) + "|" + dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static boolean isZoneShown(BlockPos pos) {
		return hiddenZones.isEmpty() || !hiddenZones.contains(zoneKey(pos));
	}

	public static void setZoneShown(BlockPos pos, boolean shown) {
		boolean changed = shown ? hiddenZones.remove(zoneKey(pos)) : hiddenZones.add(zoneKey(pos));
		if (changed) {
			save();
		}
	}
}
