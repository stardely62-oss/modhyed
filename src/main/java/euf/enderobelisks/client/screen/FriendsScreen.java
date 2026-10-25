package euf.enderobelisks.client.screen;

import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.ObeliskPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/** The owner's list of players let into a private obelisk: add by name, remove with the cross. */
public class FriendsScreen extends Screen {
	private static final int W = 220;
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
		int y = 40;
		String typed = nameField != null ? nameField.getText() : "";
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, x, y, W - 74, 18, Text.translatable("ender_obelisks.friends.name")));
		nameField.setMaxLength(16);
		nameField.setText(typed);
		addDrawableChild(ButtonWidget.builder(Text.translatable("ender_obelisks.friends.add"), b -> add()).dimensions(x + W - 70, y, 70, 18).build());
		int row = y + 30;
		for (MenuData.Friend friend : parent.getData().friends()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> remove(friend)).dimensions(x + W - 20, row, 20, 16).build());
			row += 20;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close()).dimensions(x, height - 30, W, 20).build());
		setInitialFocus(nameField);
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
		renderBackground(context);
		int x = (width - W) / 2;
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.title"), width / 2, 14, 0xFFE6E8EA);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.hint"), width / 2, 26, 0xFF9AA0A8);
		int row = 74;
		if (parent.getData().friends().isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("ender_obelisks.friends.none"), x, row, 0xFF9AA0A8);
		}
		for (MenuData.Friend friend : parent.getData().friends()) {
			context.drawTextWithShadow(textRenderer, friend.name(), x, row, 0xFFE6E8EA);
			row += 20;
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
