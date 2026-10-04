package io.github.autyism.easysuppressions.light;

import io.github.autyism.easysuppressions.zone.ZoneIndex;

import java.util.Collection;

/** Immutable snapshot of all locked light zones of one world, as read by the light hook. */
public final class LightZoneSet {
	public static final LightZoneSet EMPTY = new LightZoneSet(ZoneIndex.empty(LightZone::zone));

	private final ZoneIndex<LightZone> index;

	private LightZoneSet(ZoneIndex<LightZone> index) {
		this.index = index;
	}

	public static LightZoneSet of(Collection<LightZone> zones) {
		return zones.isEmpty() ? EMPTY : new LightZoneSet(ZoneIndex.build(zones, LightZone::zone));
	}

	public boolean isEmpty() {
		return index.isEmpty();
	}

	public ZoneIndex<LightZone> index() {
		return index;
	}

	/** The overriding light value for this block, or -1 for vanilla. */
	public int get(LightChannel channel, int x, int y, int z) {
		LightZone zone = index.find(x, y, z);
		return zone == null ? -1 : zone.get(channel, x, y, z);
	}

	/** Whether any zone changes this channel somewhere in the 16x16x16 section. */
	public boolean affectsSection(LightChannel channel, int sx, int sy, int sz) {
		for (LightZone zone : index.inColumn(sx, sz)) {
			if (zone.affects(channel) && zone.zone.intersectsSection(sx, sy, sz)) {
				return true;
			}
		}
		return false;
	}
}
