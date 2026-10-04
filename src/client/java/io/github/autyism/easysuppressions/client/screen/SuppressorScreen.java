package io.github.autyism.easysuppressions.client.screen;

import io.github.autyism.easysuppressions.client.ClientConfig;
import io.github.autyism.easysuppressions.light.ChannelMode;
import io.github.autyism.easysuppressions.light.ChannelSetting;
import io.github.autyism.easysuppressions.light.LightChannel;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Optional;

/** Light suppressor GUI. Only opens with redstone power. Read-only while locked. */
public class SuppressorScreen extends BlockScreen {
	private static final int W = 424;
	private static final int H = 240;

	private LightSettings edited;
	private boolean settingsEdited;

	public SuppressorScreen(Payloads.ScreenData data) {
		super(Text.translatable("block.easysuppressions.light_suppressor_block"), data);
		edited = data.settings();
	}

	private boolean locked() {
		return data.active();
	}

	@Override
	protected boolean zoneEditable() {
		return !locked();
	}

	@Override
	protected boolean onDataChanged(Payloads.ScreenData old) {
		boolean rebuild = false;
		if (!settingsEdited || old.active() != data.active() || !old.settings().equals(data.settings())) {
			rebuild = !edited.equals(data.settings());
			edited = data.settings();
			settingsEdited = false;
		}
		return rebuild;
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return Math.max(2, (height - H) / 2);
	}

	@Override
	protected void init() {
		int lx = left() + 8;
		int rx = left() + 220;
		int top = top();
		int y = addCornerRows(lx, top + 40);
		addDrawableChild(saveZoneButton(lx, y + 2, 200, edited));
		addDrawableChild(importButton(lx, y + 24, 200, edited));
		addDrawableChild(showZoneButton(lx, y + 46, 200));
		addDrawableChild(CyclingButtonWidget.builder((Boolean v) -> Text.translatable(v ? "easysuppressions.gui.display_suppressed"
						: "easysuppressions.gui.display_real"), ClientConfig.displaySuppressedLight())
				.values(Boolean.TRUE, Boolean.FALSE)
				.build(lx, y + 68, 200, 20, Text.translatable("easysuppressions.gui.display"), (b, v) -> ClientConfig.setDisplaySuppressedLight(v)));

		channelRow(rx, top + 40, LightChannel.SKY);
		channelRow(rx, top + 74, LightChannel.BLOCK);
		presetButton(rx, top + 100, "easysuppressions.gui.preset_simple", LightSettings.SIMPLE);
		presetButton(rx + 100, top + 100, "easysuppressions.gui.preset_mob_farm", LightSettings.MOB_FARM);
		presetButton(rx, top + 122, "easysuppressions.gui.preset_no_spawn", LightSettings.NO_SPAWN);
		presetButton(rx + 100, top + 122, "easysuppressions.gui.preset_sky_only", LightSettings.SKY_ONLY);

		ButtonWidget save = ButtonWidget.builder(Text.translatable("easysuppressions.gui.save_settings"), b -> {
			settingsEdited = false;
			send(Payloads.Action.SAVE_SETTINGS, Optional.empty(), edited);
		}).dimensions(rx, top + 148, 96, 20).build();
		save.active = !locked();
		addDrawableChild(save);

		ButtonWidget lock = ButtonWidget.builder(Text.translatable(locked() ? "easysuppressions.gui.unlock" : "easysuppressions.gui.lock")
				.formatted(locked() ? Formatting.YELLOW : Formatting.GOLD), b -> confirmLock()).dimensions(rx + 100, top + 148, 96, 20).build();
		addDrawableChild(lock);

		addDrawableChild(doneButton(left() + W / 2 - 50, top + H - 22, 100));
	}

	private void channelRow(int x, int y, LightChannel channel) {
		ChannelSetting setting = edited.get(channel);
		CyclingButtonWidget<ChannelMode> mode = CyclingButtonWidget.builder((ChannelMode m) -> Text.translatable(m.translationKey()), setting.mode())
				.values(ChannelMode.values())
				.omitKeyText()
				.build(x, y, 112, 20, Text.empty(), (b, m) -> {
					setChannel(channel, new ChannelSetting(m, edited.get(channel).value()));
					clearAndInit();
				});
		mode.active = !locked();
		addDrawableChild(mode);
		SliderWidget value = new SliderWidget(x + 116, y, 80, 20, Text.empty(), setting.value() / 15.0) {
			{
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("easysuppressions.gui.value", (int) Math.round(this.value * 15)));
			}

			@Override
			protected void applyValue() {
				setChannel(channel, new ChannelSetting(edited.get(channel).mode(), (int) Math.round(this.value * 15)));
			}
		};
		value.active = !locked() && setting.mode() == ChannelMode.FIXED;
		addDrawableChild(value);
	}

	private void setChannel(LightChannel channel, ChannelSetting setting) {
		edited = channel == LightChannel.SKY ? new LightSettings(setting, edited.block()) : new LightSettings(edited.sky(), setting);
		settingsEdited = true;
	}

	private void presetButton(int x, int y, String key, LightSettings preset) {
		ButtonWidget b = ButtonWidget.builder(Text.translatable(key), button -> {
			edited = preset;
			settingsEdited = true;
			clearAndInit();
		}).dimensions(x, y, 96, 20).build();
		b.active = !locked();
		addDrawableChild(b);
	}

	private void confirmLock() {
		if (client == null) {
			return;
		}
		if (locked()) {
			client.setScreen(new ConfirmScreen(yes -> {
				if (yes) {
					send(Payloads.Action.UNLOCK, Optional.empty(), edited);
				}
				client.setScreen(this);
			}, Text.translatable("easysuppressions.gui.confirm_unlock_title"), Text.translatable("easysuppressions.gui.confirm_unlock")));
			return;
		}
		Optional<Zone> zone = typedZone();
		if (zone.isEmpty()) {
			return;
		}
		if (edited.doesNothing()) {
			showMessage(Text.translatable("easysuppressions.message.nothing_to_lock"), true);
			return;
		}
		LightSettings settings = edited;
		client.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				settingsEdited = false;
				cornersEdited = false;
				send(Payloads.Action.LOCK, zone, settings);
			}
			client.setScreen(this);
		}, Text.translatable("easysuppressions.gui.confirm_lock_title"), Text.translatable("easysuppressions.gui.confirm_lock",
				zone.get().describe(), describe(settings.sky()), describe(settings.block()))));
	}

	private static Text describe(ChannelSetting setting) {
		MutableText text = Text.translatable(setting.mode().translationKey());
		if (setting.mode() == ChannelMode.FIXED) {
			text.append(" " + setting.value());
		}
		return text;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int lx = left() + 8;
		int rx = left() + 220;
		int top = top();
		BlockPos p = pos();
		context.drawCenteredTextWithShadow(textRenderer, title.copy().append(" (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")"), width / 2, top + 4, TEXT);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.status",
				statusText(locked(), "easysuppressions.gui.locked", "easysuppressions.gui.unlocked")), lx, top + 18, TEXT);
		drawCornerLabels(context, lx, top + 40);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.custom_mode"), rx, top + 18, GRAY);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.sky_light"), rx, top + 30, TEXT);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.block_light"), rx, top + 64, TEXT);

		int infoY = top + 178;
		if (locked() && data.settings().needsCapture()) {
			long pending = Math.max(0, data.total() - data.recorded());
			context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.pending", pending, data.total())
					.formatted(pending == 0 ? Formatting.GREEN : Formatting.YELLOW), lx, infoY, TEXT);
			if (!data.nearestPending().isEmpty()) {
				MutableText list = Text.translatable("easysuppressions.gui.pending_nearest");
				for (int i = 0; i < data.nearestPending().size(); i++) {
					long key = data.nearestPending().getLong(i);
					list.append((i == 0 ? " " : ", ") + "[" + ChunkPos.getPackedX(key) + ", " + ChunkPos.getPackedZ(key) + "]");
				}
				context.drawWrappedTextWithShadow(textRenderer, list, lx, infoY + 12, W - 16, GRAY);
			}
		} else if (!locked()) {
			context.drawWrappedTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.suppressor_hint"), lx, infoY, W - 16, GRAY);
		} else {
			context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.locked_hint"), lx, infoY, GRAY);
		}
		drawMessage(context, width / 2, top + H - 34);
	}
}
