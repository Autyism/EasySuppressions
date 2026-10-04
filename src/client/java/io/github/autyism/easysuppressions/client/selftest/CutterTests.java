package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import static io.github.autyism.easysuppressions.client.selftest.Scenes.Y;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.count;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeCutter;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.set;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.sp;

/**
 * The acceptance test of the portal cutter: a lit 10x10 portal, cutter powered, holes mined with
 * the portal pickaxe, a block placed into a hole (stays), power off and another block placed
 * (the portal breaks the vanilla way). Plus: the pickaxe outside a zone breaks a whole portal.
 */
final class CutterTests {
	private final Scenes s;
	private final SelfTest t;

	CutterTests(Scenes s) {
		this.s = s;
		this.t = s.t;
	}

	void run() {
		int x0 = 300;
		int z = 30;
		BlockPos interiorMin = new BlockPos(x0 + 1, Y + 1, z);
		BlockPos interiorMax = new BlockPos(x0 + 10, Y + 10, z);
		BlockPos cutter = new BlockPos(x0 + 5, Y, z + 2);
		BlockPos power = cutter.south();
		Zone zone = new Zone(x0, Y, z - 1, x0 + 11, Y + 11, z + 2);
		s.server("obsidian frame 12x12, light it", w -> {
			for (int i = 0; i <= 11; i++) {
				set(w, new BlockPos(x0 + i, Y, z), Blocks.OBSIDIAN);
				set(w, new BlockPos(x0 + i, Y + 11, z), Blocks.OBSIDIAN);
				set(w, new BlockPos(x0, Y + i, z), Blocks.OBSIDIAN);
				set(w, new BlockPos(x0 + 11, Y + i, z), Blocks.OBSIDIAN);
			}
			set(w, interiorMin, Blocks.FIRE);
		});
		t.waitTicks("portal lit", 5);
		s.serverCheck("a 10x10 portal was lit", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 100);
		s.server("cutter with the portal zone, powered", w -> {
			placeCutter(w, cutter, zone);
			set(w, power, Blocks.REDSTONE_BLOCK);
		});
		t.waitTicks("registered", 5);
		s.serverCheck("cutter active and zone registered", w -> ServerZones.of(w).cutters().stream().anyMatch(e -> e.zone().equals(zone)));
		t.run("client knows the protected zone", c -> t.check("client register has the zone",
				((io.github.autyism.easysuppressions.world.ZoneHolderAccess) c.world).easysuppressions$zones().isPortalProtected(x0 + 5, Y + 5, z)));

		s.gamemode("survival");
		t.onServer("portal pickaxe in hand", srv -> {
			sp(srv).getInventory().setSelectedSlot(0);
			sp(srv).setStackInHand(Hand.MAIN_HAND, new ItemStack(ModContent.PORTAL_PICKAXE));
		});
		BlockPos[] holes = {new BlockPos(x0 + 5, Y + 4, z), new BlockPos(x0 + 6, Y + 4, z), new BlockPos(x0 + 5, Y + 5, z), new BlockPos(x0 + 6, Y + 5, z)};
		boolean first = true;
		s.server("glass to stand on in front of the portal", w -> Scenes.fill(w, new BlockPos(x0 + 3, Y + 2, z - 3), new BlockPos(x0 + 8, Y + 2, z - 2),
				Blocks.GLASS.getDefaultState()));
		for (BlockPos hole : holes) {
			s.tpLook(hole.getX() + 0.5, Y + 3, z - 1.7, hole);
			s.mine("portal pickaxe on a protected portal block" + (first ? " (1 s)" : ""), hole, 20, 80);
			first = false;
		}
		t.waitTicks("updates done", 10);
		s.serverCheck("the 4 holes are air, the other 96 portal blocks stay", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 96
				&& w.getBlockState(holes[0]).isAir() && w.getBlockState(holes[3]).isAir());
		t.run("client agrees (no ghost breakage)", c -> t.check("client also has 96 portal blocks", count(c.world, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 96));
		s.serverCheck("nothing dropped", w -> w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
				net.minecraft.util.math.Box.enclosing(interiorMin, interiorMax).expand(3), e -> true).isEmpty());
		s.server("remove the glass", w -> Scenes.fill(w, new BlockPos(x0 + 3, Y + 2, z - 3), new BlockPos(x0 + 8, Y + 2, z - 2),
				Blocks.AIR.getDefaultState()));
		s.gamemode("creative");
		s.tp(x0 + 6.0, Y + 1, z - 13.5, 0F, -12F);
		t.waitTicks("render", 20);
		t.screenshot("portal_cut_powered");

		s.server("powered: place a block into a hole", w -> w.setBlockState(holes[0], Blocks.STONE.getDefaultState()));
		t.waitTicks("updates", 10);
		s.serverCheck("powered: the portal stays after a neighbour changes", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 96);
		s.server("break a frame block too", w -> w.setBlockState(new BlockPos(x0, Y + 5, z), Blocks.AIR.getDefaultState()));
		t.waitTicks("updates", 10);
		s.serverCheck("powered: the portal stays even with a broken frame", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 96);
		s.server("restore the frame, cut the power", w -> {
			w.setBlockState(new BlockPos(x0, Y + 5, z), Blocks.OBSIDIAN.getDefaultState());
			w.setBlockState(power, Blocks.AIR.getDefaultState());
		});
		t.waitTicks("unpowered", 10);
		s.serverCheck("unpowered: zone left the register", w -> ServerZones.of(w).cutters().stream().noneMatch(e -> e.zone().equals(zone)));
		s.serverCheck("unpowered: without an update the portal just stays", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 96);
		s.tp(x0 + 6.0, Y + 1, z - 13.5, 0F, -12F);
		t.waitTicks("render", 10);
		t.screenshot("portal_unpowered_before_update");
		s.server("unpowered: place a block into another hole", w -> w.setBlockState(holes[1], Blocks.STONE.getDefaultState()));
		t.waitTicks("updates", 10);
		s.serverCheck("unpowered: the portal breaks the vanilla way", w -> count(w, interiorMin, interiorMax, Blocks.NETHER_PORTAL) == 0);
		t.screenshot("portal_broken_after_power_off");

		// Pickaxe outside any zone: the whole portal goes.
		int x1 = 330;
		BlockPos min2 = new BlockPos(x1 + 1, Y + 1, z);
		BlockPos max2 = new BlockPos(x1 + 3, Y + 4, z);
		s.server("small portal outside any zone", w -> {
			for (int i = 0; i <= 4; i++) {
				set(w, new BlockPos(x1 + i, Y, z), Blocks.OBSIDIAN);
				set(w, new BlockPos(x1 + i, Y + 5, z), Blocks.OBSIDIAN);
			}
			for (int i = 0; i <= 5; i++) {
				set(w, new BlockPos(x1, Y + i, z), Blocks.OBSIDIAN);
				set(w, new BlockPos(x1 + 4, Y + i, z), Blocks.OBSIDIAN);
			}
			set(w, min2, Blocks.FIRE);
		});
		t.waitTicks("lit", 5);
		s.serverCheck("small portal lit", w -> count(w, min2, max2, Blocks.NETHER_PORTAL) == 12);
		BlockPos target = new BlockPos(x1 + 2, Y + 2, z);
		s.gamemode("survival");
		s.tpLook(x1 + 2.5, Y, z - 2.0, target);
		s.mine("portal pickaxe outside a zone", target, 20, 80);
		t.waitTicks("updates", 10);
		s.serverCheck("outside a zone the whole portal breaks", w -> count(w, min2, max2, Blocks.NETHER_PORTAL) == 0);
		s.server("clear hand", w -> sp(w.getServer()).getInventory().clear());
		s.gamemode("creative");
	}
}
