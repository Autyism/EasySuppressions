package io.github.autyism.easysuppressions.client;

import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.client.MinecraftClient;

/** Asks the chunk renderer (vanilla or Sodium) to rebuild the sections a light zone touches. */
final class Rerender {
	private Rerender() {
	}

	/** The part of the zone within the loaded area around the player, plus one block for smooth lighting. */
	static void zone(Zone zone) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.worldRenderer == null) {
			return;
		}
		int radius = (client.options.getClampedViewDistance() + 1) * 16;
		int px = client.player.getBlockX();
		int pz = client.player.getBlockZ();
		int minX = Math.max(zone.minX(), px - radius) - 1;
		int maxX = Math.min(zone.maxX(), px + radius) + 1;
		int minZ = Math.max(zone.minZ(), pz - radius) - 1;
		int maxZ = Math.min(zone.maxZ(), pz + radius) + 1;
		int minY = Math.max(zone.minY(), client.world.getBottomY()) - 1;
		int maxY = Math.min(zone.maxY(), client.world.getTopYInclusive()) + 1;
		if (minX > maxX || minZ > maxZ || minY > maxY) {
			return;
		}
		client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** One chunk column of a zone (its recorded light just arrived). */
	static void column(Zone zone, int cx, int cz) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.worldRenderer == null) {
			return;
		}
		int minX = Math.max(zone.minX(), cx << 4) - 1;
		int maxX = Math.min(zone.maxX(), (cx << 4) + 15) + 1;
		int minZ = Math.max(zone.minZ(), cz << 4) - 1;
		int maxZ = Math.min(zone.maxZ(), (cz << 4) + 15) + 1;
		int minY = Math.max(zone.minY(), client.world.getBottomY()) - 1;
		int maxY = Math.min(zone.maxY(), client.world.getTopYInclusive()) + 1;
		if (minY > maxY) {
			return;
		}
		client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
	}
}
