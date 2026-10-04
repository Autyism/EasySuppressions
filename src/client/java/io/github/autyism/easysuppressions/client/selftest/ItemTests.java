package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.block.PortalCutterBlockEntity;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.client.TestHooks;
import io.github.autyism.easysuppressions.client.screen.ConfigScreen;
import io.github.autyism.easysuppressions.client.screen.CutterScreen;
import io.github.autyism.easysuppressions.client.screen.SuppressorScreen;
import io.github.autyism.easysuppressions.item.BoundZone;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.autyism.easysuppressions.client.selftest.Scenes.Y;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.be;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeCutter;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeLight;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.set;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.sp;

/** Recipes, anvil binding, selectors, placement rule, item restrictions and the GUIs. */
final class ItemTests {
	private final Scenes s;
	private final SelfTest t;

	ItemTests(Scenes s) {
		this.s = s;
		this.t = s.t;
	}

	private static ItemStack craft(ServerWorld w, Object... grid) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Object o : grid) {
			stacks.add(o == null ? ItemStack.EMPTY : new ItemStack((net.minecraft.item.Item) o));
		}
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, stacks);
		Optional<RecipeEntry<net.minecraft.recipe.CraftingRecipe>> match = w.getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, w);
		return match.map(r -> r.value().craft(input, w.getRegistryManager())).orElse(ItemStack.EMPTY);
	}

	void recipes() {
		var O = Items.OBSIDIAN;
		var S = Items.NETHER_STAR;
		var cut = ModContent.PORTAL_CUTTER_BLOCK_ITEM;
		var lit = ModContent.LIGHT_SUPPRESSOR_BLOCK_ITEM;
		s.serverCheck("recipe: portal cutter block", w -> craft(w, O, S, O, S, Items.AMETHYST_BLOCK, S, O, S, O).isOf(cut));
		s.serverCheck("recipe: light suppressor block", w -> craft(w, O, S, O, S, Items.GLOWSTONE, S, O, S, O).isOf(lit));
		s.serverCheck("recipe: portal cutter selector", w -> craft(w, null, cut, null, null, cut, null, null, cut, null).isOf(ModContent.PORTAL_CUTTER_SELECTOR));
		s.serverCheck("recipe: light suppressor selector", w -> craft(w, null, lit, null, null, lit, null, null, lit, null).isOf(ModContent.LIGHT_SUPPRESSOR_SELECTOR));
		s.serverCheck("recipe: portal pickaxe", w -> craft(w, cut, cut, cut, null, Items.STICK, null, null, Items.STICK, null).isOf(ModContent.PORTAL_PICKAXE));
		s.serverCheck("recipe results are one item", w -> craft(w, O, S, O, S, Items.AMETHYST_BLOCK, S, O, S, O).getCount() == 1);
		s.serverCheck("selectors / pickaxe are no ingredient (two pickaxes do not repair-combine)",
				w -> craft(w, ModContent.PORTAL_PICKAXE, ModContent.PORTAL_PICKAXE, null, null, null, null, null, null, null).isEmpty()
						&& craft(w, ModContent.PORTAL_CUTTER_SELECTOR, null, null, null, null, null, null, null, null).isEmpty());
	}

	void anvil() {
		BlockPos anvil = new BlockPos(140, Y, 0);
		Zone zone = new Zone(138, Y - 1, -2, 146, Y + 4, 4);
		AnvilScreenHandler[] handler = new AnvilScreenHandler[1];
		s.gamemode("survival");
		s.server("anvil block", w -> set(w, anvil, Blocks.ANVIL));
		s.tpLook(140.5, Y, -2.5, anvil);
		t.onServer("open an anvil with blocks and a selector", srv -> {
			ServerWorld w = srv.getOverworld();
			ServerPlayerEntity p = sp(srv);
			set(w, anvil, Blocks.ANVIL);
			p.setExperienceLevel(5);
			p.getInventory().clear();
			p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, player) ->
					new AnvilScreenHandler(syncId, inv, ScreenHandlerContext.create(w, anvil)), Text.translatable("container.repair")));
			handler[0] = (AnvilScreenHandler) p.currentScreenHandler;
			ItemStack selector = new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR);
			selector.set(ModContent.SELECTION, new Selection(World.OVERWORLD, Optional.of(zone.min()), Optional.of(zone.max())));
			handler[0].getSlot(0).setStack(new ItemStack(ModContent.PORTAL_CUTTER_BLOCK_ITEM, 3));
			handler[0].getSlot(1).setStack(selector);
			handler[0].sendContentUpdates();
		});
		t.waitTicks("anvil screen", 10);
		t.screenshot("anvil_binding");
		t.onServer("check and take the result", srv -> {
			ServerPlayerEntity p = sp(srv);
			AnvilScreenHandler h = handler[0];
			ItemStack out = h.getSlot(2).getStack();
			BoundZone bound = out.get(ModContent.BOUND_ZONE);
			t.check("anvil output is one bound block", out.isOf(ModContent.PORTAL_CUTTER_BLOCK_ITEM) && out.getCount() == 1
					&& bound != null && bound.zone().equals(zone) && bound.dimension().equals(World.OVERWORLD));
			t.check("binding costs 1 level", h.getLevelCost() == 1);
			t.info("before taking: left {} right {} same handler {}", h.getSlot(0).getStack(), h.getSlot(1).getStack(), p.currentScreenHandler == h);
			h.onSlotClick(2, 0, SlotActionType.PICKUP, p);
			t.info("after taking: levels {} left {} right {} cursor {}", p.experienceLevel, h.getSlot(0).getStack(), h.getSlot(1).getStack(), h.getCursorStack());
			p.getInventory().insertStack(h.getCursorStack().copy());
			h.setCursorStack(ItemStack.EMPTY);
			t.check("1 level was used", p.experienceLevel == 4);
			t.check("one block was used up", h.getSlot(0).getStack().getCount() == 2);
			t.check("the selector stays (not consumed)", h.getSlot(1).getStack().isOf(ModContent.PORTAL_CUTTER_SELECTOR)
					&& h.getSlot(1).getStack().get(ModContent.SELECTION) != null);
			boolean inInventory = false;
			for (int i = 0; i < p.getInventory().size(); i++) {
				ItemStack st = p.getInventory().getStack(i);
				if (st.isOf(ModContent.PORTAL_CUTTER_BLOCK_ITEM) && st.get(ModContent.BOUND_ZONE) != null) {
					inInventory = true;
				}
			}
			t.check("the bound block is in the inventory", inInventory);
			t.check("the next block can be bound right away", h.getSlot(2).getStack().get(ModContent.BOUND_ZONE) != null);

			h.getSlot(1).setStack(new ItemStack(ModContent.LIGHT_SUPPRESSOR_SELECTOR));
			t.check("other kind of selector: no result", h.getSlot(2).getStack().isEmpty());
			ItemStack empty = new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR);
			h.getSlot(1).setStack(empty);
			t.check("selector without a selection: no result", h.getSlot(2).getStack().isEmpty());
			h.getSlot(0).setStack(new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR));
			h.getSlot(1).setStack(new ItemStack(ModContent.LIGHT_SUPPRESSOR_SELECTOR));
			t.check("two selectors cannot be combined", h.getSlot(2).getStack().isEmpty());
			h.getSlot(1).setStack(ItemStack.EMPTY);
			h.setNewItemName("renamed");
			t.check("selector cannot be renamed", h.getSlot(2).getStack().isEmpty());
			h.getSlot(0).setStack(new ItemStack(ModContent.PORTAL_PICKAXE));
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			Scenes.enchanted(srv.getOverworld(), book, Enchantments.EFFICIENCY, 5);
			h.getSlot(1).setStack(book);
			t.check("portal pickaxe takes no enchanted book", h.getSlot(2).getStack().isEmpty());
			h.getSlot(0).setStack(ItemStack.EMPTY);
			h.getSlot(1).setStack(ItemStack.EMPTY);
			p.closeHandledScreen();
		});
		s.gamemode("creative");
	}

	void selector() {
		BlockPos a = new BlockPos(150, Y, 5);
		BlockPos b = new BlockPos(153, Y + 1, 9);
		s.server("selector targets", w -> {
			set(w, a, Blocks.STONE);
			set(w, b, Blocks.STONE);
			sp(w.getServer()).getInventory().clear();
		});
		s.hold(new ItemStack(ModContent.LIGHT_SUPPRESSOR_SELECTOR));
		s.tpLook(150.5, Y, 1.5, a);
		s.leftClick();
		s.serverCheck("left click set corner 1 on the block (creative)", w -> {
			Selection sel = sp(w.getServer()).getMainHandStack().get(ModContent.SELECTION);
			return sel != null && sel.pos1().equals(Optional.of(a)) && sel.pos2().isEmpty();
		});
		s.serverCheck("left click did not break the block in creative", w -> w.getBlockState(a).isOf(Blocks.STONE));
		s.tpLook(150.5, Y, 1.5, b);
		s.rightClick();
		s.serverCheck("right click set corner 2", w -> {
			Selection sel = sp(w.getServer()).getMainHandStack().get(ModContent.SELECTION);
			return sel != null && sel.pos1().equals(Optional.of(a)) && sel.pos2().equals(Optional.of(b)) && sel.dimension().equals(World.OVERWORLD);
		});
		s.tp(147.5, Y + 4, -4.5, -30F, 30F);
		t.waitTicks("render", 10);
		t.screenshot("selector_selection");
		s.tp(150.5, Y, 1.5, 0F, -85F);
		s.leftClick();
		s.serverCheck("clicking at the sky changes nothing", w -> {
			Selection sel = sp(w.getServer()).getMainHandStack().get(ModContent.SELECTION);
			return sel != null && sel.isComplete();
		});
		s.server("selection in the Nether", w -> sp(w.getServer()).getMainHandStack().set(ModContent.SELECTION,
				new Selection(World.NETHER, Optional.of(new BlockPos(1, 70, 1)), Optional.of(new BlockPos(5, 75, 5)))));
		t.waitTicks("synced", 4);
		s.tpLook(150.5, Y, 1.5, a);
		s.leftClick();
		s.serverCheck("a corner in another dimension clears the old corners", w -> {
			Selection sel = sp(w.getServer()).getMainHandStack().get(ModContent.SELECTION);
			return sel != null && sel.dimension().equals(World.OVERWORLD) && sel.pos1().equals(Optional.of(a)) && sel.pos2().isEmpty();
		});

		// GUI import from the hand.
		BlockPos cutter = new BlockPos(156, Y, 5);
		Zone zone = new Zone(154, Y - 1, 3, 158, Y + 3, 7);
		s.server("cutter and a matching selector in the off hand", w -> {
			placeCutter(w, cutter, null);
			ItemStack sel = new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR);
			sel.set(ModContent.SELECTION, new Selection(World.OVERWORLD, Optional.of(zone.min()), Optional.of(zone.max())));
			sp(w.getServer()).setStackInHand(Hand.OFF_HAND, sel);
			sp(w.getServer()).setStackInHand(Hand.MAIN_HAND, new ItemStack(ModContent.LIGHT_SUPPRESSOR_SELECTOR));
		});
		s.tpLook(156.5, Y, 2.5, cutter);
		t.waitTicks("synced", 4);
		t.run("press 'import'", c -> ClientPlayNetworking.send(new Payloads.ScreenAction(cutter, Payloads.Action.IMPORT_SELECTOR,
				Optional.empty(), io.github.autyism.easysuppressions.light.LightSettings.DEFAULT)));
		t.waitTicks("imported", 5);
		s.serverCheck("import took the matching selector (off hand) and skipped the other kind", w -> zone.equals(be(w, cutter).getZone()));
		t.run("save a zone without the block", c -> ClientPlayNetworking.send(new Payloads.ScreenAction(cutter, Payloads.Action.SAVE_ZONE,
				Optional.of(new Zone(160, Y, 0, 162, Y, 2)), io.github.autyism.easysuppressions.light.LightSettings.DEFAULT)));
		t.waitTicks("refused", 5);
		s.serverCheck("a zone that does not contain the block is refused", w -> zone.equals(be(w, cutter).getZone()));
		t.run("check the refusal message", c -> t.check("refusal message: " + (TestHooks.lastScreenMessage == null ? "none" : TestHooks.lastScreenMessage.getString()),
				TestHooks.lastScreenMessage != null && TestHooks.lastScreenMessage.getString().equals(
						Text.translatable("easysuppressions.message.zone_must_contain_block").getString())));
		s.server("clear hands", w -> {
			sp(w.getServer()).getInventory().clear();
		});
	}

	void placement() {
		Zone zone = new Zone(160, Y - 1, 10, 164, Y + 2, 14);
		BlockPos outside = new BlockPos(170, Y - 1, 12);
		BlockPos inside = new BlockPos(162, Y - 1, 12);
		s.gamemode("survival");
		s.server("bound blocks in hand", w -> {
			ItemStack st = new ItemStack(ModContent.PORTAL_CUTTER_BLOCK_ITEM, 5);
			st.set(ModContent.BOUND_ZONE, new BoundZone(World.OVERWORLD, zone));
			sp(w.getServer()).getInventory().setSelectedSlot(0);
			sp(w.getServer()).setStackInHand(Hand.MAIN_HAND, st);
		});
		t.waitTicks("synced", 4);
		s.tpLookAt(170.5, Y, 9.5, net.minecraft.util.math.Vec3d.ofCenter(outside).add(0, 0.5, 0));
		s.rightClick();
		t.waitTicks("server answered", 5);
		s.serverCheck("placing outside the bound zone is refused", w -> w.getBlockState(outside.up()).isAir()
				&& sp(w.getServer()).getMainHandStack().getCount() == 5);
		t.run("client agrees", c -> t.check("client shows no block outside the zone", c.world.getBlockState(outside.up()).isAir()));
		t.screenshot("place_outside_zone_refused");
		s.tpLookAt(162.5, Y, 9.5, net.minecraft.util.math.Vec3d.ofCenter(inside).add(0, 0.5, 0));
		s.rightClick();
		t.waitTicks("server answered", 5);
		s.serverCheck("placing inside the bound zone works and the block takes the zone", w -> {
			SuppressionBlockEntity be = be(w, inside.up());
			return be instanceof PortalCutterBlockEntity && zone.equals(be.getZone()) && sp(w.getServer()).getMainHandStack().getCount() == 4;
		});
		s.server("clear hands", w -> sp(w.getServer()).getInventory().clear());
		s.gamemode("creative");
	}

	void restrictions() {
		t.onServer("enchanting table, grindstone, item properties", srv -> {
			ServerPlayerEntity p = sp(srv);
			EnchantmentScreenHandler table = new EnchantmentScreenHandler(1, p.getInventory());
			GrindstoneScreenHandler grind = new GrindstoneScreenHandler(2, p.getInventory());
			ItemStack sel = new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR);
			ItemStack pick = new ItemStack(ModContent.PORTAL_PICKAXE);
			t.check("selector does not go into the enchanting table", !table.getSlot(0).canInsert(sel));
			t.check("portal pickaxe does not go into the enchanting table", !table.getSlot(0).canInsert(pick));
			t.check("a normal sword still goes into the enchanting table", table.getSlot(0).canInsert(new ItemStack(Items.DIAMOND_SWORD)));
			t.check("selector does not go into the grindstone", !grind.getSlot(0).canInsert(sel) && !grind.getSlot(1).canInsert(sel));
			t.check("pickaxe is unbreakable, not enchantable, stacks to 1", !pick.isDamageable() && !pick.isEnchantable() && pick.getMaxCount() == 1);
			t.check("selectors stack to 1", sel.getMaxCount() == 1 && new ItemStack(ModContent.LIGHT_SUPPRESSOR_SELECTOR).getMaxCount() == 1);
			t.check("blocks stack to 64", new ItemStack(ModContent.PORTAL_CUTTER_BLOCK_ITEM).getMaxCount() == 64);
		});
		BlockPos lava = new BlockPos(190, Y - 1, 0);
		s.server("lava pool with a selector and the pickaxe", w -> {
			Scenes.fill(w, lava.add(-1, 0, -1), lava.add(1, 0, 1), Blocks.LAVA.getDefaultState());
			for (ItemStack st : new ItemStack[]{new ItemStack(ModContent.PORTAL_CUTTER_SELECTOR), new ItemStack(ModContent.PORTAL_PICKAXE),
					new ItemStack(Items.STICK)}) {
				ItemEntity e = new ItemEntity(w, lava.getX() + 0.5, lava.getY() + 0.6, lava.getZ() + 0.5, st);
				w.spawnEntity(e);
			}
		});
		t.waitTicks("items in lava", 80);
		s.serverCheck("selector and pickaxe survive lava, a stick burns", w -> {
			List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class, new Box(lava).expand(4), e -> true);
			boolean sel = items.stream().anyMatch(e -> e.getStack().isOf(ModContent.PORTAL_CUTTER_SELECTOR));
			boolean pick = items.stream().anyMatch(e -> e.getStack().isOf(ModContent.PORTAL_PICKAXE));
			boolean stick = items.stream().anyMatch(e -> e.getStack().isOf(Items.STICK));
			boolean aging = items.stream().allMatch(e -> e.getItemAge() > 40);
			t.info("items near lava: {}", items.stream().map(e -> e.getStack().getItem() + " age " + e.getItemAge()).toList());
			return sel && pick && !stick && aging;
		});
		s.cmd("kill @e[type=item]");
	}

	// ------------------------------------------------------------------ GUIs

	private static PressableWidget button(Screen screen, String translationKey) {
		String label = Text.translatable(translationKey).getString();
		for (Element e : screen.children()) {
			if (e instanceof PressableWidget w && w.getMessage().getString().contains(label)) {
				return w;
			}
		}
		return null;
	}

	private void press(String translationKey) {
		t.run("press " + translationKey, c -> {
			PressableWidget w = button(c.currentScreen, translationKey);
			t.check("button " + translationKey + " exists and is active", w != null && w.active);
			if (w != null) {
				w.onPress(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
			}
		});
		t.waitTicks("pressed", 6);
	}

	void screens() {
		BlockPos cutter = new BlockPos(200, Y, -20);
		BlockPos light = new BlockPos(204, Y, -20);
		s.server("blocks for the GUIs", w -> {
			placeCutter(w, cutter, new Zone(198, Y - 1, -22, 202, Y + 3, -18));
			placeLight(w, light, new Zone(203, Y - 1, -22, 207, Y + 3, -18));
			sp(w.getServer()).getInventory().clear();
		});
		s.tpLook(200.5, Y, -23.5, cutter);
		s.rightClick();
		t.waitUntilCheck("cutter GUI opens without redstone", c -> c.currentScreen instanceof CutterScreen, 40);
		t.waitTicks("gui", 10);
		t.screenshot("gui_cutter");
		t.run("close", c -> c.setScreen(null));
		s.tpLook(204.5, Y, -23.5, light);
		s.rightClick();
		t.waitTicks("refused", 10);
		t.run("light suppressor GUI needs redstone", c -> t.check("no GUI without redstone", c.currentScreen == null));
		t.screenshot("gui_light_needs_redstone");
		s.server("power the light suppressor", w -> set(w, light.down(), Blocks.REDSTONE_BLOCK));
		t.waitTicks("powered", 5);
		s.rightClick();
		t.waitUntilCheck("light suppressor GUI opens with redstone", c -> c.currentScreen instanceof SuppressorScreen, 40);
		t.waitTicks("gui", 10);
		t.screenshot("gui_light_unlocked");
		press("easysuppressions.gui.preset_mob_farm");
		press("easysuppressions.gui.lock");
		t.run("confirm dialog", c -> t.check("lock asks for confirmation", c.currentScreen instanceof ConfirmScreen));
		t.screenshot("gui_light_confirm_lock");
		press("gui.yes");
		t.waitTicks("locked", 20);
		s.serverCheck("locked through the GUI with the mob farm preset", w -> be(w, light).isActive()
				&& ((io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity) be(w, light)).getSettings()
				.equals(io.github.autyism.easysuppressions.light.LightSettings.MOB_FARM));
		t.run("back on the GUI, read-only", c -> {
			t.check("back on the light suppressor GUI", c.currentScreen instanceof SuppressorScreen);
			PressableWidget preset = button(c.currentScreen, "easysuppressions.gui.preset_simple");
			t.check("presets are disabled while locked", preset != null && !preset.active);
		});
		t.screenshot("gui_light_locked");
		press("easysuppressions.gui.unlock");
		t.screenshot("gui_light_confirm_unlock");
		press("gui.yes");
		t.waitTicks("unlocked", 20);
		s.serverCheck("unlocked through the GUI", w -> !be(w, light).isActive());
		t.run("close", c -> c.setScreen(null));
		t.run("open the settings screen", c -> c.setScreen(new ConfigScreen(null)));
		t.waitTicks("config", 10);
		t.screenshot("config_screen");
		t.run("close", c -> c.setScreen(null));
	}
}
