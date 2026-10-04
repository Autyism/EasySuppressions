package io.github.autyism.easysuppressions.client.render;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.client.ClientConfig;
import io.github.autyism.easysuppressions.client.ClientZones;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws zone boxes in the world. Deliberately unlike Litematica's selection boxes: thick coloured
 * lines (purple for portal cutters, amber for light suppressors; deep when active, pale when not)
 * with solid cube markers on the corners. Columns still waiting for their light to be recorded
 * are shown as translucent red pillars on the chunk borders.
 */
public final class ZoneRenderer {
	private static final float ACTIVE_WIDTH = 4.5F;
	private static final float INACTIVE_WIDTH = 3.0F;
	private static final double CORNER = 0.22;
	private static final int PENDING_COLOR = 0xFFFF3B30;
	private static final int MAX_PENDING_DRAWN = 600;

	private ZoneRenderer() {
	}

	private record Entry(SuppressorKind kind, Zone zone, boolean active) {
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return;
		}
		Region region = Region.around(client);

		// Every known zone: from loaded block entities (also inactive ones), plus register entries
		// whose block is in an unloaded chunk.
		Map<BlockPos, Entry> entries = new LinkedHashMap<>();
		for (SuppressionBlockEntity be : ClientZones.blockEntities()) {
			if (be.getZone() != null && !be.isRemoved() && be.getWorld() == client.world) {
				entries.put(be.getPos(), new Entry(be.kind(), be.getZone(), be.isActive()));
			}
		}
		for (Payloads.CutterInfo info : ClientZones.cutters()) {
			entries.putIfAbsent(info.source(), new Entry(SuppressorKind.PORTAL_CUTTER, info.zone(), true));
		}
		for (Payloads.LightZoneInfo info : ClientZones.lightInfo().values()) {
			entries.putIfAbsent(info.source(), new Entry(SuppressorKind.LIGHT_SUPPRESSOR, info.zone(), true));
		}
		for (Map.Entry<BlockPos, Entry> e : entries.entrySet()) {
			if (ClientConfig.isZoneShown(e.getKey())) {
				Entry entry = e.getValue();
				drawZone(region, entry.zone(), entry.kind().color(entry.active()), entry.active() ? ACTIVE_WIDTH : INACTIVE_WIDTH);
			}
		}

		// Light zones whose columns are still to be recorded.
		int drawn = 0;
		for (Payloads.LightZoneInfo info : ClientZones.lightInfo().values()) {
			if (!ClientConfig.isZoneShown(info.source())) {
				continue;
			}
			for (int i = 0; i < info.pendingNear().size() && drawn < MAX_PENDING_DRAWN; i++) {
				long key = info.pendingNear().getLong(i);
				var zone = ClientZones.lightZone(info.source());
				if (zone != null && zone.hasColumn(key)) {
					continue; // recorded meanwhile (the column data reached us before the next list)
				}
				if (drawPendingColumn(client, region, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key))) {
					drawn++;
				}
			}
		}

		drawHeldSelection(client, region);
	}

	/** The loaded area around the player (horizontal) and the world's height. */
	private record Region(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
		static Region around(MinecraftClient client) {
			int r = client.options.getClampedViewDistance();
			ChunkPos c = client.player.getChunkPos();
			return new Region((c.x - r) * 16.0, (c.x + r + 1) * 16.0, client.world.getBottomY(), client.world.getTopYInclusive() + 1.0,
					(c.z - r) * 16.0, (c.z + r + 1) * 16.0);
		}

		boolean inX(double x) {
			return x >= minX && x <= maxX;
		}

		boolean inY(double y) {
			return y >= minY && y <= maxY;
		}

		boolean inZ(double z) {
			return z >= minZ && z <= maxZ;
		}
	}

	private static void drawZone(Region region, Zone zone, int color, float width) {
		double x0 = zone.minX();
		double y0 = zone.minY();
		double z0 = zone.minZ();
		double x1 = zone.maxX() + 1.0;
		double y1 = zone.maxY() + 1.0;
		double z1 = zone.maxZ() + 1.0;
		// Edges along X
		for (double y : new double[]{y0, y1}) {
			for (double z : new double[]{z0, z1}) {
				if (region.inY(y) && region.inZ(z)) {
					segment(new Vec3d(Math.max(x0, region.minX), y, z), new Vec3d(Math.min(x1, region.maxX), y, z), color, width);
				}
			}
		}
		// Edges along Z
		for (double y : new double[]{y0, y1}) {
			for (double x : new double[]{x0, x1}) {
				if (region.inY(y) && region.inX(x)) {
					segment(new Vec3d(x, y, Math.max(z0, region.minZ)), new Vec3d(x, y, Math.min(z1, region.maxZ)), color, width);
				}
			}
		}
		// Vertical edges
		for (double x : new double[]{x0, x1}) {
			for (double z : new double[]{z0, z1}) {
				if (region.inX(x) && region.inZ(z)) {
					segment(new Vec3d(x, Math.max(y0, region.minY), z), new Vec3d(x, Math.min(y1, region.maxY), z), color, width);
				}
			}
		}
		// Corner markers
		int fill = ColorHelper.withAlpha(230, color);
		for (double x : new double[]{x0, x1}) {
			for (double y : new double[]{y0, y1}) {
				for (double z : new double[]{z0, z1}) {
					if (region.inX(x) && region.inY(y) && region.inZ(z)) {
						net.minecraft.world.debug.gizmo.GizmoDrawing.box(
								new Box(x - CORNER, y - CORNER, z - CORNER, x + CORNER, y + CORNER, z + CORNER),
								DrawStyle.filledAndStroked(0xFF000000 | 0x202020, 1.5F, fill));
					}
				}
			}
		}
	}

	private static void segment(Vec3d a, Vec3d b, int color, float width) {
		if (a.squaredDistanceTo(b) < 1.0E-6 || a.x > b.x || a.y > b.y || a.z > b.z) {
			return;
		}
		net.minecraft.world.debug.gizmo.GizmoDrawing.line(a, b, color, width);
	}

	private static boolean drawPendingColumn(MinecraftClient client, Region region, int cx, int cz) {
		ChunkPos player = client.player.getChunkPos();
		int r = client.options.getClampedViewDistance() + 16;
		if (Math.abs(cx - player.x) > r || Math.abs(cz - player.z) > r) {
			return false;
		}
		double x0 = cx * 16.0;
		double z0 = cz * 16.0;
		double x1 = x0 + 16.0;
		double z1 = z0 + 16.0;
		double y0 = region.minY;
		double y1 = region.maxY;
		DrawStyle wall = DrawStyle.filled(ColorHelper.withAlpha(46, PENDING_COLOR));
		wall(new Vec3d(x0, y0, z0), new Vec3d(x1, y1, z0), Direction.NORTH, wall);
		wall(new Vec3d(x0, y0, z1), new Vec3d(x1, y1, z1), Direction.NORTH, wall);
		wall(new Vec3d(x0, y0, z0), new Vec3d(x0, y1, z1), Direction.WEST, wall);
		wall(new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z1), Direction.WEST, wall);
		for (double x : new double[]{x0, x1}) {
			for (double z : new double[]{z0, z1}) {
				net.minecraft.world.debug.gizmo.GizmoDrawing.line(new Vec3d(x, y0, z), new Vec3d(x, y1, z), PENDING_COLOR, 2.5F);
			}
		}
		return true;
	}

	private static void wall(Vec3d min, Vec3d max, Direction facing, DrawStyle style) {
		net.minecraft.world.debug.gizmo.GizmoDrawing.face(min, max, facing, style);
		net.minecraft.world.debug.gizmo.GizmoDrawing.face(min, max, facing.getOpposite(), style);
	}

	private static void drawHeldSelection(MinecraftClient client, Region region) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = client.player.getStackInHand(hand);
			if (!(stack.getItem() instanceof SelectorItem selector)) {
				continue;
			}
			Selection selection = stack.get(ModContent.SELECTION);
			if (selection == null || !selection.dimension().equals(client.world.getRegistryKey())) {
				return;
			}
			int color = selector.kind().color(true);
			selection.pos1().ifPresent(pos -> marker(pos, color, "1"));
			selection.pos2().ifPresent(pos -> marker(pos, ColorHelper.withAlpha(255, 0xFFFFFF), "2"));
			selection.zone().ifPresent(zone -> drawZone(region, zone, ColorHelper.withAlpha(255, color), 2.0F));
			return;
		}
	}

	private static void marker(BlockPos pos, int color, String label) {
		net.minecraft.world.debug.gizmo.GizmoDrawing.box(pos, 0.03F, DrawStyle.filledAndStroked(color, 3.0F, ColorHelper.withAlpha(70, color)));
		net.minecraft.world.debug.gizmo.GizmoDrawing.blockLabel(label, pos, 0, color, 1.2F);
	}
}
