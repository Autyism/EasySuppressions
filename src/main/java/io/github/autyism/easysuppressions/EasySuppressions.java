package io.github.autyism.easysuppressions;

import io.github.autyism.easysuppressions.command.EsCommand;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.item.PortalPickaxeItem;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.net.Handshake;
import io.github.autyism.easysuppressions.net.ServerNet;
import io.github.autyism.easysuppressions.vanilla.VanillaSupport;
import io.github.autyism.easysuppressions.world.ServerZones;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasySuppressions implements ModInitializer {
	public static final String MOD_ID = "easysuppressions";
	public static final String NAME = "Autyism's Easy Suppressions";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	private static String version;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static String version() {
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer(MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		}
		return version;
	}

	@Override
	public void onInitialize() {
		ModContent.init();
		EsConfig.load();
		ServerNet.registerPayloads();
		ServerNet.init();
		Handshake.init();
		VanillaSupport.init();
		ServerZones.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EsCommand.register(dispatcher));

		// Selectors never break blocks; the portal pickaxe only works on nether portal blocks.
		// Runs on both sides: on the client it also stops the break packet from being sent.
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof SelectorItem) {
				return ActionResult.FAIL;
			}
			if (stack.getItem() instanceof PortalPickaxeItem && !PortalPickaxeItem.isMineable(world.getBlockState(pos))) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		LOGGER.info("{} {} loaded", NAME, version());
	}
}
