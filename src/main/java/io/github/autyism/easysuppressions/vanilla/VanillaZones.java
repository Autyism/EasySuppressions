package io.github.autyism.easysuppressions.vanilla;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.world.WorldZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zone display for players without the mod: particle outlines, only for that player. While a
 * selector is held: its selection, and the zones of that kind nearby. "Show zone" in the block
 * dialog shows one zone for a while.
 */
public final class VanillaZones {
	private static final int INTERVAL = 10;
	private static final double NEAR = 64.0;
	private static final int MAX_PER_BOX = 360;
	private static final int MAX_PER_PLAYER = 1200;
	private static final int CUTTER_COLOR = 0xC060FF;
	private static final int LIGHT_COLOR = 0xFFA030;
	private static final int SELECTION_COLOR = 0x40FFFF;

	private record Shown(Zone zone, SuppressorKind kind, long until, String dimension) {
	}

	private static final Map<UUID, Shown> SHOWN = new HashMap<>();

	private VanillaZones() {
	}

	public static void show(ServerPlayerEntity player, Zone zone, SuppressorKind kind, int ticks) {
		SHOWN.put(player.getUuid(), new Shown(zone, kind, player.getEntityWorld().getTime() + ticks,
				player.getEntityWorld().getRegistryKey().getValue().toString()));
	}

	public static void forget(ServerPlayerEntity player) {
		SHOWN.remove(player.getUuid());
	}

	public static void tick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (EsClients.has(player)) {
				continue;
			}
			ServerWorld world = player.getEntityWorld();
			int[] budget = {MAX_PER_PLAYER};
			Shown shown = SHOWN.get(player.getUuid());
			if (shown != null) {
				if (world.getTime() > shown.until || !shown.dimension.equals(world.getRegistryKey().getValue().toString())) {
					SHOWN.remove(player.getUuid());
				} else {
					box(player, shown.zone, color(shown.kind), budget);
				}
			}
			for (Hand hand : Hand.values()) {
				ItemStack stack = player.getStackInHand(hand);
				if (stack.getItem() instanceof SelectorItem selector) {
					held(player, world, stack, selector.kind(), budget);
					break;
				}
			}
		}
	}

	private static void held(ServerPlayerEntity player, ServerWorld world, ItemStack stack, SuppressorKind kind, int[] budget) {
		Selection selection = stack.get(ModContent.SELECTION);
		if (selection != null && selection.dimension().equals(world.getRegistryKey())) {
			if (selection.isComplete()) {
				box(player, selection.zone().orElseThrow(), SELECTION_COLOR, budget);
			} else {
				selection.pos1().ifPresent(p -> box(player, Zone.of(p, p), SELECTION_COLOR, budget));
				selection.pos2().ifPresent(p -> box(player, Zone.of(p, p), SELECTION_COLOR, budget));
			}
		}
		WorldZones zones = ServerZones.of(world);
		List<Zone> near = new ArrayList<>();
		if (kind == SuppressorKind.PORTAL_CUTTER) {
			for (EsWorldState.CutterEntry e : zones.cutters()) {
				near.add(e.zone());
			}
		} else {
			for (LightZone zone : zones.lightZones()) {
				near.add(zone.zone);
			}
		}
		Vec3d eye = player.getEyePos();
		near.removeIf(z -> distance(z, eye) > NEAR);
		near.sort((a, b) -> Double.compare(distance(a, eye), distance(b, eye)));
		for (Zone zone : near) {
			box(player, zone, color(kind), budget);
		}
	}

	private static int color(SuppressorKind kind) {
		return kind == SuppressorKind.PORTAL_CUTTER ? CUTTER_COLOR : LIGHT_COLOR;
	}

	private static double distance(Zone z, Vec3d p) {
		double dx = Math.max(0, Math.max(z.minX() - p.x, p.x - (z.maxX() + 1)));
		double dy = Math.max(0, Math.max(z.minY() - p.y, p.y - (z.maxY() + 1)));
		double dz = Math.max(0, Math.max(z.minZ() - p.z, p.z - (z.maxZ() + 1)));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The 12 edges of the zone's outer box, evenly spaced. */
	private static void box(ServerPlayerEntity player, Zone zone, int color, int[] budget) {
		if (budget[0] <= 0) {
			return;
		}
		double x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
		double x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
		double total = 4 * ((x1 - x0) + (y1 - y0) + (z1 - z0));
		int count = Math.min(Math.min(MAX_PER_BOX, budget[0]), (int) Math.ceil(total * 2));
		double step = total / Math.max(1, count);
		DustParticleEffect dust = new DustParticleEffect(color, 1.0F);
		ServerWorld world = player.getEntityWorld();
		double[][] edges = {
				{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
				{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
				{x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
		int sent = 0;
		for (double[] e : edges) {
			double length = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
			for (double d = 0; d <= length + 1e-6 && sent < count; d += step) {
				double t = length == 0 ? 0 : d / length;
				double x = e[0] + (e[3] - e[0]) * t;
				double y = e[1] + (e[4] - e[1]) * t;
				double z = e[2] + (e[5] - e[2]) * t;
				if (player.squaredDistanceTo(x, y, z) > 128 * 128) {
					continue;
				}
				world.spawnParticles(player, dust, true, false, x, y, z, 1, 0, 0, 0, 0);
				sent++;
			}
		}
		budget[0] -= sent;
	}
}
