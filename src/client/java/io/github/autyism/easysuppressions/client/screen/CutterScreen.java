package io.github.autyism.easysuppressions.client.screen;

import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.net.Payloads;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Portal cutter GUI. Opens with or without redstone power. */
public class CutterScreen extends BlockScreen {
	private static final int W = 330;
	private static final int H = 206;

	public CutterScreen(Payloads.ScreenData data) {
		super(Text.translatable("block.easysuppressions.portal_cutter_block"), data);
	}

	@Override
	protected boolean zoneEditable() {
		return true;
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return Math.max(4, (height - H) / 2);
	}

	@Override
	protected void init() {
		int x = left() + 8;
		int y = top() + 42;
		y = addCornerRows(x, y);
		addDrawableChild(saveZoneButton(x, y + 4, 150, LightSettings.DEFAULT));
		addDrawableChild(importButton(x + 156, y + 4, W - 172, LightSettings.DEFAULT));
		addDrawableChild(showZoneButton(x, y + 28, 150));
		addDrawableChild(doneButton(left() + W / 2 - 50, top() + H - 24, 100));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int x = left() + 8;
		int top = top();
		BlockPos p = pos();
		context.drawCenteredTextWithShadow(textRenderer, title.copy().append(" (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")"), width / 2, top + 4, TEXT);
		context.drawTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.status",
				statusText(data.active(), "easysuppressions.gui.cutter_active", "easysuppressions.gui.cutter_inactive")), x, top + 18, TEXT);
		drawCornerLabels(context, x, top + 42);
		int hintY = top + 42 + 2 * ROW + 56;
		context.drawWrappedTextWithShadow(textRenderer, Text.translatable("easysuppressions.gui.cutter_hint"), x, hintY, W - 16, GRAY);
		drawMessage(context, width / 2, top + H - 38);
	}
}
