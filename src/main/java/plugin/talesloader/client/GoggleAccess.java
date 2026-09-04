package plugin.talesloader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import plugin.talesloader.block.ChunkLoaderBlockEntity;

/**
 * Client side lookup for the goggle overlay. Lives here so the block entity keeps its common code
 * free of client classes; it is only ever touched from {@code addToGoggleTooltip}.
 */
@OnlyIn(Dist.CLIENT)
public final class GoggleAccess {
    private GoggleAccess() {
    }

    /** Whether the player looking at the loader may see owner and access details. */
    public static boolean maySeeDetails(ChunkLoaderBlockEntity loader) {
        Player player = Minecraft.getInstance().player;
        return player != null && loader.canUse(player);
    }
}
