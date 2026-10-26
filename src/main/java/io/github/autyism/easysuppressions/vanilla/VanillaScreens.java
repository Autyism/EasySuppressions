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
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.action.DialogAction;
import net.minecraft.dialog.action.DynamicCustomDialogAction;
import net.minecraft.dialog.action.SimpleDialogAction;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.input.NumberRangeInputControl;
import net.minecraft.dialog.input.SingleOptionInputControl;
import net.minecraft.dialog.input.TextInputControl;
import net.minecraft.dialog.type.ConfirmationDialog;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The block GUIs for players without the mod, as dialogs (any 1.21.6+ client shows them). Same
 * actions as the mod's screens, carried out by the same server code (ServerNet.handleAction), which
 * checks reach, redstone and the lock again for every one of them.
 */
public final class VanillaScreens {
	public static final Identifier ACTION = EasySuppressions.id("gui");
	private static final int WIDTH = 310;

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

	/** A message from the last action, shown at the top of the dialog when it opens again. */
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
		Dialog dialog = be instanceof LightSuppressorBlockEntity light ? suppressor(player, light, draft) : cutter(player, be, draft);
		player.openDialog(RegistryEntry.of(dialog));
	}

	// ------------------------------------------------------------------ dialogs

	private static Dialog cutter(ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft) {
		List<DialogBody> body = new ArrayList<>();
		message(player, body, draft);
		body.add(line(EsLang.get(player, "easysuppressions.gui.status",
				EsLang.get(player, be.isActive() ? "easysuppressions.gui.cutter_active" : "easysuppressions.gui.cutter_inactive"))));
		body.add(line(zoneLine(player, be.getZone())));
		body.add(gray(EsLang.get(player, "easysuppressions.gui.cutter_hint")));
		body.add(gray(EsLang.get(player, "easysuppressions.vanilla.selector_hint")));

		List<DialogInput> inputs = cornerInputs(player, be, draft);
		List<DialogActionButtonData> buttons = new ArrayList<>();
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.save_zone"), dynamic(be.getPos(), "save_zone")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.import"), dynamic(be.getPos(), "import")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.show_zone"), dynamic(be.getPos(), "show")));
		return multi(EsLang.get(player, "block.easysuppressions.portal_cutter_block"), body, inputs, buttons, player);
	}

	private static Dialog suppressor(ServerPlayerEntity player, LightSuppressorBlockEntity be, Draft draft) {
		boolean locked = be.isLocked();
		List<DialogBody> body = new ArrayList<>();
		message(player, body, draft);
		body.add(line(EsLang.get(player, "easysuppressions.gui.status",
				EsLang.get(player, locked ? "easysuppressions.gui.locked" : "easysuppressions.gui.unlocked"))));
		body.add(line(zoneLine(player, be.getZone())));
		Payloads.ScreenData data = ServerNet.screenData(be);
		long pending = data.total() - data.recorded();
		if (locked && pending > 0) {
			body.add(line(EsLang.get(player, "easysuppressions.gui.pending", pending, data.total())));
			LongList nearest = data.nearestPending();
			if (!nearest.isEmpty()) {
				StringBuilder list = new StringBuilder(EsLang.get(player, "easysuppressions.gui.pending_nearest"));
				for (int i = 0; i < nearest.size(); i++) {
					long key = nearest.getLong(i);
					list.append(i == 0 ? " " : ", ").append(ChunkPos.getPackedX(key)).append(" ").append(ChunkPos.getPackedZ(key));
				}
				body.add(gray(list.toString()));
			}
		}
		if (locked) {
			LightSettings s = be.getSettings();
			body.add(line(EsLang.get(player, "easysuppressions.gui.sky_light") + ": " + describe(player, s.sky())
					+ "   " + EsLang.get(player, "easysuppressions.gui.block_light") + ": " + describe(player, s.block())));
			body.add(gray(EsLang.get(player, "easysuppressions.gui.locked_hint")));
			List<DialogActionButtonData> buttons = new ArrayList<>();
			buttons.add(button(EsLang.get(player, "easysuppressions.gui.unlock"), simple(be.getPos(), "unlock_ask")));
			buttons.add(button(EsLang.get(player, "easysuppressions.gui.show_zone"), simple(be.getPos(), "show")));
			return multi(EsLang.get(player, "block.easysuppressions.light_suppressor_block"), body, List.of(), buttons, player);
		}
		body.add(gray(EsLang.get(player, "easysuppressions.gui.suppressor_hint")));
		body.add(gray(EsLang.get(player, "easysuppressions.vanilla.selector_hint")));

		LightSettings settings = draft.settings != null ? draft.settings : be.getSettings();
		List<DialogInput> inputs = new ArrayList<>(cornerInputs(player, be, draft));
		inputs.add(new DialogInput("sky_mode", modeInput(player, "easysuppressions.gui.sky_light", settings.sky().mode())));
		inputs.add(new DialogInput("sky_value", valueInput(player, "easysuppressions.gui.sky_light", settings.sky().value())));
		inputs.add(new DialogInput("block_mode", modeInput(player, "easysuppressions.gui.block_light", settings.block().mode())));
		inputs.add(new DialogInput("block_value", valueInput(player, "easysuppressions.gui.block_light", settings.block().value())));

		List<DialogActionButtonData> buttons = new ArrayList<>();
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.save_zone"), dynamic(be.getPos(), "save_zone")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.save_settings"), dynamic(be.getPos(), "save_settings")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.import"), dynamic(be.getPos(), "import")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.lock"), dynamic(be.getPos(), "lock_ask")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.preset_simple"), dynamic(be.getPos(), "preset_simple")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.preset_mob_farm"), dynamic(be.getPos(), "preset_mob_farm")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.preset_no_spawn"), dynamic(be.getPos(), "preset_no_spawn")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.preset_sky_only"), dynamic(be.getPos(), "preset_sky_only")));
		buttons.add(button(EsLang.get(player, "easysuppressions.gui.show_zone"), dynamic(be.getPos(), "show")));
		return multi(EsLang.get(player, "block.easysuppressions.light_suppressor_block"), body, inputs, buttons, player);
	}

	private static Dialog confirm(ServerPlayerEntity player, String title, String question, NbtCompound yes, BlockPos pos) {
		DialogCommonData common = new DialogCommonData(Text.literal(title), Optional.empty(), true, false, AfterAction.CLOSE,
				List.of(line(question)), List.of());
		return new ConfirmationDialog(common,
				new DialogActionButtonData(new DialogButtonData(Text.literal(EsLang.get(player, "easysuppressions.vanilla.yes")).formatted(Formatting.RED), 150),
						Optional.of(custom(yes))),
				new DialogActionButtonData(new DialogButtonData(Text.literal(EsLang.get(player, "easysuppressions.vanilla.cancel")), 150),
						Optional.of(simple(pos, "reopen"))));
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
		// keep what was typed: the dialog opens again with it after the action
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
				player.openDialog(RegistryEntry.of(confirm(player, EsLang.get(player, "easysuppressions.gui.confirm_lock_title"),
						EsLang.get(player, "easysuppressions.gui.confirm_lock", zone.describe(), describe(player, edited.sky()), describe(player, edited.block())),
						yes, pos)));
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
			case "unlock_ask" -> player.openDialog(RegistryEntry.of(confirm(player, EsLang.get(player, "easysuppressions.gui.confirm_unlock_title"),
					EsLang.get(player, "easysuppressions.gui.confirm_unlock"), base(pos, "unlock"), pos)));
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
				// the dialog closes so the zone can be seen
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

	private static List<DialogInput> cornerInputs(ServerPlayerEntity player, SuppressionBlockEntity be, Draft draft) {
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
		return List.of(
				new DialogInput("corner1", new TextInputControl(200, Text.literal(EsLang.get(player, "easysuppressions.vanilla.corner_input", 1)), true, c1, 64, Optional.empty())),
				new DialogInput("corner2", new TextInputControl(200, Text.literal(EsLang.get(player, "easysuppressions.vanilla.corner_input", 2)), true, c2, 64, Optional.empty())));
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

	private static SingleOptionInputControl modeInput(ServerPlayerEntity player, String labelKey, ChannelMode current) {
		List<SingleOptionInputControl.Entry> entries = new ArrayList<>();
		for (ChannelMode mode : ChannelMode.values()) {
			entries.add(new SingleOptionInputControl.Entry(mode.asString(), Optional.of(Text.literal(EsLang.get(player, mode.translationKey()))), mode == current));
		}
		return new SingleOptionInputControl(200, entries, Text.literal(EsLang.get(player, labelKey)), true);
	}

	private static NumberRangeInputControl valueInput(ServerPlayerEntity player, String labelKey, int current) {
		return new NumberRangeInputControl(200, Text.literal(EsLang.get(player, labelKey) + " · " + EsLang.get(player, "easysuppressions.vanilla.fixed_value")),
				"options.generic_value", new NumberRangeInputControl.RangeInfo(0, 15, Optional.of((float) current), Optional.of(1.0F)));
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

	private static void message(ServerPlayerEntity player, List<DialogBody> body, Draft draft) {
		if (draft.message != null) {
			body.add(new PlainMessageDialogBody(draft.message.copy().formatted(draft.error ? Formatting.RED : Formatting.GREEN), WIDTH));
		}
	}

	private static String xyz(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static DialogBody line(String text) {
		return new PlainMessageDialogBody(Text.literal(text), WIDTH);
	}

	private static DialogBody gray(String text) {
		return new PlainMessageDialogBody(Text.literal(text).formatted(Formatting.GRAY), WIDTH);
	}

	private static DialogActionButtonData button(String label, DialogAction action) {
		return new DialogActionButtonData(new DialogButtonData(Text.literal(label), 150), Optional.of(action));
	}

	private static NbtCompound base(BlockPos pos, String op) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("op", op);
		nbt.putInt("x", pos.getX());
		nbt.putInt("y", pos.getY());
		nbt.putInt("z", pos.getZ());
		return nbt;
	}

	/** Sends the typed values along. */
	private static DialogAction dynamic(BlockPos pos, String op) {
		return new DynamicCustomDialogAction(ACTION, Optional.of(base(pos, op)));
	}

	private static DialogAction simple(BlockPos pos, String op) {
		return custom(base(pos, op));
	}

	private static SimpleDialogAction custom(NbtCompound payload) {
		return new SimpleDialogAction(new ClickEvent.Custom(ACTION, Optional.of(payload)));
	}

	private static Dialog multi(String title, List<DialogBody> body, List<DialogInput> inputs, List<DialogActionButtonData> buttons, ServerPlayerEntity player) {
		DialogCommonData common = new DialogCommonData(Text.literal(title), Optional.empty(), true, false, AfterAction.CLOSE, body, inputs);
		Optional<DialogActionButtonData> exit = Optional.of(new DialogActionButtonData(
				new DialogButtonData(Text.literal(EsLang.get(player, "easysuppressions.vanilla.close")), 150), Optional.empty()));
		return new MultiActionDialog(common, buttons, exit, 2);
	}
}
