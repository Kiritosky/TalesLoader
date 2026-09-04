package plugin.talesloader.client;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import plugin.talesloader.net.MapDataS2C;
import plugin.talesloader.net.RequestMapC2S;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Map of the force loaded chunks around a point, drawn on top of the actual terrain so buildings are
 * recognisable. One colour per player; solid means running, faded means reserved but out of fuel.
 */
@OnlyIn(Dist.CLIENT)
public class ChunkMapScreen extends AbstractSimiScreen {
    /** Pixels per chunk. The middle entry is the default: one block per pixel. */
    private static final int[] ZOOM_LEVELS = {8, 12, 16, 24};
    private static final int DEFAULT_ZOOM = 2;
    private static final int MARGIN = 10;
    /** Title line plus the centre coordinates line above the map. */
    private static final int HEADER_HEIGHT = 36;
    private static final int LEGEND_ROWS = 3;
    private static final int VIEWER_MARKER = 0xFFFFFFFF;
    private static final int LOADED_ALPHA = 0x66;
    private static final int IDLE_ALPHA = 0x33;

    @Nullable
    private final Screen parent;
    private MapDataS2C data;
    private int zoom = DEFAULT_ZOOM;
    private int mapX;
    private int mapY;

    public ChunkMapScreen(MapDataS2C data, @Nullable Screen parent) {
        super(Component.translatable("gui.talesloader.map_title"));
        this.data = data;
        this.parent = parent;
    }

    public void setData(MapDataS2C data) {
        this.data = data;
        ChunkMinimap.invalidate();
    }

    private String centerText() {
        return Component.translatable("gui.talesloader.map_center", data.centerX(), data.centerZ()).getString();
    }

    private int cell() {
        return ZOOM_LEVELS[zoom];
    }

    private int gridSize() {
        return data.radius() * 2 + 1;
    }

    @Override
    protected void init() {
        int mapSpan = gridSize() * cell();
        // Title and centre coordinates each get their own line - side by side they collide as soon
        // as the map is small or the coordinates are long.
        int textWidth = MARGIN * 2 + Math.max(font.width(title), font.width(centerText()));
        setWindowSize(Math.max(mapSpan + MARGIN * 2, textWidth),
                HEADER_HEIGHT + mapSpan + 8 + LEGEND_ROWS * 11 + 8 + 18 + MARGIN);
        super.init();

        mapX = guiLeft + (windowWidth - mapSpan) / 2;
        mapY = guiTop + HEADER_HEIGHT;

        int buttonY = guiTop + windowHeight - MARGIN - 18;
        IconButton refresh = new IconButton(guiLeft + windowWidth - MARGIN - 40, buttonY, AllIcons.I_REFRESH);
        refresh.withCallback(() -> PacketDistributor.sendToServer(RequestMapC2S.INSTANCE));
        refresh.setToolTip(Component.translatable("gui.talesloader.refresh"));

        IconButton done = new IconButton(guiLeft + windowWidth - MARGIN - 18, buttonY, AllIcons.I_CONFIRM);
        done.withCallback(this::onClose);
        done.setToolTip(Component.translatable("gui.done"));

        addRenderableWidgets(refresh, done);
    }

    /** Dim the world like vanilla does, but without the blur pass a container screen would add. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        CreateStyle.window(graphics, guiLeft, guiTop, windowWidth, windowHeight);

        graphics.drawString(font, title, guiLeft + MARGIN, guiTop + 8, CreateStyle.TEXT, false);
        graphics.drawString(font, centerText(), guiLeft + MARGIN, guiTop + 20, CreateStyle.TEXT_DIM, false);

        renderMap(graphics);
        renderLegend(graphics, guiLeft + MARGIN, mapY + gridSize() * cell() + 8);
    }

    private void renderMap(GuiGraphics graphics) {
        int size = gridSize();
        int span = size * cell();
        CreateStyle.field(graphics, mapX - 4, mapY - 4, span + 8, span + 8);
        ChunkMinimap.draw(graphics, mapX, mapY, span, span, data.centerX(), data.centerZ(), data.radius());

        ChunkPos viewer = minecraft != null && minecraft.player != null
                ? minecraft.player.chunkPosition()
                : new ChunkPos(0, 0);

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int chunkX = data.centerX() - data.radius() + col;
                int chunkZ = data.centerZ() - data.radius() + row;
                int x = mapX + col * cell();
                int y = mapY + row * cell();

                MapDataS2C.Claim claim = claimAt(chunkX, chunkZ);
                if (claim != null) {
                    graphics.fill(x, y, x + cell(), y + cell(),
                            PlayerColors.argb(claim.ownerId(), claim.active() ? LOADED_ALPHA : IDLE_ALPHA));
                    graphics.renderOutline(x, y, cell(), cell(), PlayerColors.argb(claim.ownerId(), 0xFF));
                } else {
                    graphics.fill(x, y, x + cell(), y + 1, 0x33000000);
                    graphics.fill(x, y, x + 1, y + cell(), 0x33000000);
                }

                if (viewer.x == chunkX && viewer.z == chunkZ) {
                    graphics.renderOutline(x, y, cell(), cell(), VIEWER_MARKER);
                }
            }
        }
    }

    private void renderLegend(GuiGraphics graphics, int x, int y) {
        Map<UUID, String> owners = new LinkedHashMap<>();
        for (MapDataS2C.Claim claim : data.claims()) {
            owners.putIfAbsent(claim.ownerId(), claim.ownerName());
        }
        if (owners.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.talesloader.map_empty"), x, y,
                    CreateStyle.TEXT_DIM, false);
            return;
        }
        int row = 0;
        for (Map.Entry<UUID, String> owner : owners.entrySet()) {
            if (row >= LEGEND_ROWS) {
                graphics.drawString(font, Component.translatable("gui.talesloader.map_more", owners.size() - row),
                        x, y + row * 11, CreateStyle.TEXT_DIM, false);
                break;
            }
            int lineY = y + row * 11;
            graphics.fill(x, lineY, x + 8, lineY + 8, PlayerColors.argb(owner.getKey(), 0xFF));
            graphics.renderOutline(x, lineY, 8, 8, CreateStyle.BRASS_DARK);
            graphics.drawString(font, owner.getValue(), x + 12, lineY, CreateStyle.TEXT, false);
            row++;
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int span = gridSize() * cell();
        if (mouseX < mapX || mouseX >= mapX + span || mouseY < mapY || mouseY >= mapY + span) {
            return;
        }
        int chunkX = data.centerX() - data.radius() + (mouseX - mapX) / cell();
        int chunkZ = data.centerZ() - data.radius() + (mouseY - mapY) / cell();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.talesloader.map_chunk", chunkX, chunkZ));
        lines.add(Component.translatable("gui.talesloader.map_blocks", chunkX * 16, chunkZ * 16)
                .withStyle(ChatFormatting.DARK_GRAY));
        MapDataS2C.Claim claim = claimAt(chunkX, chunkZ);
        if (claim == null) {
            lines.add(Component.translatable("gui.talesloader.map_free").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.talesloader.map_owner", claim.ownerName()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(claim.active() ? "gui.talesloader.map_active" : "gui.talesloader.map_idle")
                    .withStyle(claim.active() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Scrolling over the map zooms, the way Create handles scroll input everywhere else. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int span = gridSize() * cell();
        if (mouseX >= mapX && mouseX < mapX + span && mouseY >= mapY && mouseY < mapY + span) {
            int next = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoom + (int) Math.signum(scrollY)));
            if (next != zoom) {
                zoom = next;
                // The minimap texture is resolution independent, only the blit size changes.
                rebuildWidgets();
                playUiSound(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 0.25F, 1.0F + zoom * 0.1F);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void playUiSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    @Nullable
    private MapDataS2C.Claim claimAt(int chunkX, int chunkZ) {
        for (MapDataS2C.Claim claim : data.claims()) {
            if (claim.chunkX() == chunkX && claim.chunkZ() == chunkZ) {
                return claim;
            }
        }
        return null;
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
