package euf.modhyed.client;

import euf.modhyed.EufGolemEntity;
import euf.modhyed.Modhyed;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class EufGolemRenderer extends MobEntityRenderer<EufGolemEntity, EufGolemModel> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Modhyed.id("euf_golem"), "main");
	private static final Identifier TEXTURE = Modhyed.id("textures/entity/euf_golem.png");
	private static final RenderLayer GLOW = RenderLayer.getEyes(Modhyed.id("textures/entity/euf_golem_glow.png"));

	public EufGolemRenderer(EntityRendererFactory.Context context) {
		super(context, new EufGolemModel(context.getPart(LAYER)), 0.4f);
		// Eyes, chest core and crystal glow in the dark, like an enderman's eyes
		addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public RenderLayer getEyesTexture() {
				return GLOW;
			}
		});
	}

	@Override
	public Identifier getTexture(EufGolemEntity entity) {
		return TEXTURE;
	}
}
