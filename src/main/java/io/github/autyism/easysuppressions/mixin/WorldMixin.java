package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.world.ZoneHolder;
import io.github.autyism.easysuppressions.world.ZoneHolderAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every world (server and client) its zone snapshot holder. */
@Mixin(World.class)
abstract class WorldMixin implements ZoneHolderAccess {
	@Unique
	private volatile ZoneHolder easysuppressions$holder;

	@Override
	public ZoneHolder easysuppressions$zones() {
		ZoneHolder holder = easysuppressions$holder;
		if (holder == null) {
			synchronized (this) {
				holder = easysuppressions$holder;
				if (holder == null) {
					holder = new ZoneHolder();
					easysuppressions$holder = holder;
				}
			}
		}
		return holder;
	}
}
