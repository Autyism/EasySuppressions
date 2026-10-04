package io.github.autyism.easysuppressions.world;

import io.github.autyism.easysuppressions.light.LightZoneSet;
import io.github.autyism.easysuppressions.zone.Zone;
import io.github.autyism.easysuppressions.zone.ZoneIndex;

/**
 * Per-world (server or client) snapshot of what the hooks need to know: the active portal cutter
 * zones and the locked light zones. Written on the owning thread, read from anywhere.
 */
public final class ZoneHolder {
	public static final ZoneIndex<Zone> NO_CUTTERS = ZoneIndex.empty(z -> z);

	public volatile ZoneIndex<Zone> cutters = NO_CUTTERS;
	public volatile LightZoneSet light = LightZoneSet.EMPTY;

	public boolean isPortalProtected(int x, int y, int z) {
		ZoneIndex<Zone> index = cutters;
		return !index.isEmpty() && index.find(x, y, z) != null;
	}
}
