package plugin.talesloader.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import plugin.talesloader.Talesloader;

import javax.annotation.Nullable;

/**
 * Renders the terrain around a point the way a filled map does: one pixel per block, coloured by the
 * block's map colour and shaded by the height difference to the north, so buildings and terrain edges
 * stand out. Built from the chunks the client already has - nothing is requested from the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ChunkMinimap {
    private static final int PIXELS_PER_CHUNK = 16;
    private static final int SEARCH_DEPTH = 32;
    private static final long REBUILD_INTERVAL_MS = 2000L;
    /** ABGR, matching {@link NativeImage#setPixelRGBA}. */
    private static final int COLOR_UNKNOWN = 0xFF2C2C2C;

    @Nullable
    private static DynamicTexture texture;
    @Nullable
    private static ResourceLocation textureId;
    private static int span;
    private static int builtCenterX = Integer.MIN_VALUE;
    private static int builtCenterZ = Integer.MIN_VALUE;
    private static int builtRadius = -1;
    private static long builtAt;
    /** Reused across rebuilds so a refresh allocates nothing. */
    private static int[] heights = new int[0];
    private static MapColor[] colors = new MapColor[0];

    private ChunkMinimap() {
    }

    /** Draws the map scaled into the given rectangle. Use whole multiples of the pixel size for crisp output. */
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height,
                            int centerChunkX, int centerChunkZ, int radius) {
        ResourceLocation id = prepare(centerChunkX, centerChunkZ, radius);
        if (id == null) {
            graphics.fill(x, y, x + width, y + height, 0xFF2C2C2C);
            return;
        }
        graphics.blit(id, x, y, width, height, 0.0F, 0.0F, span, span, span, span);
    }

    /** Forces a rebuild on the next draw, e.g. after the map centre was moved by the server. */
    public static void invalidate() {
        builtRadius = -1;
    }

    @Nullable
    private static ResourceLocation prepare(int centerChunkX, int centerChunkZ, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        int size = (radius * 2 + 1) * PIXELS_PER_CHUNK;
        if (texture == null || span != size) {
            if (texture != null && textureId != null) {
                minecraft.getTextureManager().release(textureId);
                texture.close();
            }
            span = size;
            texture = new DynamicTexture(size, size, false);
            textureId = minecraft.getTextureManager().register("talesloader_minimap", texture);
            builtRadius = -1;
        }

        long now = Util.getMillis();
        if (builtCenterX != centerChunkX || builtCenterZ != centerChunkZ || builtRadius != radius
                || now - builtAt > REBUILD_INTERVAL_MS) {
            build(level, centerChunkX, centerChunkZ, radius);
            builtCenterX = centerChunkX;
            builtCenterZ = centerChunkZ;
            builtRadius = radius;
            builtAt = now;
        }
        return textureId;
    }

    private static void build(ClientLevel level, int centerChunkX, int centerChunkZ, int radius) {
        DynamicTexture target = texture;
        if (target == null) {
            return;
        }
        NativeImage image = target.getPixels();
        if (image == null) {
            return;
        }

        long startedAt = Util.getNanos();
        int chunks = radius * 2 + 1;
        if (heights.length != span * span) {
            heights = new int[span * span];
            colors = new MapColor[span * span];
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();

        for (int chunkZ = 0; chunkZ < chunks; chunkZ++) {
            for (int chunkX = 0; chunkX < chunks; chunkX++) {
                ChunkAccess chunk = level.getChunk(centerChunkX - radius + chunkX, centerChunkZ - radius + chunkZ,
                        ChunkStatus.FULL, false);
                int originX = (centerChunkX - radius + chunkX) << 4;
                int originZ = (centerChunkZ - radius + chunkZ) << 4;

                for (int blockZ = 0; blockZ < PIXELS_PER_CHUNK; blockZ++) {
                    for (int blockX = 0; blockX < PIXELS_PER_CHUNK; blockX++) {
                        int index = (chunkZ * PIXELS_PER_CHUNK + blockZ) * span + chunkX * PIXELS_PER_CHUNK + blockX;
                        if (chunk == null) {
                            heights[index] = Integer.MIN_VALUE;
                            colors[index] = null;
                            continue;
                        }
                        // WORLD_SURFACE points at the first free space, so start one below.
                        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
                        MapColor found = MapColor.NONE;
                        int y = surface;
                        while (y > minY && surface - y < SEARCH_DEPTH) {
                            cursor.set(originX + blockX, y, originZ + blockZ);
                            BlockState state = chunk.getBlockState(cursor);
                            found = state.getMapColor(level, cursor);
                            if (found != MapColor.NONE) {
                                break;
                            }
                            y--;
                        }
                        heights[index] = y;
                        colors[index] = found == MapColor.NONE ? null : found;
                    }
                }
            }
        }

        for (int pixelZ = 0; pixelZ < span; pixelZ++) {
            for (int pixelX = 0; pixelX < span; pixelX++) {
                int index = pixelZ * span + pixelX;
                MapColor color = colors[index];
                if (color == null) {
                    image.setPixelRGBA(pixelX, pixelZ, COLOR_UNKNOWN);
                    continue;
                }
                int north = pixelZ > 0 && heights[index - span] != Integer.MIN_VALUE
                        ? heights[index - span]
                        : heights[index];
                MapColor.Brightness brightness = heights[index] > north
                        ? MapColor.Brightness.HIGH
                        : heights[index] < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                image.setPixelRGBA(pixelX, pixelZ, color.calculateRGBColor(brightness));
            }
        }

        target.upload();

        long elapsedMs = (Util.getNanos() - startedAt) / 1_000_000L;
        if (elapsedMs > 5L) {
            Talesloader.LOGGER.debug("Minimap rebuild for {} chunks took {} ms", chunks * chunks, elapsedMs);
        }
    }
}
