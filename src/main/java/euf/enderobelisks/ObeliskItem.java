package euf.enderobelisks;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** The obelisk item: a tooltip that explains what it does, since recipe viewers only show the recipe. */
public class ObeliskItem extends BlockItem {
	public ObeliskItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("ender_obelisks.tooltip.use").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("ender_obelisks.tooltip.upgrade").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("ender_obelisks.tooltip.name").formatted(Formatting.DARK_GRAY));
	}
}
