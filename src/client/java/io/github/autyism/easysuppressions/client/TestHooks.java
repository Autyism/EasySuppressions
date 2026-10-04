package io.github.autyism.easysuppressions.client;

import net.minecraft.text.Text;

/** Switches used only by the self test; they never change during normal play. */
public final class TestHooks {
	/** Treat the attack key as held (the window may be unfocused during an unattended test). */
	public static volatile boolean forceAttackHeld;
	/** Last message the server sent for a block GUI. */
	public static volatile Text lastScreenMessage;

	private TestHooks() {
	}
}
