package plugin.talesloader.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import plugin.talesloader.Config;
import plugin.talesloader.registry.ModDataComponents;
import plugin.talesloader.util.TimeFormat;

import java.util.List;

public class ChunkLoaderItem extends BlockItem {
    public ChunkLoaderItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // The static description comes from Create's tooltip system (see ClientSetup); only the
        // stored fuel is dynamic enough to belong here.
        Long stored = stack.get(ModDataComponents.STORED_FUEL.get());
        if (stored != null && stored > 0L) {
            int singleChunkRate = Math.max(1, Config.baseRate() + Config.perChunkRate());
            tooltip.add(Component.translatable("tooltip.talesloader.stored_fuel",
                    TimeFormat.ticksToClock(stored / singleChunkRate)).withStyle(ChatFormatting.GOLD));
        }
    }
}
