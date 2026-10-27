package io.github.autyism.easysuppressions.vanilla;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.light.ChannelMode;
import io.github.autyism.easysuppressions.light.ChannelSetting;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.net.ServerNet;
import io.github.autyism.easysuppressions.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongList;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The block GUIs for players without the mod, as chest screens the server shows (item buttons; an anvil
 * for typing a corner). Same actions as the mod's screens, carried out by the same server code
 * (ServerNet.handleAction), which checks reach, redstone and the lock again for every one of them.
 */
public final class VanillaScreens {
	public static final Identifier ACTION = EasySuppressions.id("gui");

	/** What the player typed but has not saved yet, and the result of the last action. */
	private record Draft(BlockPos pos, @Nullable String corner1, @Nullable String corner2, @Nullable LightSettings settings,
						 @Nullable Text message, boolean error) {
		Draft withMessage(@Nullable Text text, boolean err) {
			return new Draft(pos, corner1, corner2, settings, text, err);
		}
	}

	private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

	private VanillaScreens() {
	}

	public static void forget(ServerPlayerEntity player) {
		DRAFTS.remove(player.getUuid());
	}

	/** A message from the last action, shown at the top of the screen when it opens again. */
	public static void note(ServerPlayerEntity player, BlockPos pos, Text text, boolean error) {
		Draft draft = draft(player, pos);
		DRAFTS.put(player.getUuid(), draft.withMessage(EsLang.translate(text, EsClients.language(player)), error));
	}

	private static Draft draft(ServerPlayerEntity player, BlockPos pos) {
		Draft draft = DRAFTS.get(player.getUuid());
		return draft != null && draft.pos.equals(pos) ? draft : new Draft(pos.toImmutable(), null, null, null, null, false);
	}

	public static void open(ServerPlayerEntity player, SuppressionBlockEntity be) {
		Draft draft = draft(player, be.getPos());
		// the message is shown once
		DRAFTS.put(player.getUuid(), draft.withMessage(null, false));
		if (be instanceof LightSuppressorBlockEntity light) suppressor(player, light, draft);
		else cutter(player, be, draft);
	}

	// ------------------------------------------------------------------ screens

	private static void cutter(ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft) {
		BlockPos pos = be.getPos();
		SimpleGui gui = chest(player, 3, EsLang.get(player, "block.easysuppressions.portal_cutter_block"));
		List<Text> lore = new java.util.ArrayList<>();
		message(lore, draft);
		lore.add(white(EsLang.get(player, "easysuppressions.gui.status",
				EsLang.get(player, be.isActive() ? "easysuppressions.gui.cutter_active" : "easysuppressions.gui.cutter_inactive"))));
		lore.add(white(zoneLine(player, be.getZone())));
		lore.add(gray(EsLang.get(player, "easysuppressions.gui.cutter_hint")));
		lore.add(gray(EsLang.get(player, "easysuppressions.vanilla.selector_hint")));
		gui.setSlot(4, info(Items.PURPLE_GLAZED_TERRACOTTA, Text.literal(EsLang.get(player, "block.easysuppressions.portal_cutter_block")), lore));
		cornerButtons(gui, player, be, draft, 10, 11);
		gui.setSlot(13, button(Items.LIME_CONCRETE, EsLang.get(player, "easysuppressions.gui.save_zone"), List.of(), () -> act(player, pos, "save_zone")));
		gui.setSlot(14, button(Items.AMETHYST_SHARD, EsLang.get(player, "easysuppressions.gui.import"), List.of(), () -> act(player, pos, "import")));
		gui.setSlot(16, button(Items.ENDER_EYE, EsLang.get(player, "easysuppressions.gui.show_zone"), List.of(), () -> {
			gui.close();
			act(player, pos, "show");
		}));
		gui.setSlot(22, button(Items.BARRIER, EsLang.get(player, "easysuppressions.vanilla.close"), List.of(), gui::close));
		gui.open();
	}

	private static void suppressor(ServerPlayerEntity player, LightSuppressorBlockEntity be, Draft draft) {
		BlockPos pos = be.getPos();
		boolean locked = be.isLocked();
		List<Text> lore = new java.util.ArrayList<>();
		message(lore, draft);
		lore.add(white(EsLang.get(player, "easysuppressions.gui.status",
				EsLang.get(player, locked ? "easysuppressions.gui.locked" : "easysuppressions.gui.unlocked"))));
		lore.add(white(zoneLine(player, be.getZone())));
		Payloads.ScreenData data = ServerNet.screenData(be);
		long pending = data.total() - data.recorded();
		if (locked && pending > 0) {
			lore.add(white(EsLang.get(player, "easysuppressions.gui.pending", pending, data.total())));
			LongList nearest = data.nearestPending();
			if (!nearest.isEmpty()) {
				StringBuilder list = new StringBuilder(EsLang.get(player, "easysuppressions.gui.pending_nearest"));
				for (int i = 0; i < nearest.size(); i++) {
					long key = nearest.getLong(i);
					list.append(i == 0 ? " " : ", ").append(ChunkPos.getPackedX(key)).append(" ").append(ChunkPos.getPackedZ(key));
				}
				lore.add(gray(list.toString()));
			}
		}
		String title = EsLang.get(player, "block.easysuppressions.light_suppressor_block");
		if (locked) {
			LightSettings s = be.getSettings();
			lore.add(white(EsLang.get(player, "easysuppressions.gui.sky_light") + ": " + describe(player, s.sky())
					+ "   " + EsLang.get(player, "easysuppressions.gui.block_light") + ": " + describe(player, s.block())));
			lore.add(gray(EsLang.get(player, "easysuppressions.gui.locked_hint")));
			SimpleGui gui = chest(player, 3, title);
			gui.setSlot(4, info(Items.ORANGE_GLAZED_TERRACOTTA, Text.literal(title), lore));
			gui.setSlot(11, button(Items.TRIPWIRE_HOOK, EsLang.get(player, "easysuppressions.gui.unlock"), List.of(), () -> act(player, pos, "unlock_ask")));
			gui.setSlot(15, button(Items.ENDER_EYE, EsLang.get(player, "easysuppressions.gui.show_zone"), List.of(), () -> {
				gui.close();
				act(player, pos, "show");
			}));
			gui.setSlot(22, button(Items.BARRIER, EsLang.get(player, "easysuppressions.vanilla.close"), List.of(), gui::close));
			gui.open();
			return;
		}
		lore.add(gray(EsLang.get(player, "easysuppressions.gui.suppressor_hint")));
		lore.add(gray(EsLang.get(player, "easysuppressions.vanilla.selector_hint")));
		LightSettings settings = draft.settings != null ? draft.settings : be.getSettings();
		SimpleGui gui = chest(player, 6, title);
		gui.setSlot(4, info(Items.ORANGE_GLAZED_TERRACOTTA, Text.literal(title), lore));
		cornerButtons(gui, player, be, draft, 10, 11);
		gui.setSlot(13, button(Items.LIME_CONCRETE, EsLang.get(player, "easysuppressions.gui.save_zone"), List.of(), () -> act(player, pos, "save_zone")));
		gui.setSlot(14, button(Items.BLAZE_ROD, EsLang.get(player, "easysuppressions.gui.import"), List.of(), () -> act(player, pos, "import")));
		gui.setSlot(16, button(Items.ENDER_EYE, EsLang.get(player, "easysuppressions.gui.show_zone"), List.of(), () -> {
			gui.close();
			act(player, pos, "show");
		}));
		channelButtons(gui, player, be, draft, settings, true, 28);
		channelButtons(gui, player, be, draft, settings, false, 32);
		gui.setSlot(37, button(Items.LIME_CONCRETE, EsLang.get(player, "easysuppressions.gui.save_settings"), List.of(), () -> act(player, pos, "save_settings")));
		gui.setSlot(39, button(Items.LIGHT_BLUE_DYE, EsLang.get(player, "easysuppressions.gui.preset_simple"), List.of(), () -> act(player, pos, "preset_simple")));
		gui.setSlot(40, button(Items.ZOMBIE_HEAD, EsLang.get(player, "easysuppressions.gui.preset_mob_farm"), List.of(), () -> act(player, pos, "preset_mob_farm")));
		gui.setSlot(41, button(Items.TORCH, EsLang.get(player, "easysuppressions.gui.preset_no_spawn"), List.of(), () -> act(player, pos, "preset_no_spawn")));
		gui.setSlot(42, button(Items.DAYLIGHT_DETECTOR, EsLang.get(player, "easysuppressions.gui.preset_sky_only"), List.of(), () -> act(player, pos, "preset_sky_only")));
		gui.setSlot(43, button(Items.IRON_BARS, EsLang.get(player, "easysuppressions.gui.lock"), List.of(), () -> act(player, pos, "lock_ask")));
		gui.setSlot(49, button(Items.BARRIER, EsLang.get(player, "easysuppressions.vanilla.close"), List.of(), gui::close));
		gui.open();
	}

	/** Mode (click to switch) and, for a fixed value, minus / value / plus */
	private static void channelButtons(SimpleGui gui, ServerPlayerEntity player, LightSuppressorBlockEntity be, Draft draft, LightSettings settings,
									   boolean sky, int slot) {
		ChannelSetting channel = sky ? settings.sky() : settings.block();
		String label = EsLang.get(player, sky ? "easysuppressions.gui.sky_light" : "easysuppressions.gui.block_light");
		ChannelMode next = ChannelMode.values()[(channel.mode().ordinal() + 1) % ChannelMode.values().length];
		gui.setSlot(slot, button(sky ? Items.SUNFLOWER : Items.GLOWSTONE, label + ": " + describe(player, channel),
				List.of(EsLang.get(player, "easysuppressions.vanilla.click_mode", EsLang.get(player, next.translationKey()))),
				() -> setChannel(player, be, draft, settings, sky, new ChannelSetting(next, channel.value()))));
		if (channel.mode() != ChannelMode.FIXED) return;
		gui.setSlot(slot + 1, button(Items.RED_STAINED_GLASS_PANE, "- 1", List.of(), () -> setChannel(player, be, draft, settings, sky,
				new ChannelSetting(ChannelMode.FIXED, Math.max(0, channel.value() - 1)))));
		gui.setSlot(slot + 2, info(Items.LIGHT, Text.literal(label + " = " + channel.value()), List.of()).setCount(Math.max(1, channel.value())));
		gui.setSlot(slot + 3, button(Items.LIME_STAINED_GLASS_PANE, "+ 1", List.of(), () -> setChannel(player, be, draft, settings, sky,
				new ChannelSetting(ChannelMode.FIXED, Math.min(15, channel.value() + 1)))));
	}

	private static void setChannel(ServerPlayerEntity player, LightSuppressorBlockEntity be, Draft draft, LightSettings settings, boolean sky, ChannelSetting channel) {
		LightSettings edited = sky ? new LightSettings(channel, settings.block()) : new LightSettings(settings.sky(), channel);
		DRAFTS.put(player.getUuid(), new Draft(draft.pos, draft.corner1, draft.corner2, edited, null, false));
		open(player, be);
	}

	private static void cornerButtons(SimpleGui gui, ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft, int slot1, int slot2) {
		String[] corners = corners(player, be, draft);
		for (int i = 0; i < 2; i++) {
			int n = i + 1;
			String current = corners[i];
			gui.setSlot(i == 0 ? slot1 : slot2, button(i == 0 ? Items.RED_BANNER : Items.BLUE_BANNER, EsLang.get(player, "easysuppressions.vanilla.corner_input", n) + ": " + current,
					List.of(EsLang.get(player, "easysuppressions.vanilla.click_type")), () -> type(player, EsLang.get(player, "easysuppressions.vanilla.corner_input", n), current, text -> {
						Draft d = draft(player, be.getPos());
						String[] now = corners(player, be, d);
						DRAFTS.put(player.getUuid(), new Draft(d.pos, n == 1 ? text : now[0], n == 2 ? text : now[1], d.settings, null, false));
						open(player, be);
					}, () -> open(player, be))));
		}
	}

	private static void confirm(ServerPlayerEntity player, String title, String question, NbtCompound yes, BlockPos pos) {
		SimpleGui gui = chest(player, 3, title);
		gui.setSlot(4, info(Items.PAPER, Text.literal(question), List.of()));
		gui.setSlot(11, button(Items.LIME_CONCRETE, EsLang.get(player, "easysuppressions.vanilla.yes"), List.of(question), () -> handle(player, Optional.of(yes))));
		gui.setSlot(15, button(Items.RED_CONCRETE, EsLang.get(player, "easysuppressions.vanilla.cancel"), List.of(), () -> act(player, pos, "reopen")));
		gui.open();
	}

	/** A button's action, carried out like the mod's screen action of the same name */
	private static void act(ServerPlayerEntity player, BlockPos pos, String op) {
		handle(player, Optional.of(base(pos, op)));
	}

	// ------------------------------------------------------------------ actions

	public static void handle(ServerPlayerEntity player, Optional<NbtElement> payload) {
		if (!(payload.orElse(null) instanceof NbtCompound nbt)) {
			return;
		}
		BlockPos pos = new BlockPos(nbt.getInt("x", 0), nbt.getInt("y", 0), nbt.getInt("z", 0));
		SuppressionBlockEntity be = ServerNet.reachable(player, pos);
		if (be == null) {
			forget(player);
			return;
		}
		String op = nbt.getString("op", "");
		Draft draft = draft(player, pos);
		// keep what was typed: the screen opens again with it after the action
		if (nbt.contains("corner1")) {
			LightSettings typed = be instanceof LightSuppressorBlockEntity && nbt.contains("sky_mode") ? settings(nbt) : draft.settings;
			draft = new Draft(draft.pos, nbt.getString("corner1", ""), nbt.getString("corner2", ""), typed, null, false);
			DRAFTS.put(player.getUuid(), draft);
		}
		LightSettings edited = be instanceof LightSuppressorBlockEntity light ? (draft.settings != null ? draft.settings : light.getSettings()) : LightSettings.DEFAULT;
		switch (op) {
			case "reopen" -> open(player, be);
			case "save_zone" -> {
				Zone zone = typedZone(player, be, draft);
				if (zone != null) {
					clearCorners(player, draft);
					ServerNet.handleAction(player, new Payloads.ScreenAction(pos, Payloads.Action.SAVE_ZONE, Optional.of(zone), edited));
				}
			}
			case "import" -> {
				clearCorners(player, draft);
				ServerNet.handleAction(player, new Payloads.ScreenAction(pos, Payloads.Action.IMPORT_SELECTOR, Optional.empty(), edited));
			}
			case "save_settings" -> {
				DRAFTS.put(player.getUuid(), new Draft(draft.pos, draft.corner1, draft.corner2, null, null, false));
				ServerNet.handleAction(player, new Payloads.ScreenAction(pos, Payloads.Action.SAVE_SETTINGS, Optional.empty(), edited));
			}
			case "preset_simple", "preset_mob_farm", "preset_no_spawn", "preset_sky_only" -> {
				LightSettings preset = switch (op) {
					case "preset_mob_farm" -> LightSettings.MOB_FARM;
					case "preset_no_spawn" -> LightSettings.NO_SPAWN;
					case "preset_sky_only" -> LightSettings.SKY_ONLY;
					default -> LightSettings.SIMPLE;
				};
				DRAFTS.put(player.getUuid(), new Draft(draft.pos, draft.corner1, draft.corner2, preset, null, false));
				open(player, be);
			}
			case "lock_ask" -> {
				Zone zone = typedZone(player, be, draft);
				if (zone == null) {
					return;
				}
				if (edited.doesNothing()) {
					note(player, pos, Text.translatable("easysuppressions.message.nothing_to_lock"), true);
					open(player, be);
					return;
				}
				NbtCompound yes = base(pos, "lock");
				yes.putIntArray("zone", new int[]{zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ()});
				putSettings(yes, edited);
				confirm(player, EsLang.get(player, "easysuppressions.gui.confirm_lock_title"),
						EsLang.get(player, "easysuppressions.gui.confirm_lock", zone.describe(), describe(player, edited.sky()), describe(player, edited.block())),
						yes, pos);
			}
			case "lock" -> {
				int[] z = nbt.getIntArray("zone").orElse(new int[0]);
				if (z.length != 6) {
					return;
				}
				DRAFTS.remove(player.getUuid());
				ServerNet.handleAction(player, new Payloads.ScreenAction(pos, Payloads.Action.LOCK,
						Optional.of(new Zone(z[0], z[1], z[2], z[3], z[4], z[5])), settings(nbt)));
			}
			case "unlock_ask" -> confirm(player, EsLang.get(player, "easysuppressions.gui.confirm_unlock_title"),
					EsLang.get(player, "easysuppressions.gui.confirm_unlock"), base(pos, "unlock"), pos);
			case "unlock" -> {
				DRAFTS.remove(player.getUuid());
				ServerNet.handleAction(player, new Payloads.ScreenAction(pos, Payloads.Action.UNLOCK, Optional.empty(), edited));
			}
			case "show" -> {
				Zone zone = draft.corner1 != null ? parseZone(player, draft) : be.getZone();
				if (zone == null) {
					zone = be.getZone();
				}
				if (zone == null) {
					note(player, pos, Text.translatable("easysuppressions.message.no_zone"), true);
					open(player, be);
					return;
				}
				// the screen closed so the zone can be seen
				VanillaZones.show(player, zone, be.kind(), 20 * 15);
				player.sendMessage(Text.literal(EsLang.get(player, "easysuppressions.vanilla.zone_shown")).formatted(Formatting.AQUA), true);
			}
			default -> {
			}
		}
	}

	private static void clearCorners(ServerPlayerEntity player, Draft draft) {
		DRAFTS.put(player.getUuid(), new Draft(draft.pos, null, null, draft.settings, null, false));
	}

	@Nullable
	private static Zone typedZone(ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft) {
		Zone zone = parseZone(player, draft);
		if (zone == null) {
			note(player, be.getPos(), Text.literal(EsLang.get(player, "easysuppressions.vanilla.bad_corner")), true);
			open(player, be);
		}
		return zone;
	}

	@Nullable
	private static Zone parseZone(ServerPlayerEntity player, Draft draft) {
		BlockPos a = parseCorner(player, draft.corner1);
		BlockPos b = parseCorner(player, draft.corner2);
		return a == null || b == null ? null : Zone.of(a, b);
	}

	/** "x y z", whole numbers; ~ and ~n are relative to the player like in commands. */
	@Nullable
	static BlockPos parseCorner(ServerPlayerEntity player, @Nullable String text) {
		if (text == null) {
			return null;
		}
		String[] parts = text.trim().replace(',', ' ').split("\\s+");
		if (parts.length != 3) {
			return null;
		}
		BlockPos at = player.getBlockPos();
		int[] base = {at.getX(), at.getY(), at.getZ()};
		int[] out = new int[3];
		try {
			for (int i = 0; i < 3; i++) {
				String p = parts[i];
				if (p.startsWith("~")) {
					out[i] = base[i] + (p.length() == 1 ? 0 : Integer.parseInt(p.substring(1)));
				} else {
					out[i] = Integer.parseInt(p);
				}
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return new BlockPos(out[0], out[1], out[2]);
	}

	private static LightSettings settings(NbtCompound nbt) {
		return new LightSettings(channel(nbt, "sky"), channel(nbt, "block"));
	}

	private static ChannelSetting channel(NbtCompound nbt, String prefix) {
		String mode = nbt.getString(prefix + "_mode", "none");
		ChannelMode m = switch (mode) {
			case "lock" -> ChannelMode.LOCK;
			case "fixed" -> ChannelMode.FIXED;
			default -> ChannelMode.NONE;
		};
		NbtElement value = nbt.get(prefix + "_value");
		int v = value == null ? 0 : value.asNumber().map(Number::intValue)
				.orElseGet(() -> value.asString().map(s -> {
					try {
						return (int) Float.parseFloat(s);
					} catch (NumberFormatException e) {
						return 0;
					}
				}).orElse(0));
		return new ChannelSetting(m, v);
	}

	private static void putSettings(NbtCompound nbt, LightSettings s) {
		nbt.putString("sky_mode", s.sky().mode().asString());
		nbt.putInt("sky_value", s.sky().value());
		nbt.putString("block_mode", s.block().mode().asString());
		nbt.putInt("block_value", s.block().value());
	}

	// ------------------------------------------------------------------ pieces

	/** The two corners shown: what was typed, else the zone, else the held selector's selection, else the block */
	private static String[] corners(ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft) {
		String c1 = draft.corner1;
		String c2 = draft.corner2;
		if (c1 == null || c2 == null) {
			Zone zone = be.getZone();
			Selection selection = heldSelection(player, be.kind());
			if (zone != null) {
				c1 = xyz(zone.min());
				c2 = xyz(zone.max());
			} else if (selection != null && selection.isComplete() && selection.dimension().equals(player.getEntityWorld().getRegistryKey())) {
				Zone z = selection.zone().orElseThrow();
				c1 = xyz(z.min());
				c2 = xyz(z.max());
			} else {
				c1 = xyz(be.getPos());
				c2 = xyz(be.getPos());
			}
		}
		return new String[]{c1, c2};
	}

	@Nullable
	private static Selection heldSelection(ServerPlayerEntity player, SuppressorKind kind) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof SelectorItem selector && selector.kind() == kind) {
				return stack.get(ModContent.SELECTION);
			}
		}
		return null;
	}

	private static String describe(ServerPlayerEntity player, ChannelSetting setting) {
		String text = EsLang.get(player, setting.mode().translationKey());
		return setting.mode() == ChannelMode.FIXED ? text + " " + setting.value() : text;
	}

	private static String zoneLine(ServerPlayerEntity player, @Nullable Zone zone) {
		return zone == null ? EsLang.get(player, "easysuppressions.vanilla.zone_none")
				: EsLang.get(player, "easysuppressions.tooltip.range", zone.describe()) + "  "
				+ EsLang.get(player, "easysuppressions.tooltip.size", zone.sizeX(), zone.sizeY(), zone.sizeZ());
	}

	private static void message(List<Text> lore, Draft draft) {
		if (draft.message != null) {
			lore.add(draft.message.copy().formatted(draft.error ? Formatting.RED : Formatting.GREEN));
		}
	}

	private static String xyz(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static Text white(String text) {
		return Text.literal(text).formatted(Formatting.WHITE);
	}

	private static Text gray(String text) {
		return Text.literal(text).formatted(Formatting.GRAY);
	}

	private static NbtCompound base(BlockPos pos, String op) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("op", op);
		nbt.putInt("x", pos.getX());
		nbt.putInt("y", pos.getY());
		nbt.putInt("z", pos.getZ());
		return nbt;
	}

	private static SimpleGui chest(ServerPlayerEntity player, int rows, String title) {
		SimpleGui gui = new SimpleGui(rows == 6 ? ScreenHandlerType.GENERIC_9X6 : ScreenHandlerType.GENERIC_9X3, player, false);
		gui.setTitle(Text.literal(title));
		return gui;
	}

	/** An item with a name and lore lines (each line split at line breaks, the line's colour kept) */
	private static GuiElementBuilder info(Item item, Text name, List<Text> lore) {
		GuiElementBuilder b = new GuiElementBuilder(item).setName(name.copy().styled(s -> s.withItalic(false).withColor(Formatting.WHITE))).hideDefaultTooltip();
		for (Text line : lore) {
			for (String part : line.getString().split("\n")) b.addLoreLine(Text.literal(part).setStyle(line.getStyle().withItalic(false)));
		}
		return b;
	}

	private static GuiElementBuilder button(Item item, String name, List<String> lore, Runnable click) {
		return info(item, Text.literal(name), lore.stream().map(VanillaScreens::gray).toList()).setCallback((index, type, action) -> {
			if (type == ClickType.MOUSE_LEFT || type == ClickType.MOUSE_RIGHT || type == ClickType.MOUSE_LEFT_SHIFT || type == ClickType.MOUSE_RIGHT_SHIFT) click.run();
		});
	}

	/** Typing a corner: an anvil with the current text; the result slot accepts */
	private static void type(ServerPlayerEntity player, String title, String current, java.util.function.Consumer<String> done, Runnable cancel) {
		AnvilInputGui gui = new AnvilInputGui(player, false);
		gui.setTitle(Text.literal(title));
		gui.setDefaultInputValue(current);
		gui.setSlot(1, button(Items.RED_CONCRETE, EsLang.get(player, "easysuppressions.vanilla.cancel"), List.of(), cancel));
		gui.setSlot(2, button(Items.LIME_CONCRETE, EsLang.get(player, "easysuppressions.vanilla.yes"), List.of(), () -> done.accept(gui.getInput())));
		gui.open();
	}
}
