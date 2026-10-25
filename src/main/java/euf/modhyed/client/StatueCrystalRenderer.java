package euf.modhyed.client;

import euf.modhyed.Modhyed;
import euf.modhyed.StatueBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Draws the statue's crystal (models/block/euf_statue_crystal.json) spinning and bobbing above the head, full bright. */
public class StatueCrystalRenderer implements BlockEntityRenderer<StatueBlockEntity> {
	public static final Identifier MODEL = Modhyed.id("block/euf_statue_crystal");
	private static final double CENTER_Y = 19 / 16.0;

	public StatueCrystalRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(StatueBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModel model = client.getBakedModelManager().getModel(MODEL);
		// Offset by position so neighbouring statues don't spin in lockstep
		float time = entity.getWorld().getTime() + tickDelta + Math.floorMod(entity.getPos().hashCode(), 360);

		matrices.push();
		matrices.translate(0.5, CENTER_Y + MathHelper.sin(time * 0.1f) * 0.04, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 3.0f));
		matrices.translate(-0.5, -CENTER_Y, -0.5);
		client.getBlockRenderManager().getModelRenderer().render(matrices.peek(),
				vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), entity.getCachedState(), model,
				1.0f, 1.0f, 1.0f, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
