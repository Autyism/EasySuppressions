package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.entity.ComparatorBlockEntity;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import static io.github.autyism.easysuppressions.client.selftest.Scenes.Y;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.be;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.count;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeCutter;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeLight;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.set;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.sp;

/** Block properties: redstone in only, observers, comparators, pistons, explosions, wither, mining, removal. */
final class BlockTests {
	private final Scenes s;
	private final SelfTest t;

	BlockTests(Scenes s) {
		this.s = s;
		this.t = s.t;
	}

	void redstoneAndObservers() {
		BlockPos c1 = new BlockPos(20, Y, 0);
		BlockPos obs1 = c1.north();
		BlockPos l1 = new BlockPos(20, Y, 6);
		BlockPos obs2 = l1.north();
		s.server("observer setup", w -> {
			placeCutter(w, c1, new Zone(18, Y - 1, -2, 22, Y + 2, 2));
			placeLight(w, l1, new Zone(18, Y - 1, 5, 22, Y + 2, 8)).setSettings(LightSettings.MOB_FARM);
			// Observers watching the blocks (FACING = the side they watch).
			w.setBlockState(obs1, Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, Direction.SOUTH));
			w.setBlockState(obs2, Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, Direction.SOUTH));
		});
		t.waitTicks("observer placement pulse over", 10);
		boolean[] fired = {false};
		int[] phase = {0};
		t.onServer("start toggling", srv -> phase[0] = 0);
		t.waitUntil("toggle cutter power and light lock 6 times while watching the observers", c -> {
			c.getServer().execute(() -> {
				ServerWorld w = c.getServer().getOverworld();
				if (w.getBlockState(obs1).get(ObserverBlock.POWERED) || w.getBlockState(obs2).get(ObserverBlock.POWERED)) {
					fired[0] = true;
				}
				int p = phase[0]++;
				if (p % 8 == 0 && p < 48) {
					boolean on = (p / 8) % 2 == 0;
					// Power through a redstone block on the far side (not next to the observer).
					w.setBlockState(c1.south(), on ? Blocks.REDSTONE_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
					LightSuppressorBlockEntity ls = (LightSuppressorBlockEntity) be(w, l1);
					if (on) {
						ls.lock(w, null);
					} else {
						ls.unlock(w);
					}
				}
			});
			return phase[0] >= 60;
		}, 200);
		t.run("check observers", c -> t.check("observers never fired while the active state changed", !fired[0]));
		s.serverCheck("cutter ends unpowered", w -> !be(w, c1).isActive());

		// Comparator behind an active cutter reads nothing; strong power does not pass through.
		BlockPos c2 = new BlockPos(24, Y, 0);
		BlockPos comparator = c2.north();
		BlockPos dustAfterComparator = comparator.north();
		BlockPos c3 = new BlockPos(28, Y, 0);
		BlockPos repeater = c3.west();
		BlockPos repeaterSource = repeater.west();
		BlockPos dustBehind = c3.east();
		BlockPos c4 = new BlockPos(32, Y, 0);
		BlockPos lever = c4.north();
		BlockPos dustBesideLever = c4.south();
		s.server("redstone setup", w -> {
			placeCutter(w, c2, new Zone(23, Y - 1, -2, 25, Y + 1, 2));
			set(w, c2.down(), Blocks.REDSTONE_BLOCK);
			w.setBlockState(comparator, Blocks.COMPARATOR.getDefaultState().with(ComparatorBlock.FACING, Direction.NORTH));
			set(w, dustAfterComparator, Blocks.REDSTONE_WIRE);

			placeCutter(w, c3, new Zone(26, Y - 1, -2, 30, Y + 1, 2));
			w.setBlockState(repeater, Blocks.REPEATER.getDefaultState().with(RepeaterBlock.FACING, Direction.WEST));
			set(w, repeaterSource, Blocks.REDSTONE_BLOCK);
			set(w, dustBehind, Blocks.REDSTONE_WIRE);

			placeCutter(w, c4, new Zone(31, Y - 1, -2, 33, Y + 1, 2));
			w.setBlockState(lever, Blocks.LEVER.getDefaultState().with(LeverBlock.FACE, BlockFace.WALL)
					.with(LeverBlock.FACING, Direction.NORTH).with(LeverBlock.POWERED, true));
			w.updateNeighbors(lever, Blocks.LEVER);
			w.updateNeighbors(c4, Blocks.LEVER);
			set(w, dustBesideLever, Blocks.REDSTONE_WIRE);
		});
		t.waitTicks("redstone settles", 10);
		s.serverCheck("cutter powered by the redstone block below", w -> be(w, c2).isActive());
		s.serverCheck("comparator behind the active cutter outputs 0",
				w -> w.getBlockEntity(comparator) instanceof ComparatorBlockEntity cbe && cbe.getOutputSignal() == 0
						&& w.getBlockState(dustAfterComparator).get(RedstoneWireBlock.POWER) == 0);
		s.serverCheck("cutter powered by a repeater", w -> be(w, c3).isActive());
		s.serverCheck("repeater power does not pass through the cutter", w -> w.getBlockState(dustBehind).get(RedstoneWireBlock.POWER) == 0);
		s.serverCheck("cutter powered by a lever on it", w -> be(w, c4).isActive());
		s.serverCheck("lever power does not pass through the cutter", w -> w.getBlockState(dustBesideLever).get(RedstoneWireBlock.POWER) == 0);
		s.serverCheck("blocks have no comparator output",
				w -> !ModContent.PORTAL_CUTTER_BLOCK.getDefaultState().hasComparatorOutput()
						&& !ModContent.LIGHT_SUPPRESSOR_BLOCK.getDefaultState().hasComparatorOutput()
						&& !ModContent.PORTAL_CUTTER_BLOCK.getDefaultState().emitsRedstonePower());
		s.serverCheck("blocks are wither and dragon immune",
				w -> ModContent.PORTAL_CUTTER_BLOCK.getDefaultState().isIn(BlockTags.WITHER_IMMUNE)
						&& ModContent.LIGHT_SUPPRESSOR_BLOCK.getDefaultState().isIn(BlockTags.WITHER_IMMUNE)
						&& ModContent.PORTAL_CUTTER_BLOCK.getDefaultState().isIn(BlockTags.DRAGON_IMMUNE)
						&& ModContent.LIGHT_SUPPRESSOR_BLOCK.getDefaultState().isIn(BlockTags.DRAGON_IMMUNE)
						&& ModContent.PORTAL_CUTTER_BLOCK.getBlastResistance() >= 3600000.0F);
		s.tpLook(26.5, Y + 2, -5.5, new BlockPos(26, Y, 0));
		t.waitTicks("render", 10);
		t.screenshot("redstone_in_only");
	}

	void pistons() {
		BlockPos piston = new BlockPos(40, Y, 0);
		BlockPos pushed = piston.east();
		BlockPos sticky = new BlockPos(40, Y, 4);
		BlockPos head = sticky.east();
		BlockPos pulled = head.east();
		s.server("piston setup", w -> {
			w.setBlockState(piston, Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
			placeLight(w, pushed, null);
			set(w, piston.up(), Blocks.REDSTONE_BLOCK);
			w.setBlockState(sticky, Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
			set(w, sticky.up(), Blocks.REDSTONE_BLOCK);
		});
		t.waitTicks("pistons move", 10);
		s.serverCheck("piston cannot push the block", w -> w.getBlockState(pushed).isOf(ModContent.LIGHT_SUPPRESSOR_BLOCK)
				&& !w.getBlockState(piston).get(PistonBlock.EXTENDED));
		s.server("put a block in front of the extended sticky piston, then unpower it", w -> {
			placeCutter(w, pulled, null);
			set(w, sticky.up(), Blocks.AIR);
		});
		t.waitTicks("sticky piston retracts", 10);
		s.serverCheck("sticky piston cannot pull the block", w -> w.getBlockState(pulled).isOf(ModContent.PORTAL_CUTTER_BLOCK)
				&& w.getBlockState(head).isAir());
	}

	void explosions() {
		BlockPos center = new BlockPos(60, Y, 0);
		BlockPos cutter = center.west();
		BlockPos light = center.east();
		BlockPos control = center.north(2);
		s.server("explosion setup", w -> {
			placeCutter(w, cutter, null);
			placeLight(w, light, null);
			set(w, control, Blocks.STONE);
		});
		s.cmd("summon tnt 60 -60 0 {fuse:0}");
		t.waitTicks("tnt", 10);
		s.cmd("summon creeper 60 -60 1 {Fuse:0s,ignited:1b}");
		t.waitTicks("creeper", 10);
		s.cmd("summon end_crystal 60 -60 -1 {ShowBottom:0b}");
		t.waitTicks("crystal", 5);
		s.cmd("damage @e[type=end_crystal,limit=1,sort=nearest] 1");
		t.waitTicks("crystal explosion", 10);
		s.cmd("summon wither_skull 61 -57 0 {dangerous:1b,Motion:[0.0,-1.0,0.0],power:[0.0,-0.5,0.0]}",
				"summon wither_skull 59 -57 0 {dangerous:1b,Motion:[0.0,-1.0,0.0],power:[0.0,-0.5,0.0]}");
		t.waitTicks("wither skulls", 30);
		s.serverCheck("control stone was blown up", w -> !w.getBlockState(control).isOf(Blocks.STONE));
		s.serverCheck("blocks survived TNT, creeper, end crystal and blue wither skulls",
				w -> w.getBlockState(cutter).isOf(ModContent.PORTAL_CUTTER_BLOCK) && w.getBlockState(light).isOf(ModContent.LIGHT_SUPPRESSOR_BLOCK));
		s.tpLook(60.5, Y + 2, -6.5, center);
		t.waitTicks("render", 10);
		t.screenshot("after_explosions");
	}

	void wither() {
		// A wither in a 3x3x4 box of our blocks and dirt; after being hurt it breaks the blocks around it.
		BlockPos base = new BlockPos(80, Y, 0);
		s.server("wither cage", w -> {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = 0; dy <= 4; dy++) {
						boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 4;
						if (!wall) {
							continue;
						}
						BlockPos p = base.add(dx, dy, dz);
						if (dy == 4 || (dx + dz) % 2 == 0) {
							w.setBlockState(p, ((dx + dy + dz) & 1) == 0 ? ModContent.PORTAL_CUTTER_BLOCK.getDefaultState()
									: ModContent.LIGHT_SUPPRESSOR_BLOCK.getDefaultState());
						} else {
							set(w, p, Blocks.DIRT);
						}
					}
				}
			}
			// A ring of our blocks (dy 1) and of dirt (dy 2) right around the wither: always inside its
			// 3x4x3 breaking box, and it cannot move out of it.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					if (((dx + dz) & 1) == 0) {
						placeCutter(w, base.add(dx, 1, dz), null);
					} else {
						placeLight(w, base.add(dx, 1, dz), null);
					}
					set(w, base.add(dx, 2, dz), Blocks.DIRT);
				}
			}
		});
		s.cmd("difficulty normal", "summon wither 80.5 -60 0.5 {Invul:0,PersistenceRequired:1b}");
		t.waitTicks("wither spawned", 20);
		s.cmd("damage @e[type=wither,limit=1] 4 minecraft:player_attack by @s");
		t.waitTicks("wither breaks blocks around it (20 tick cooldown)", 50);
		s.serverCheck("the hurt wither broke the dirt ring around it", w -> count(w, base.add(-1, 2, -1), base.add(1, 2, 1), Blocks.DIRT) == 0);
		s.serverCheck("the hurt wither did not break our blocks", w -> count(w, base.add(-1, 1, -1), base.add(1, 1, 1), ModContent.PORTAL_CUTTER_BLOCK)
				+ count(w, base.add(-1, 1, -1), base.add(1, 1, 1), ModContent.LIGHT_SUPPRESSOR_BLOCK) == 8);
		s.tpLook(80.5, Y + 2, -7.5, base);
		t.waitTicks("render", 5);
		t.screenshot("wither_cage");
		s.cmd("kill @e[type=wither]", "kill @e[type=item]", "kill @e[type=experience_orb]", "difficulty peaceful");
	}

	void mining() {
		BlockPos cutter = new BlockPos(100, Y, 20);
		BlockPos light = new BlockPos(102, Y, 20);
		BlockPos obsidian = new BlockPos(104, Y, 20);
		s.server("mining setup", w -> {
			placeCutter(w, cutter, null);
			placeLight(w, light, null);
			set(w, obsidian, Blocks.OBSIDIAN);
		});
		s.gamemode("survival");
		t.onServer("efficiency V pickaxe and haste II", srv -> {
			ServerWorld w = srv.getOverworld();
			sp(srv).getInventory().setSelectedSlot(0);
			sp(srv).setStackInHand(Hand.MAIN_HAND, Scenes.pickaxe(w));
			Scenes.haste(sp(srv), 1);
		});
		s.tpLook(100.5, Y, 18.0, cutter);
		s.mine("cutter with efficiency V + haste II (10 s)", cutter, 200, 400);
		s.serverCheck("no drops from the cutter", w -> w.getEntitiesByClass(ItemEntity.class, new Box(cutter).expand(6), e -> true).isEmpty());

		s.cmd("easysuppressions config blockBreakSeconds 3");
		t.waitTicks("config synced", 5);
		t.onServer("silk touch, mining fatigue III", srv -> {
			ServerWorld w = srv.getOverworld();
			sp(srv).setStackInHand(Hand.MAIN_HAND, Scenes.enchanted(w, new ItemStack(Items.NETHERITE_PICKAXE), Enchantments.SILK_TOUCH, 1));
			sp(srv).removeStatusEffect(StatusEffects.HASTE);
			sp(srv).addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 20 * 600, 2));
		});
		s.tpLook(102.5, Y, 18.0, light);
		s.mine("light suppressor with silk touch + mining fatigue III after setting 3 s", light, 60, 200);
		s.serverCheck("no drops from the light suppressor (silk touch)", w -> w.getEntitiesByClass(ItemEntity.class, new Box(light).expand(6), e -> true).isEmpty());

		s.server("bare hands, in water", w -> {
			placeCutter(w, cutter, null);
			ServerWorld sw = w;
			sp(sw.getServer()).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			sp(sw.getServer()).removeStatusEffect(StatusEffects.MINING_FATIGUE);
		});
		// A 1x1 water hole walled with glass, the cutter as its south wall: no current pushes the player.
		s.cmd("fill 99 -60 18 101 -58 19 glass", "fill 100 -60 19 100 -59 19 water");
		s.tpLook(100.5, Y, 19.5, cutter);
		t.waitTicks("in water", 10);
		t.run("check the player is in water", c -> t.check("player is in water", c.player.isTouchingWater()));
		s.mine("cutter by hand while standing in water (3 s)", cutter, 60, 200);
		s.cmd("fill 99 -60 18 101 -58 19 air");
		s.cmd("easysuppressions config blockBreakSeconds 10");

		// Portal pickaxe on anything else: nothing, in survival and creative.
		t.onServer("hold the portal pickaxe", srv -> sp(srv).setStackInHand(Hand.MAIN_HAND, new ItemStack(ModContent.PORTAL_PICKAXE)));
		s.tpLook(104.5, Y, 18.0, obsidian);
		s.mineNoEffect("portal pickaxe on obsidian (survival)", obsidian, 60);
		s.gamemode("creative");
		s.tpLook(104.5, Y, 18.0, obsidian);
		s.mineNoEffect("portal pickaxe on obsidian (creative)", obsidian, 30);
		t.onServer("hold a selector", srv -> sp(srv).setStackInHand(Hand.MAIN_HAND, new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR)));
		s.mineNoEffect("selector held on obsidian (creative)", obsidian, 30);

		// Creative breaks the blocks at once.
		s.server("creative setup", w -> {
			placeLight(w, light, null);
			sp(w.getServer()).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		});
		s.tpLook(102.5, Y, 18.0, light);
		s.mine("light suppressor in creative (instant)", light, 0, 40);
	}

	void registryCleanup() {
		BlockPos cutter = new BlockPos(120, Y, 0);
		BlockPos light = new BlockPos(124, Y, 0);
		s.server("registered blocks", w -> {
			placeCutter(w, cutter, new Zone(119, Y - 1, -1, 121, Y + 2, 1));
			set(w, cutter.down(), Blocks.REDSTONE_BLOCK);
			LightSuppressorBlockEntity ls = placeLight(w, light, new Zone(123, Y - 1, -1, 125, Y + 2, 1));
			ls.setSettings(LightSettings.SIMPLE);
			ls.lock(w, null);
		});
		t.waitTicks("registered", 5);
		s.serverCheck("cutter zone registered", w -> ServerZones.of(w).cutters().stream().anyMatch(e -> e.source().equals(cutter)));
		s.serverCheck("light zone registered", w -> ServerZones.of(w).lightEntry(light) != null);
		t.run("clear chat", c -> c.inGameHud.getChatHud().clear(false));
		s.cmd("easysuppressions zones list");
		t.waitTicks("list shown", 5);
		t.screenshotKeepChat("zones_list_registered");
		s.cmd("setblock 120 -60 0 stone", "setblock 124 -60 0 glass");
		t.waitTicks("replaced", 5);
		s.serverCheck("/setblock removed the cutter zone from the register",
				w -> ServerZones.of(w).cutters().stream().noneMatch(e -> e.source().equals(cutter)));
		s.serverCheck("/setblock removed the light zone from the register", w -> ServerZones.of(w).lightEntry(light) == null
				&& ServerZones.of(w).lightZone(light) == null);
		t.run("clear chat", c -> c.inGameHud.getChatHud().clear(false));
		s.cmd("easysuppressions zones list");
		t.waitTicks("list shown", 5);
		t.screenshotKeepChat("zones_list_after_setblock");

		// /fill and a block placed by "another mod" (plain setBlockState).
		s.server("again, removed with /fill and setBlockState", w -> {
			placeCutter(w, cutter, new Zone(119, Y - 1, -1, 121, Y + 2, 1));
			set(w, cutter.down(), Blocks.REDSTONE_BLOCK);
		});
		t.waitTicks("registered again", 5);
		s.serverCheck("cutter registered again", w -> ServerZones.of(w).cutters().stream().anyMatch(e -> e.source().equals(cutter)));
		s.cmd("fill 119 -60 -1 121 -60 1 air");
		t.waitTicks("filled", 3);
		s.serverCheck("/fill removed the cutter zone", w -> ServerZones.of(w).cutters().stream().noneMatch(e -> e.source().equals(cutter)));
		s.server("cutter again, replaced by setBlockState without block updates", w -> {
			placeCutter(w, cutter, new Zone(119, Y - 1, -1, 121, Y + 2, 1));
			set(w, cutter.down(), Blocks.REDSTONE_BLOCK);
		});
		t.waitTicks("registered again", 5);
		s.server("replace", w -> w.setBlockState(cutter, Blocks.DIRT.getDefaultState(), net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.NOTIFY_LISTENERS));
		s.serverCheck("raw setBlockState removed the cutter zone", w -> ServerZones.of(w).cutters().stream().noneMatch(e -> e.source().equals(cutter)));
	}
}
