package plugin.talesloader.client.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import plugin.talesloader.block.ChunkLoaderBlock;

/**
 * Ponder scenes for the chunk loader. Both run on {@code assets/talesloader/ponder/chunk_loader.nbt},
 * a 5x5 plate with the loader in the middle.
 */
@OnlyIn(Dist.CLIENT)
public final class TalesLoaderPonderScenes {
    private static final BlockPos LOADER = new BlockPos(2, 1, 2);

    private TalesLoaderPonderScenes() {
    }

    public static void chunkLoader(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chunk_loader", "Keeping chunks loaded");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().position(LOADER), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The Chunk Loader keeps the area around it loaded, even with no player nearby")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LOADER))
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(LOADER), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(70)
                .text("Right-click it to open its interface")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LOADER))
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("Its 3x3 grid shows the surrounding chunks - each one can be switched on separately")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(LOADER))
                .attachKeyFrame();
        scene.idle(90);

        scene.world().modifyBlock(LOADER, state -> state.setValue(ChunkLoaderBlock.ACTIVE, true), true);
        scene.overlay().showText(70)
                .text("A running loader glows. Chunks already claimed by someone else stay locked")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LOADER))
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    public static void fuel(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chunk_loader_fuel", "Fuelling the Chunk Loader");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().position(LOADER), Direction.DOWN);
        scene.world().modifyBlock(LOADER, state -> state.setValue(ChunkLoaderBlock.ACTIVE, true), false);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(LOADER), Pointing.DOWN, 40)
                .withItem(new ItemStack(Items.COAL))
                .rightClick();
        scene.overlay().showText(70)
                .text("Fuel goes into the slot in the interface - by hand only")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LOADER))
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(80)
                .colored(PonderPalette.RED)
                .text("Hoppers, droppers and pipes cannot insert anything")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(LOADER, Direction.WEST))
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Every active chunk raises the consumption, so runtime drops the more chunks are loaded")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(LOADER))
                .attachKeyFrame();
        scene.idle(90);

        scene.world().modifyBlock(LOADER, state -> state.setValue(ChunkLoaderBlock.ACTIVE, false), true);
        scene.overlay().showText(70)
                .colored(PonderPalette.RED)
                .text("Out of fuel the loader shuts down and releases its chunks - the owner gets a message")
                .placeNearTarget()
                .pointAt(util.vector().topOf(LOADER))
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }
}
