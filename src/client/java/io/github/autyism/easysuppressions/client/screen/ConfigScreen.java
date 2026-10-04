package io.github.autyism.easysuppressions.client.screen;

import io.github.autyism.easysuppressions.client.ClientConfig;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Settings screen (Mod Menu). The two times are server settings: in the main menu they edit this
 * game's own config (used by singleplayer worlds); in a singleplayer world or as an operator on a
 * server they are sent to the server, which applies them for everybody; other players see the
 * server's values read-only. The light display mode is a personal client setting.
 */
public class ConfigScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget breakField;
	private TextFieldWidget pickaxeField;
	private String breakText;
	private String pickaxeText;
	private Text message;
	private int messageColor;

	public ConfigScreen(@Nullable Screen parent) {
		super(Text.translatable("easysuppressions.config.title"));
		this.parent = parent;
		EsConfig.Values v = current();
		breakText = format(v.blockBreakSeconds());
		pickaxeText = format(v.pickaxeSeconds());
	}

	private static String format(double seconds) {
		return seconds == Math.rint(seconds) ? Long.toString((long) seconds) : Double.toString(seconds);
	}

	private boolean connectedToServer() {
		return client != null && client.world != null;
	}

	private EsConfig.Values current() {
		return EsConfig.forSide(true);
	}

	private boolean editable() {
		if (!connectedToServer()) {
			return true;
		}
		if (client.getServer() != null) {
			return true; // singleplayer / LAN host
		}
		return client.player != null && client.player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 4 + 10;
		breakField = numberField(cx + 10, y, breakText);
		breakField.setChangedListener(s -> breakText = s);
		pickaxeField = numberField(cx + 10, y + 26, pickaxeText);
		pickaxeField.setChangedListener(s -> pickaxeText = s);
		addDrawableChild(CyclingButtonWidget.builder((Boolean v) -> Text.translatable(v ? "easysuppressions.gui.display_suppressed"
						: "easysuppressions.gui.display_real"), ClientConfig.displaySuppressedLight())
				.values(Boolean.TRUE, Boolean.FALSE)
				.build(cx - 150, y + 60, 300, 20, Text.translatable("easysuppressions.gui.display"), (b, v) -> ClientConfig.setDisplaySuppressedLight(v)));
		ButtonWidget save = ButtonWidget.builder(Text.translatable("easysuppressions.config.save"), b -> save())
				.dimensions(cx - 152, height - 52, 148, 20).build();
		save.active = editable();
		addDrawableChild(save);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(cx + 4, height - 52, 148, 20).build());
	}

	private TextFieldWidget numberField(int x, int y, String text) {
		TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 80, 18, Text.empty());
		field.setMaxLength(8);
		field.setTextPredicate(s -> s.matches("\\d{0,4}(\\.\\d{0,2})?"));
		field.setText(text);
		field.setEditable(editable());
		return addDrawableChild(field);
	}

	private void save() {
		double breakSeconds;
		double pickaxeSeconds;
		try {
			breakSeconds = Double.parseDouble(breakText);
			pickaxeSeconds = Double.parseDouble(pickaxeText);
		} catch (NumberFormatException e) {
			show(Text.translatable("easysuppressions.gui.bad_number"), true);
			return;
		}
		EsConfig.Values values = new EsConfig.Values(breakSeconds, pickaxeSeconds);
		if (!connectedToServer()) {
			EsConfig.set(values);
			show(Text.translatable("easysuppressions.config.saved_local"), false);
		} else if (ClientPlayNetworking.canSend(Payloads.UpdateConfig.ID)) {
			ClientPlayNetworking.send(new Payloads.UpdateConfig(values));
			show(Text.translatable("easysuppressions.config.sent"), false);
		} else {
			show(Text.translatable("easysuppressions.message.server_missing_mod"), true);
		}
	}

	private void show(Text text, boolean error) {
		message = text;
		messageColor = error ? 0xFFFF6060 : 0xFF70FF70;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int cx = width / 2;
		int y = height / 4 + 10;
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 20, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.config.block_break"), cx - 150, y + 5, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.config.pickaxe"), cx - 150, y + 31, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.config.seconds"), cx + 96, y + 5, 0xFFA0A0A0);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.config.seconds"), cx + 96, y + 31, 0xFFA0A0A0);
		EsConfig.Values v = current();
		Text where = !connectedToServer() ? Text.translatable("easysuppressions.config.where_local")
				: client.getServer() != null ? Text.translatable("easysuppressions.config.where_singleplayer")
				: editable() ? Text.translatable("easysuppressions.config.where_server_op")
				: Text.translatable("easysuppressions.config.where_server_readonly");
		context.drawCenteredTextWithShadow(textRenderer, where.copy().formatted(Formatting.GRAY), cx, y + 92, 0xFFA0A0A0);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("easysuppressions.config.current", format(v.blockBreakSeconds()),
				format(v.pickaxeSeconds())), cx, y + 106, 0xFFA0A0A0);
		if (message != null) {
			context.drawCenteredTextWithShadow(textRenderer, message, cx, height - 70, messageColor);
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
