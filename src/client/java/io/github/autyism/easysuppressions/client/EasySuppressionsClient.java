package io.github.autyism.easysuppressions.client;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.client.model.ActiveSwitchModel;
import io.github.autyism.easysuppressions.client.render.ZoneRenderer;
import io.github.autyism.easysuppressions.client.screen.BlockScreen;
import io.github.autyism.easysuppressions.client.screen.CutterScreen;
import io.github.autyism.easysuppressions.client.screen.SuppressorScreen;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.light.LightHooks;
import io.github.autyism.easysuppressions.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;

public class EasySuppressionsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();

		// The active model has a glow overlay with transparent pixels.
		BlockRenderLayerMap.putBlocks(BlockRenderLayer.CUTOUT, ModContent.PORTAL_CUTTER_BLOCK, ModContent.LIGHT_SUPPRESSOR_BLOCK);
		CustomUnbakedBlockStateModel.register(EasySuppressions.id("active_switch"), ActiveSwitchModel.Unbaked.CODEC);
		LightHooks.clientSectionOverride = ClientLight::buildSection;

		// Login handshake: answer the server's version check.
		ClientConfigurationNetworking.registerGlobalReceiver(Payloads.Hello.ID, (payload, context) ->
				context.responseSender().sendPacket(new Payloads.HelloReply(EasySuppressions.version())));

		ClientPlayNetworking.registerGlobalReceiver(Payloads.ConfigSync.ID, (payload, context) -> EsConfig.setSynced(payload.values()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CutterZones.ID, (payload, context) -> ClientZones.onCutterZones(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LightZones.ID, (payload, context) -> ClientZones.onLightZones(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LightColumns.ID, (payload, context) -> ClientZones.onLightColumns(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Screen.ID, (payload, context) -> onScreen(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.ScreenMessage.ID, (payload, context) -> {
			TestHooks.lastScreenMessage = payload.message();
			if (context.client().currentScreen instanceof BlockScreen screen && screen.pos().equals(payload.pos())) {
				screen.showMessage(payload.message(), payload.error());
			} else if (context.client().player != null) {
				context.client().player.sendMessage(payload.message(), true);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EsConfig.setSynced(null);
			ClientZones.onDisconnect();
		});

		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> ClientZones.onChunkLoad(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ClientZones.onChunkUnload(chunk));
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, world) -> {
			if (be instanceof SuppressionBlockEntity sbe) {
				ClientZones.onBlockEntityLoad(sbe, world);
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, world) -> {
			if (be instanceof SuppressionBlockEntity sbe) {
				ClientZones.onBlockEntityUnload(sbe);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientZones::tick);
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ZoneRenderer::render);

		if (Boolean.getBoolean("es.selftest")) {
			io.github.autyism.easysuppressions.client.selftest.SelfTest.install();
		}
	}

	private static void onScreen(MinecraftClient client, Payloads.Screen payload) {
		if (payload.open()) {
			client.setScreen(payload.data().kind() == SuppressorKind.PORTAL_CUTTER
					? new CutterScreen(payload.data()) : new SuppressorScreen(payload.data()));
		} else if (client.currentScreen instanceof BlockScreen screen && screen.pos().equals(payload.data().pos())) {
			screen.update(payload.data());
		}
	}
}
