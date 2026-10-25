package euf.enderobelisks.compat;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskColors;
import euf.enderobelisks.client.ObeliskClientCache;
import euf.enderobelisks.net.ObeliskSync;
import java.util.EnumSet;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.MarkerOverlay;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.model.MapImage;
import journeymap.client.api.model.TextProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Shows every obelisk you may use as a marker on JourneyMap's maps, in its colour, with its name. Loaded by
 * JourneyMap only (the "journeymap" entrypoint); without JourneyMap this class is never touched.
 */
@ClientPlugin
public class JourneyMapPlugin implements IClientPlugin {
	private static final Identifier ICON = EnderObelisks.id("textures/gui/map_marker.png");
	private IClientAPI api;

	@Override
	public void initialize(IClientAPI jmClientApi) {
		this.api = jmClientApi;
		api.subscribe(EnderObelisks.MOD_ID, EnumSet.of(ClientEvent.Type.MAPPING_STARTED));
		// The cache changes off the render thread's schedule only through packets handled on it; redraw on each change
		ObeliskClientCache.addListener(() -> MinecraftClient.getInstance().execute(this::refresh));
		EnderObelisks.LOGGER.info("JourneyMap integration ready");
	}

	@Override
	public String getModId() {
		return EnderObelisks.MOD_ID;
	}

	@Override
	public void onEvent(ClientEvent event) {
		if (event.type == ClientEvent.Type.MAPPING_STARTED) {
			refresh();
		}
	}

	private void refresh() {
		if (api == null) {
			return;
		}
		api.removeAll(EnderObelisks.MOD_ID);
		int shown = 0;
		for (ObeliskSync.Entry entry : ObeliskClientCache.entries()) {
			int rgb = ObeliskColors.rgb(entry.color());
			MapImage icon = new MapImage(ICON, 16, 16).setColor(rgb).setDisplayWidth(12).setDisplayHeight(12)
					.setAnchorX(6).setAnchorY(6);
			MarkerOverlay marker = new MarkerOverlay(EnderObelisks.MOD_ID, entry.id().toString(), entry.pos(), icon);
			marker.setDimension(RegistryKey.of(RegistryKeys.WORLD, entry.dimension()));
			marker.setLabel(entry.name());
			marker.setTitle(entry.name() + " · " + entry.ownerName());
			marker.setTextProperties(new TextProperties().setColor(rgb).setBackgroundOpacity(0.4f).setOffsetY(10));
			try {
				api.show(marker);
				shown++;
			} catch (Exception e) {
				EnderObelisks.LOGGER.warn("JourneyMap refused the marker for {}", entry.name(), e);
			}
		}
		EnderObelisks.LOGGER.info("JourneyMap: showing {} obelisk markers", shown);
	}
}
