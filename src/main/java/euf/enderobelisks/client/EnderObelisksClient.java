package euf.enderobelisks.client;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.client.map.MapScanner;
import euf.enderobelisks.client.screen.FriendsScreen;
import euf.enderobelisks.client.screen.ObeliskScreen;
import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.ObeliskPackets;
import euf.enderobelisks.net.ObeliskSync;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class EnderObelisksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRendererFactories.register(EnderObelisks.OBELISK_BLOCK_ENTITY, ObeliskRenderer::new);
		// The eye and shard models belong to no blockstate, so they have to be loaded explicitly
		ModelLoadingPlugin.register(context -> context.addModels(ObeliskRenderer.EYE_MODEL, ObeliskRenderer.SHARD_MODEL,
				ObeliskRenderer.SHARD_GLOW_MODEL));

		MapScanner.register();
		ClientPlayNetworking.registerGlobalReceiver(ObeliskPackets.MENU, (client, handler, buf, sender) -> {
			MenuData data = MenuData.read(buf);
			client.execute(() -> {
				if (client.currentScreen instanceof ObeliskScreen screen && screen.isFor(data.sourcePos())) {
					screen.update(data);
				} else if (client.currentScreen instanceof FriendsScreen friends && friends.parent().isFor(data.sourcePos())) {
					friends.parent().update(data);
					friends.refresh();
				} else {
					client.setScreen(new ObeliskScreen(data));
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ObeliskSync.SYNC, (client, handler, buf, sender) -> {
			var entries = ObeliskSync.read(buf);
			client.execute(() -> ObeliskClientCache.set(entries));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> ObeliskClientCache.set(List.of())));
	}
}
