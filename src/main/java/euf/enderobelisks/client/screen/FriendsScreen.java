package euf.enderobelisks.client.screen;

import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.ObeliskPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The owner's list of players let into a private obelisk: add by name, remove with the cross. Same obsidian look as the menu. */
public class FriendsScreen extends Screen {
	private static final int W = 220;
	private static final int ROW = 18;
	private final ObeliskScreen parent;
	private TextFieldWidget nameField;

	public FriendsScreen(ObeliskScreen parent) {
		super(Text.translatable("ender_obelisks.friends.title"));
		this.parent = parent;
	}

	public ObeliskScreen parent() {
		return parent;
	}

	/** The menu data changed (a friend added or removed): rebuild the rows. */
	public void refresh() {
		clearAndInit();
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = 46;
		String typed = nameField != null ? nameField.getText() : "";
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, x + 5, y + 5, W - 80, 10, Text.translatable("ender_obelisks.friends.name")));
		nameField.setMaxLength(16);
		nameField.setDrawsBackground(false);
		nameField.setEditableColor(ObeliskUi.TEXT);
		nameField.setText(typed);
		addDrawableChild(new RuneButton(x + W - 68, y, 68, 19, Text.translatable("ender_obelisks.friends.add"), ObeliskUi.ICON_FRIENDS, b -> add()));
		int row = y + 30;
		for (MenuData.Friend friend : parent.getData().friends()) {
			addDrawableChild(new RuneButton(x + W - 18, row - 4, 16, 16, Text.literal("✕"), null, b -> remove(friend)));
			row += ROW;
		}
		addDrawableChild(new RuneButton(x, panelBottom() - 26, W, 20, Text.translatable("gui.back"), null, b -> close()));
		setInitialFocus(nameField);
	}

	private int panelBottom() {
		return Math.min(height - 12, 76 + Math.max(1, parent.getData().friends().size()) * ROW + 40);
	}

	private void add() {
		if (nameField.getText().isBlank()) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(parent.getData().sourcePos());
		buf.writeString(nameField.getText().strip());
		ClientPlayNetworking.send(ObeliskPackets.FRIEND_ADD, buf);
		nameField.setText("");
	}

	private void remove(MenuData.Friend friend) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(parent.getData().sourcePos());
		buf.writeUuid(friend.id());
		ClientPlayNetworking.send(ObeliskPackets.FRIEND_REMOVE, buf);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ObeliskUi.background(context, width, height);
		int x = (width - W) / 2;
		ObeliskUi.panel(context, x - 10, 8, x + W + 10, panelBottom());
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.title"), width / 2, 16, ObeliskUi.VIOLET);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.hint"), width / 2, 28, ObeliskUi.MUTED);
		context.fill(x, 46, x + W - 72, 65, 0xFF030205);
		ObeliskUi.outline(context, x, 46, x + W - 72, 65, nameField.isFocused() ? ObeliskUi.TEAL : ObeliskUi.OBSIDIAN_LIGHT);
		ObeliskUi.divider(context, x, x + W, 70);
		int row = 76;
		if (parent.getData().friends().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.none").formatted(Formatting.ITALIC), width / 2, row, ObeliskUi.MUTED);
		}
		for (MenuData.Friend friend : parent.getData().friends()) {
			ObeliskUi.diamond(context, x + 4, row + 3, 2, ObeliskUi.TEAL);
			context.drawTextWithShadow(textRenderer, friend.name(), x + 12, row, ObeliskUi.TEXT);
			row += ROW;
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) { // Enter
			add();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void tick() {
		nameField.tick();
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
