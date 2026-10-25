package euf.enderobelisks.client.screen;

import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.ObeliskColors;
import euf.enderobelisks.client.map.MapCache;
import euf.enderobelisks.client.map.MapScanner;
import euf.enderobelisks.client.map.MapTiles;
import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.MenuData.Entry;
import euf.enderobelisks.net.ObeliskPackets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The obelisk menu: an explored map of the dimension with every obelisk you may use (names, range circle, your
 * position) on the left; on the right the obelisk you stand at (name, access, upgrade), the selected destination
 * and a list sorted by distance. Drag to pan, scroll to zoom, click a marker or a list row to select.
 */
public class ObeliskScreen extends Screen {
	private static final int PANEL_W = 180;
	private static final int PAD = 6;
	private static final int ROW_H = 11;
	private static final double MIN_SCALE = 0.01;
	private static final double MAX_SCALE = 8.0;

	private static final int COLOR_CURRENT = 0xFFFFD24A;
	private static final int COLOR_FAR = 0xFF6C7078;
	private static final int COLOR_RANGE = 0xA07FE0C8;
	private static final int COLOR_LINE = 0xFF3A3F46;
	private static final int COLOR_MUTED = 0xFF9AA0A8;
	private static final int COLOR_TITLE = 0xFFE6E8EA;

	private MenuData data;
	private long dataTime;
	@Nullable
	private UUID selected;
	private double centerX;
	private double centerZ;
	private double scale;
	private boolean dragging;
	private int listScroll;

	private int mapX0;
	private int mapY0;
	private int mapX1;
	private int mapY1;
	private int panelX;
	private int listY;

	private TextFieldWidget nameField;
	private ButtonWidget renameButton;
	private ButtonWidget accessButton;
	private ButtonWidget friendsButton;
	private ButtonWidget upgradeButton;
	private ButtonWidget teleportButton;

	public ObeliskScreen(MenuData data) {
		super(Text.translatable("ender_obelisks.menu.title"));
		this.data = data;
		this.centerX = data.sourcePos().getX() + 0.5;
		this.centerZ = data.sourcePos().getZ() + 0.5;
	}

	public boolean isFor(BlockPos sourcePos) {
		return data.sourcePos().equals(sourcePos);
	}

	public MenuData getData() {
		return data;
	}

	public void update(MenuData newData) {
		this.data = newData;
		this.dataTime = worldTime();
		if (selected != null && newData.entries().stream().noneMatch(e -> e.id().equals(selected))) {
			selected = null;
		}
		if (nameField != null && !nameField.isFocused()) {
			nameField.setText(newData.name());
		}
		refreshButtons();
	}

	@Override
	protected void init() {
		panelX = width - PANEL_W;
		mapX0 = PAD;
		mapY0 = 20;
		mapX1 = panelX - PAD;
		mapY1 = height - PAD;
		if (scale == 0) {
			int size = Math.min(mapX1 - mapX0, mapY1 - mapY0);
			scale = MathHelper.clamp(size / (data.range() * 2.3), MIN_SCALE, MAX_SCALE);
		}
		dataTime = worldTime();

		int x = panelX + PAD;
		int w = PANEL_W - 2 * PAD;
		nameField = new TextFieldWidget(textRenderer, x, 30, w - 24, 16, Text.translatable("ender_obelisks.menu.name"));
		nameField.setMaxLength(ObeliskService.MAX_NAME_LENGTH);
		nameField.setText(data.name());
		addDrawableChild(nameField);
		renameButton = addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> rename()).dimensions(x + w - 20, 30, 20, 16).build());
		accessButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> send(ObeliskPackets.TOGGLE_PUBLIC, null))
				.dimensions(x, 64, w - 50, 16).build());
		friendsButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> client.setScreen(new FriendsScreen(this)))
				.dimensions(x + w - 48, 64, 48, 16).build());
		upgradeButton = addDrawableChild(ButtonWidget.builder(Text.translatable("ender_obelisks.menu.upgrade"), b -> upgrade())
				.dimensions(x, 138, w, 16).build());
		teleportButton = addDrawableChild(ButtonWidget.builder(Text.translatable("ender_obelisks.menu.teleport"), b -> teleportSelected())
				.dimensions(x, 220, w, 18).build());
		listY = 254;
		refreshButtons();
	}

	private void refreshButtons() {
		if (teleportButton == null) {
			return;
		}
		boolean owner = data.isOwner();
		nameField.setEditable(owner);
		renameButton.active = owner;
		accessButton.active = owner;
		accessButton.setMessage(Text.translatable(data.isPublic() ? "ender_obelisks.menu.access_public" : "ender_obelisks.menu.access_private"));
		friendsButton.visible = owner;
		friendsButton.setMessage(Text.translatable("ender_obelisks.menu.friends", data.friends().size()));
		List<ItemStack> cost = data.nextCost();
		upgradeButton.active = owner && !data.maxTier() && (creative() || cost.stream().allMatch(s -> have(s) >= s.getCount()));
		Entry target = selectedEntry();
		teleportButton.active = target != null && !target.id().equals(data.sourceId()) && inRange(target) && cooldownLeft() == 0;
	}

	@Override
	public void tick() {
		nameField.tick();
		refreshButtons();
	}

	// ---- actions (public so the CI harness can drive them) ----

	public void select(@Nullable UUID id) {
		selected = id;
		refreshButtons();
	}

	public void teleportSelected() {
		Entry target = selectedEntry();
		if (target != null) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeBlockPos(data.sourcePos());
			buf.writeUuid(target.id());
			ClientPlayNetworking.send(ObeliskPackets.TELEPORT, buf);
			close();
		}
	}

	public void upgrade() {
		send(ObeliskPackets.UPGRADE, null);
	}

	private void rename() {
		send(ObeliskPackets.RENAME, nameField.getText());
	}

	private void send(Identifier channel, @Nullable String text) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(data.sourcePos());
		if (text != null) {
			buf.writeString(text);
		}
		ClientPlayNetworking.send(channel, buf);
	}

	// ---- helpers ----

	private long worldTime() {
		return client != null && client.world != null ? client.world.getTime() : 0;
	}

	private int cooldownLeft() {
		return (int) Math.max(0, data.cooldownTicks() - (worldTime() - dataTime));
	}

	private boolean creative() {
		return client != null && client.player != null && client.player.getAbilities().creativeMode;
	}

	private int have(ItemStack stack) {
		return client == null || client.player == null ? 0 : client.player.getInventory().count(stack.getItem());
	}

	private double distance(Entry entry) {
		return Math.sqrt(entry.pos().getSquaredDistance(data.sourcePos()));
	}

	private boolean sameDimension(Entry entry) {
		return entry.dimension().equals(data.dimension());
	}

	/** Same dimension: within range. Another dimension: only if the server allows it from this tier. */
	private boolean inRange(Entry entry) {
		if (!sameDimension(entry)) {
			return data.crossDimension() && data.tier() >= data.crossDimensionMinTier();
		}
		return distance(entry) <= data.range();
	}

	private static Text dimensionName(Identifier id) {
		String key = "ender_obelisks.dim." + id.getPath();
		return Text.translatableWithFallback(key, id.getPath());
	}

	@Nullable
	private Entry selectedEntry() {
		return selected == null ? null : data.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
	}

	private List<Entry> sortedEntries() {
		List<Entry> list = new ArrayList<>(data.entries());
		// This dimension first, nearest first; then the other dimensions
		list.sort(Comparator.comparing((Entry e) -> !sameDimension(e)).thenComparingDouble(this::distance));
		return list;
	}

	private int colorOf(Entry entry) {
		if (entry.id().equals(data.sourceId())) {
			return COLOR_CURRENT;
		}
		if (!inRange(entry)) {
			return COLOR_FAR;
		}
		return 0xFF000000 | ObeliskColors.rgb(entry.color());
	}

	private double mapCenterX() {
		return (mapX0 + mapX1) / 2.0;
	}

	private double mapCenterY() {
		return (mapY0 + mapY1) / 2.0;
	}

	private int toScreenX(double worldX) {
		return (int) Math.round(mapCenterX() + (worldX - centerX) * scale);
	}

	private int toScreenY(double worldZ) {
		return (int) Math.round(mapCenterY() + (worldZ - centerZ) * scale);
	}

	private boolean inMap(double x, double y) {
		return x >= mapX0 && x < mapX1 && y >= mapY0 && y < mapY1;
	}

	@Nullable
	private Entry entryAt(double mouseX, double mouseY) {
		Entry best = null;
		double bestDist = 7 * 7;
		for (Entry entry : data.entries()) {
			if (!sameDimension(entry)) {
				continue;
			}
			double dx = toScreenX(entry.pos().getX() + 0.5) - mouseX;
			double dy = toScreenY(entry.pos().getZ() + 0.5) - mouseY;
			if (dx * dx + dy * dy < bestDist) {
				bestDist = dx * dx + dy * dy;
				best = entry;
			}
		}
		return best;
	}

	// ---- drawing ----

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.title").append(": ").append(data.name()),
				mapX0, 6, COLOR_TITLE);

		context.fill(mapX0 - 1, mapY0 - 1, mapX1 + 1, mapY1 + 1, COLOR_LINE);
		context.fill(mapX0, mapY0, mapX1, mapY1, 0xFF0C0E10);
		context.enableScissor(mapX0, mapY0, mapX1, mapY1);
		drawTiles(context);
		drawRangeCircle(context);
		drawMarkers(context, mouseX, mouseY);
		drawPlayer(context);
		context.disableScissor();
		if (inMap(mouseX, mouseY)) {
			double wx = centerX + (mouseX - mapCenterX()) / scale;
			double wz = centerZ + (mouseY - mapCenterY()) / scale;
			context.drawTextWithShadow(textRenderer, String.format("X %d  Z %d", (int) Math.floor(wx), (int) Math.floor(wz)),
					mapX0 + 4, mapY1 - 12, COLOR_MUTED);
		}

		drawPanel(context, mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);

		Entry hovered = inMap(mouseX, mouseY) ? entryAt(mouseX, mouseY) : null;
		if (hovered != null) {
			context.drawTooltip(textRenderer, describe(hovered), mouseX, mouseY);
		}
	}

	private void drawTiles(DrawContext context) {
		MapCache cache = MapScanner.cache();
		if (cache == null) {
			return;
		}
		double halfW = (mapX1 - mapX0) / 2.0 / scale;
		double halfH = (mapY1 - mapY0) / 2.0 / scale;
		int tx0 = Math.floorDiv((int) Math.floor(centerX - halfW), MapTiles.TILE_BLOCKS);
		int tx1 = Math.floorDiv((int) Math.floor(centerX + halfW), MapTiles.TILE_BLOCKS);
		int tz0 = Math.floorDiv((int) Math.floor(centerZ - halfH), MapTiles.TILE_BLOCKS);
		int tz1 = Math.floorDiv((int) Math.floor(centerZ + halfH), MapTiles.TILE_BLOCKS);
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				Identifier texture = MapTiles.get(cache, tx, tz);
				if (texture == null) {
					continue;
				}
				context.getMatrices().push();
				context.getMatrices().translate(mapCenterX() + (tx * MapTiles.TILE_BLOCKS - centerX) * scale,
						mapCenterY() + (tz * MapTiles.TILE_BLOCKS - centerZ) * scale, 0);
				context.getMatrices().scale((float) scale, (float) scale, 1.0f);
				context.drawTexture(texture, 0, 0, 0, 0, MapTiles.TILE_BLOCKS, MapTiles.TILE_BLOCKS, MapTiles.TILE_BLOCKS, MapTiles.TILE_BLOCKS);
				context.getMatrices().pop();
			}
		}
	}

	private void drawRangeCircle(DrawContext context) {
		double cx = data.sourcePos().getX() + 0.5;
		double cz = data.sourcePos().getZ() + 0.5;
		double r = data.range();
		int steps = (int) MathHelper.clamp(r * scale * 0.6, 48, 720);
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			int x = toScreenX(cx + Math.cos(a) * r);
			int y = toScreenY(cz + Math.sin(a) * r);
			context.fill(x - 1, y - 1, x + 1, y + 1, COLOR_RANGE);
		}
	}

	private void drawMarkers(DrawContext context, int mouseX, int mouseY) {
		Entry hovered = entryAt(mouseX, mouseY);
		for (Entry entry : data.entries()) {
			if (!sameDimension(entry)) {
				continue; // other dimensions are in the list, not on this map
			}
			int x = toScreenX(entry.pos().getX() + 0.5);
			int y = toScreenY(entry.pos().getZ() + 0.5);
			if (!inMap(x, y)) {
				continue;
			}
			int color = colorOf(entry);
			boolean isSelected = entry.id().equals(selected);
			// A small diamond with a dark outline; the obelisk you stand at is a size larger
			int r = entry.id().equals(data.sourceId()) ? 4 : 3;
			if (isSelected) {
				diamond(context, x, y, r + 2, 0xFFFFFFFF);
			}
			diamond(context, x, y, r + 1, 0xFF000000);
			diamond(context, x, y, r, color);
			context.fill(x, y - r + 1, x + 1, y - r + 2, 0x80FFFFFF);
			boolean label = isSelected || entry == hovered || entry.id().equals(data.sourceId()) || scale >= 0.12;
			if (label) {
				context.drawCenteredTextWithShadow(textRenderer, entry.name(), x, y + 7, color);
			}
		}
	}

	private static void diamond(DrawContext context, int x, int y, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			context.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
		}
	}

	private void drawPlayer(DrawContext context) {
		if (client == null || client.player == null) {
			return;
		}
		int x = toScreenX(client.player.getX());
		int y = toScreenY(client.player.getZ());
		context.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
		context.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
	}

	private void drawPanel(DrawContext context, int mouseX, int mouseY) {
		int x = panelX + PAD;
		int right = width - PAD;
		context.fill(panelX, 0, width, height, 0xD0101214);
		context.fill(panelX, 0, panelX + 1, height, COLOR_LINE);

		context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.this_obelisk").formatted(Formatting.AQUA), x, 8, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.name"), x, 20, COLOR_MUTED);
		context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.tier", data.tier(), data.range()),
				x, 52, 0xFFFFFFFF);

		// Marker colour: eight swatches; the chosen one is framed. Only the owner can change it.
		for (int i = 0; i < ObeliskColors.RGB.length; i++) {
			int sx = swatchX(i);
			if (i == data.color()) {
				context.fill(sx - 1, SWATCH_Y - 1, sx + SWATCH + 1, SWATCH_Y + SWATCH + 1, 0xFFFFFFFF);
			}
			context.fill(sx, SWATCH_Y, sx + SWATCH, SWATCH_Y + SWATCH, 0xFF000000 | ObeliskColors.rgb(i));
		}

		List<ItemStack> cost = data.nextCost();
		if (data.maxTier()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.max_tier"), x, 104, 0xFFFFD24A);
		} else {
			context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.next_tier", data.nextRange()),
					x, 104, COLOR_MUTED);
			int ix = x;
			for (ItemStack stack : cost) {
				context.drawItem(stack, ix, 116);
				String count = have(stack) + "/" + stack.getCount();
				boolean enough = creative() || have(stack) >= stack.getCount();
				context.drawTextWithShadow(textRenderer, count, ix + 18, 120, enough ? 0xFF8CF08C : 0xFFF07070);
				ix += 22 + textRenderer.getWidth(count);
			}
			if (mouseY >= 116 && mouseY < 132 && mouseX >= x && mouseX < ix) {
				int index = Math.min(cost.size() - 1, (mouseX - x) / Math.max(1, (ix - x) / cost.size()));
				context.drawItemTooltip(textRenderer, cost.get(index), mouseX, mouseY);
			}
		}

		context.fill(x, 160, right, 161, COLOR_LINE);
		Entry target = selectedEntry();
		if (target == null) {
			context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.menu.pick"), x, 168, COLOR_MUTED);
		} else {
			List<Text> lines = describe(target);
			for (int i = 0; i < lines.size() && i < 5; i++) {
				context.drawTextWithShadow(textRenderer, lines.get(i), x, 166 + i * 10, 0xFFFFFFFF);
			}
		}

		context.fill(x, listY - 6, right, listY - 5, COLOR_LINE);
		List<Entry> entries = sortedEntries();
		int rows = Math.max(0, (height - PAD - listY) / ROW_H);
		listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, entries.size() - rows));
		for (int i = 0; i < rows && i + listScroll < entries.size(); i++) {
			Entry entry = entries.get(i + listScroll);
			int y = listY + i * ROW_H;
			if (entry.id().equals(selected)) {
				context.fill(x - 2, y - 1, right + 2, y + ROW_H - 1, 0x40FFFFFF);
			}
			String dist = sameDimension(entry) ? (int) distance(entry) + "m" : dimensionName(entry.dimension()).getString();
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(entry.name(), right - x - textRenderer.getWidth(dist) - 6),
					x, y, colorOf(entry));
			context.drawTextWithShadow(textRenderer, dist, right - textRenderer.getWidth(dist), y, COLOR_MUTED);
		}
	}

	private List<Text> describe(Entry entry) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(entry.name()).formatted(Formatting.BOLD));
		lines.add(Text.translatable(entry.isPublic() ? "ender_obelisks.menu.owner_public" : "ender_obelisks.menu.owner_private",
				entry.ownerName()).formatted(Formatting.GRAY));
		if (sameDimension(entry)) {
			lines.add(Text.translatable("ender_obelisks.menu.where", entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
					(int) distance(entry)).formatted(Formatting.GRAY));
		} else {
			lines.add(Text.translatable("ender_obelisks.menu.where_dimension", entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
					dimensionName(entry.dimension())).formatted(Formatting.GRAY));
		}
		if (entry.id().equals(data.sourceId())) {
			lines.add(Text.translatable("ender_obelisks.menu.status_here").formatted(Formatting.GOLD));
		} else if (!inRange(entry) && !sameDimension(entry)) {
			lines.add((data.crossDimension() ? Text.translatable("ender_obelisks.menu.status_dimension_tier", data.crossDimensionMinTier())
					: Text.translatable("ender_obelisks.menu.status_dimension_off")).formatted(Formatting.RED));
		} else if (!inRange(entry)) {
			lines.add(Text.translatable("ender_obelisks.menu.status_far", data.range()).formatted(Formatting.RED));
		} else if (cooldownLeft() > 0) {
			lines.add(Text.translatable("ender_obelisks.menu.status_cooldown", (cooldownLeft() + 19) / 20).formatted(Formatting.YELLOW));
		} else {
			lines.add(Text.translatable("ender_obelisks.menu.status_ready").formatted(Formatting.GREEN));
		}
		return lines;
	}

	private static final int SWATCH = 12;
	private static final int SWATCH_Y = 86;

	private int swatchX(int i) {
		return panelX + PAD + i * (SWATCH + 9);
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (data.isOwner() && mouseY >= SWATCH_Y && mouseY < SWATCH_Y + SWATCH) {
			for (int i = 0; i < ObeliskColors.RGB.length; i++) {
				if (mouseX >= swatchX(i) && mouseX < swatchX(i) + SWATCH) {
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeBlockPos(data.sourcePos());
					buf.writeVarInt(i);
					ClientPlayNetworking.send(ObeliskPackets.SET_COLOR, buf);
					return true;
				}
			}
		}
		if (inMap(mouseX, mouseY)) {
			Entry entry = entryAt(mouseX, mouseY);
			if (entry != null) {
				select(entry.id());
			} else {
				dragging = true;
			}
			return true;
		}
		if (mouseX >= panelX && mouseY >= listY) {
			int index = (int) ((mouseY - listY) / ROW_H) + listScroll;
			List<Entry> entries = sortedEntries();
			if (index >= 0 && index < entries.size()) {
				Entry entry = entries.get(index);
				select(entry.id());
				centerX = entry.pos().getX() + 0.5;
				centerZ = entry.pos().getZ() + 0.5;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			centerX -= deltaX / scale;
			centerZ -= deltaY / scale;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inMap(mouseX, mouseY)) {
			// Zoom around the cursor: the block under it stays under it
			double wx = centerX + (mouseX - mapCenterX()) / scale;
			double wz = centerZ + (mouseY - mapCenterY()) / scale;
			scale = MathHelper.clamp(scale * (amount > 0 ? 1.25 : 0.8), MIN_SCALE, MAX_SCALE);
			centerX = wx - (mouseX - mapCenterX()) / scale;
			centerZ = wz - (mouseY - mapCenterY()) / scale;
			return true;
		}
		if (mouseX >= panelX && mouseY >= listY) {
			listScroll -= (int) Math.signum(amount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	/** Zoom so the whole range circle fits; used by the harness for its screenshots. */
	public void zoomToRange() {
		int size = Math.min(mapX1 - mapX0, mapY1 - mapY0);
		scale = MathHelper.clamp(size / (data.range() * 2.3), MIN_SCALE, MAX_SCALE);
		centerX = data.sourcePos().getX() + 0.5;
		centerZ = data.sourcePos().getZ() + 0.5;
	}

	public void zoomTo(double newScale) {
		scale = MathHelper.clamp(newScale, MIN_SCALE, MAX_SCALE);
	}

	@Override
	public boolean shouldPause() {
		// The integrated server must keep running: it answers this menu's packets
		return false;
	}
}
