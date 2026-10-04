package io.github.autyism.easysuppressions.client.screen;

import io.github.autyism.easysuppressions.client.ClientConfig;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/** Shared parts of both block GUIs: the zone corners, import, the "show zone" switch, messages. */
public abstract class BlockScreen extends Screen {
	protected static final int TEXT = 0xFFFFFFFF;
	protected static final int GRAY = 0xFFA0A0A0;
	protected static final int ROW = 22;

	protected Payloads.ScreenData data;
	/** Corner fields: x1 y1 z1 x2 y2 z2, kept as text so a half-typed value survives a refresh. */
	protected final String[] corner = new String[6];
	protected boolean cornersEdited;
	protected final TextFieldWidget[] cornerFields = new TextFieldWidget[6];
	private Text message;
	private int messageColor;
	private int messageTicks;
	private int refreshTicks;

	protected BlockScreen(Text title, Payloads.ScreenData data) {
		super(title);
		this.data = data;
		loadCorners();
	}

	public BlockPos pos() {
		return data.pos();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void loadCorners() {
		Optional<Zone> zone = data.zone();
		int[] v = zone.map(z -> new int[]{z.minX(), z.minY(), z.minZ(), z.maxX(), z.maxY(), z.maxZ()}).orElse(null);
		for (int i = 0; i < 6; i++) {
			corner[i] = v == null ? "" : Integer.toString(v[i]);
		}
		cornersEdited = false;
	}

	/** New data from the server (after an action or the periodic refresh). */
	public void update(Payloads.ScreenData newData) {
		boolean structural = newData.active() != data.active() || newData.powered() != data.powered();
		boolean zoneChanged = !newData.zone().equals(data.zone());
		Payloads.ScreenData old = data;
		data = newData;
		if (zoneChanged || !cornersEdited) {
			loadCorners();
			for (int i = 0; i < 6; i++) {
				if (cornerFields[i] != null && !cornerFields[i].getText().equals(corner[i])) {
					cornerFields[i].setText(corner[i]);
				}
			}
			cornersEdited = false;
		}
		structural |= onDataChanged(old);
		if (structural && client != null) {
			clearAndInit();
		}
	}

	/** Returns true when the widgets have to be rebuilt. */
	protected boolean onDataChanged(Payloads.ScreenData old) {
		return false;
	}

	public void showMessage(Text text, boolean error) {
		message = text;
		messageColor = error ? 0xFFFF6060 : 0xFF70FF70;
		messageTicks = 160;
	}

	protected abstract boolean zoneEditable();

	/** Room for the "Corner N" labels in front of the fields. */
	protected int labelWidth() {
		return Math.max(textRenderer.getWidth(Text.translatable("easysuppressions.gui.corner", 2)) + 6, 30);
	}

	/** Width of the corner rows (label and three fields). */
	protected int cornerRowsWidth() {
		return labelWidth() + 3 * 51 - 3;
	}

	/** Adds the two corner rows at (x, y); returns the y below them. */
	protected int addCornerRows(int x, int y) {
		for (int row = 0; row < 2; row++) {
			for (int axis = 0; axis < 3; axis++) {
				int i = row * 3 + axis;
				TextFieldWidget field = new TextFieldWidget(textRenderer, x + labelWidth() + axis * 51, y + row * ROW, 48, 18, Text.literal("xyz".substring(axis, axis + 1)));
				field.setMaxLength(9);
				field.setTextPredicate(s -> s.matches("-?\\d{0,8}"));
				field.setText(corner[i]);
				field.setEditable(zoneEditable());
				field.setChangedListener(s -> {
					if (!s.equals(corner[i])) {
						corner[i] = s;
						cornersEdited = true;
					}
				});
				cornerFields[i] = addDrawableChild(field);
			}
		}
		return y + 2 * ROW;
	}

	protected void drawCornerLabels(DrawContext context, int x, int y) {
		for (int row = 0; row < 2; row++) {
			context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.corner", row + 1), x, y + row * ROW + 5, TEXT);
		}
		for (int axis = 0; axis < 3; axis++) {
			context.drawCenteredTextWithShadow(textRenderer, Text.literal("xyz".substring(axis, axis + 1)), x + labelWidth() + axis * 51 + 24, y - 10, GRAY);
		}
	}

	/** The zone typed into the fields, or empty with an error shown. */
	protected Optional<Zone> typedZone() {
		int[] v = new int[6];
		for (int i = 0; i < 6; i++) {
			try {
				v[i] = Integer.parseInt(corner[i]);
			} catch (NumberFormatException e) {
				showMessage(Text.translatable("easysuppressions.gui.bad_number"), true);
				return Optional.empty();
			}
		}
		Zone zone = Zone.of(new BlockPos(v[0], v[1], v[2]), new BlockPos(v[3], v[4], v[5]));
		if (!zone.contains(pos())) {
			showMessage(Text.translatable("easysuppressions.message.zone_must_contain_block"), true);
			return Optional.empty();
		}
		return Optional.of(zone);
	}

	protected void send(Payloads.Action action, Optional<Zone> zone, LightSettings settings) {
		if (ClientPlayNetworking.canSend(Payloads.ScreenAction.ID)) {
			ClientPlayNetworking.send(new Payloads.ScreenAction(pos(), action, zone, settings));
		}
	}

	protected ButtonWidget saveZoneButton(int x, int y, int w, LightSettings settings) {
		ButtonWidget b = ButtonWidget.builder(Text.translatable("easysuppressions.gui.save_zone"), button ->
				typedZone().ifPresent(zone -> {
					cornersEdited = false;
					send(Payloads.Action.SAVE_ZONE, Optional.of(zone), settings);
				})).dimensions(x, y, w, 20).build();
		b.active = zoneEditable();
		return b;
	}

	protected ButtonWidget importButton(int x, int y, int w, LightSettings settings) {
		ButtonWidget b = ButtonWidget.builder(Text.translatable("easysuppressions.gui.import"), button -> {
			cornersEdited = false;
			send(Payloads.Action.IMPORT_SELECTOR, Optional.empty(), settings);
		}).dimensions(x, y, w, 20).build();
		b.active = zoneEditable();
		return b;
	}

	protected CyclingButtonWidget<Boolean> showZoneButton(int x, int y, int w) {
		return CyclingButtonWidget.onOffBuilder(ClientConfig.isZoneShown(pos()))
				.build(x, y, w, 20, Text.translatable("easysuppressions.gui.show_zone"), (button, value) -> ClientConfig.setZoneShown(pos(), value));
	}

	protected ButtonWidget doneButton(int x, int y, int w) {
		return ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(x, y, w, 20).build();
	}

	@Override
	public void tick() {
		super.tick();
		if (messageTicks > 0) {
			messageTicks--;
		}
		if (++refreshTicks >= 20) {
			refreshTicks = 0;
			if (ClientPlayNetworking.canSend(Payloads.RequestScreen.ID)) {
				ClientPlayNetworking.send(new Payloads.RequestScreen(pos()));
			}
		}
		if (client != null && client.player != null
				&& client.player.squaredDistanceTo(pos().toCenterPos()) > 12 * 12) {
			close();
		}
	}

	protected void drawMessage(DrawContext context, int centerX, int y) {
		if (message != null && messageTicks > 0) {
			context.drawCenteredTextWithShadow(textRenderer, message, centerX, y, messageColor);
		}
	}

	protected static Text statusText(boolean on, String onKey, String offKey) {
		return Text.translatable(on ? onKey : offKey).formatted(on ? Formatting.GREEN : Formatting.GRAY);
	}
}
