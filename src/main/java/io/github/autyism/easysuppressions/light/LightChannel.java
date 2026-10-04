package io.github.autyism.easysuppressions.light;

import net.minecraft.world.LightType;

/** The two independent light channels. */
public enum LightChannel {
	SKY,
	BLOCK;

	public static LightChannel of(LightType type) {
		return type == LightType.SKY ? SKY : BLOCK;
	}

	public LightType type() {
		return this == SKY ? LightType.SKY : LightType.BLOCK;
	}
}
