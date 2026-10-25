package euf.enderobelisks.client;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.client.map.MapScanner;
import euf.enderobelisks.client.screen.FriendsScreen;
import euf.enderobelisks.client.screen.ObeliskScreen;
import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.ObeliskPackets;
import euf.enderobelisks.net.ObeliskSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

public class EnderObelisksClient implements ClientModInitializer {
	private static final Map<Item, String> LORE = Map.of(
			EnderObelisks.OBELISK_ITEM, "obelisk",
			EnderObelisks.RUINED_OBELISK_ITEM, "ruined_obelisk",
			EnderObelisks.TIME_SHARD, "time_shard",
			EnderObelisks.RETURN_SHARD, "return_shard");

	/**
	 * Lore under the item name, in italics, from ender_obelisks.lore.<item>.1, .2, ... (as many lines as the language
	 * file has); the practical hints the items add themselves follow after an empty line.
	 */
	static void addLore(ItemStack stack, TooltipContext context, List<Text> lines) {
		String item = LORE.get(stack.getItem());
		if (item == null || lines.isEmpty()) {
			return;
		}
		List<Text> lore = new ArrayList<>();
		for (int i = 1; Language.getInstance().hasTranslation("ender_obelisks.lore." + item + "." + i); i++) {
			lore.add(Text.translatable("ender_obelisks.lore." + item + "." + i).formatted(Formatting.GRAY, Formatting.ITALIC));
		}
		if (lore.isEmpty()) {
			return;
		}
		hideHints(lines);
		if (lines.size() > 1) {
			lore.add(Text.empty());
		}
		lines.addAll(1, lore);
	}

	/** How-to hints the items add: shown only while Shift is held, so the tooltip is the name and the story. */
	private static final Set<String> HINTS = Set.of("ender_obelisks.tooltip.use", "ender_obelisks.tooltip.upgrade", "ender_obelisks.tooltip.name",
			"ender_obelisks.return.tooltip_use", "ender_obelisks.return.tooltip_unbound");

	private static void hideHints(List<Text> lines) {
		if (Screen.hasShiftDown()) {
			return;
		}
		boolean removed = lines.removeIf(line -> line.getContent() instanceof TranslatableTextContent t && HINTS.contains(t.getKey()));
		if (removed) {
			lines.add(Text.translatable("ender_obelisks.tooltip.more").formatted(Formatting.DARK_GRAY));
		}
	}

	@Override
	public void onInitializeClient() {
		BlockEntityRendererFactories.register(EnderObelisks.OBELISK_BLOCK_ENTITY, ObeliskRenderer::new);
		// The eye and shard models belong to no blockstate, so they have to be loaded explicitly
		ModelLoadingPlugin.register(context -> context.addModels(ObeliskRenderer.EYE_MODEL, ObeliskRenderer.SHARD_MODEL,
				ObeliskRenderer.SHARD_GLOW_MODEL));

		MapScanner.register();
		ItemTooltipCallback.EVENT.register(EnderObelisksClient::addLore);
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
