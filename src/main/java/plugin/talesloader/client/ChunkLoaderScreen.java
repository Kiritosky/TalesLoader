package plugin.talesloader.client;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.TooltipArea;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import plugin.talesloader.block.ChunkLoaderBlockEntity;
import plugin.talesloader.fuel.FuelValues;
import plugin.talesloader.menu.ChunkLoaderMenu;
import plugin.talesloader.net.RequestMapC2S;
import plugin.talesloader.net.ToggleChunkC2S;
import plugin.talesloader.registry.ModBlocks;
import plugin.talesloader.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ChunkLoaderScreen extends AbstractSimiContainerScreen<ChunkLoaderMenu> {
    private static final int TINT_ACTIVE = 0x5544DD55;
    private static final int TINT_CENTER = 0x5533AAFF;
    private static final int TINT_BLOCKED = 0x66DD3333;
    private static final int TINT_FREE = 0x66000000;
    private static final int LINE = 0xFF3A2A1C;
    private static final int BORDER_ACTIVE = 0xFFF4C86A;
    private static final int BAR_FILL = 0xFFCB9E59;
    private static final int BAR_FILL_HIGHLIGHT = 0xFFF4C86A;
    private static final int BAR_FILL_LOW = 0xFFA33F3F;
    private static final int BAR_FILL_LOW_HIGHLIGHT = 0xFFD46A6A;
    /** Below this the gauge and the indicator switch to the warning colour. */
    private static final long LOW_FUEL_TICKS = 20L * 60L;

    private IconButton mapButton;
    private IconButton trustedButton;
    private Indicator statusIndicator;
    private Label clockLabel;
    private TooltipArea gaugeTooltip;

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        setWindowSize(ChunkLoaderMenu.IMAGE_WIDTH, ChunkLoaderMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        mapButton = new IconButton(leftPos + ChunkLoaderMenu.PANEL_X, topPos + ChunkLoaderMenu.BUTTON_Y,
                AllIcons.I_SCHEMATIC);
        mapButton.withCallback(() -> PacketDistributor.sendToServer(RequestMapC2S.INSTANCE));
        mapButton.setToolTip(Component.translatable("gui.talesloader.map"));

        trustedButton = new IconButton(leftPos + ChunkLoaderMenu.PANEL_X + ChunkLoaderMenu.BUTTON_SIZE + 4,
                topPos + ChunkLoaderMenu.BUTTON_Y, AllIcons.I_WHITELIST);
        trustedButton.withCallback(() -> minecraft.setScreen(new TrustedScreen(menu.getPos(), this)));
        trustedButton.setToolTip(Component.translatable("gui.talesloader.trusted"));

        statusIndicator = new Indicator(leftPos + ChunkLoaderMenu.PANEL_X, topPos + ChunkLoaderMenu.INDICATOR_Y,
                Component.empty());

        clockLabel = new Label(leftPos + ChunkLoaderMenu.PANEL_X, topPos + 30, Component.empty())
                .colored(0x4A3520)
                .withShadow();

        gaugeTooltip = new TooltipArea(leftPos + ChunkLoaderMenu.PANEL_X, topPos + ChunkLoaderMenu.INDICATOR_Y,
                ChunkLoaderMenu.BAR_WIDTH, ChunkLoaderMenu.BAR_Y - ChunkLoaderMenu.INDICATOR_Y
                + ChunkLoaderMenu.BAR_HEIGHT);

        addRenderableWidgets(mapButton, trustedButton, statusIndicator, clockLabel, gaugeTooltip);
        updateState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateState();
    }

    /** Pushes the menu state into the Create widgets; they only render what they were handed. */
    private void updateState() {
        long remaining = menu.getRemainingTicks();
        clockLabel.text = Component.literal(TimeFormat.ticksToClock(remaining));
        statusIndicator.state = menu.getFuel() <= 0L ? Indicator.State.RED
                : remaining < LOW_FUEL_TICKS ? Indicator.State.YELLOW
                        : Indicator.State.GREEN;
        gaugeTooltip.withTooltip(fuelTooltip());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        CreateStyle.window(graphics, leftPos, topPos, imageWidth, ChunkLoaderMenu.PANEL_HEIGHT);
        renderPlayerInventory(graphics, getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.getWidth()),
                topPos + ChunkLoaderMenu.INVENTORY_TEXTURE_Y);

        renderChunkGrid(graphics, mouseX, mouseY);
        renderFuelGauge(graphics);
        renderPanelText(graphics);

        Slot fuelSlot = menu.slots.get(0);
        CreateStyle.slot(graphics, leftPos + fuelSlot.x, topPos + fuelSlot.y);

        // Loader block sitting on the top edge of the window, clear of the panel text below it.
        // Skipped when the window is pushed against the screen edge, where it would have nowhere to go.
        if (topPos >= 36) {
            GuiGameElement.of(ModBlocks.CHUNK_LOADER.get())
                    .<GuiGameElement.GuiRenderBuilder>at(leftPos + imageWidth - 44, topPos - 32, -200)
                    .rotateBlock(22, 45, 0)
                    .scale(2.2)
                    .render(graphics);
        }
    }

    private void renderChunkGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int originX = leftPos + ChunkLoaderMenu.GRID_X;
        int originY = topPos + ChunkLoaderMenu.GRID_Y;
        int span = 3 * ChunkLoaderMenu.CELL_SIZE;

        CreateStyle.field(graphics, leftPos + ChunkLoaderMenu.FIELD_X, topPos + ChunkLoaderMenu.FIELD_Y,
                ChunkLoaderMenu.FIELD_SIZE, ChunkLoaderMenu.FIELD_SIZE);

        ChunkPos center = new ChunkPos(menu.getPos());
        ChunkMinimap.draw(graphics, originX, originY, span, span, center.x, center.z, 1);

        int mask = menu.getSelectionMask();
        int hovered = cellAt(mouseX, mouseY);
        for (int index = 0; index < ChunkLoaderBlockEntity.CHUNK_COUNT; index++) {
            int x = originX + (index % 3) * ChunkLoaderMenu.CELL_SIZE;
            int y = originY + (index / 3) * ChunkLoaderMenu.CELL_SIZE;
            int size = ChunkLoaderMenu.CELL_SIZE;

            // Vertical gradient gives the cells the same depth Create's own panels have.
            graphics.fillGradient(x, y, x + size, y + size, tintFor(index, mask), shadeOf(tintFor(index, mask)));
            if (index == hovered) {
                // Create's own breathing highlight, so hovering feels like the rest of the mod.
                graphics.fill(x, y, x + size, y + size, hoverTint());
            }
            graphics.renderOutline(x, y, size, size, (mask & (1 << index)) != 0 ? BORDER_ACTIVE : LINE);
        }

        int markerX = originX + ChunkLoaderMenu.CELL_SIZE + ChunkLoaderMenu.CELL_SIZE / 2;
        int markerY = originY + ChunkLoaderMenu.CELL_SIZE + ChunkLoaderMenu.CELL_SIZE / 2;
        GuiGameElement.of(ModBlocks.CHUNK_LOADER.get())
                .<GuiGameElement.GuiRenderBuilder>at(markerX - 8, markerY - 8, 100)
                .rotateBlock(30, 45, 0)
                .scale(0.9)
                .render(graphics);
    }

    /** Pulsing white overlay, in sync with Create's UI animations. */
    private int hoverTint() {
        float breath = (float) ((Math.sin(net.createmod.catnip.animation.AnimationTickHolder.getRenderTime() / 8.0F)
                + 1.0F) / 2.0F);
        int alpha = 0x18 + (int) (breath * 0x28);
        return (alpha << 24) | 0xFFFFFF;
    }

    /** Darker variant of a tint, used as the bottom stop of a cell gradient. */
    private static int shadeOf(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (red * 3 / 5 << 16) | (green * 3 / 5 << 8) | (blue * 3 / 5);
    }

    private int tintFor(int index, int mask) {
        if ((mask & (1 << index)) != 0) {
            return index == ChunkLoaderBlockEntity.CENTER_INDEX ? TINT_CENTER : TINT_ACTIVE;
        }
        return isBlocked(index) ? TINT_BLOCKED : TINT_FREE;
    }

    private boolean isBlocked(int index) {
        return ClientLoaderState.matches(menu.getPos()) && !ClientLoaderState.blockedBy(index).isEmpty();
    }

    private void renderFuelGauge(GuiGraphics graphics) {
        boolean low = menu.getRemainingTicks() < LOW_FUEL_TICKS;
        float progress = (float) ((double) menu.getFuel() / menu.getMaxFuel());
        CreateStyle.gauge(graphics, leftPos + ChunkLoaderMenu.PANEL_X, topPos + ChunkLoaderMenu.BAR_Y,
                ChunkLoaderMenu.BAR_WIDTH, ChunkLoaderMenu.BAR_HEIGHT, progress,
                low ? BAR_FILL_LOW : BAR_FILL, low ? BAR_FILL_LOW_HIGHLIGHT : BAR_FILL_HIGHLIGHT);
    }

    private void renderPanelText(GuiGraphics graphics) {
        int x = leftPos + ChunkLoaderMenu.PANEL_X;
        int titleRoom = ChunkLoaderMenu.PANEL_X - 14;
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), titleRoom),
                leftPos + 10, topPos + 8, CreateStyle.TEXT, false);
        drawPanelLine(graphics, Component.translatable("gui.talesloader.remaining"), x, topPos + 18,
                CreateStyle.TEXT);
        drawPanelLine(graphics, Component.translatable("gui.talesloader.chunks",
                menu.getActiveChunkCount(), ChunkLoaderBlockEntity.CHUNK_COUNT), x, topPos + 70,
                CreateStyle.TEXT);
        drawPanelLine(graphics, Component.translatable("gui.talesloader.rate", menu.getConsumptionRate()),
                x, topPos + 82, CreateStyle.TEXT_DIM);

        String owner = ClientLoaderState.matches(menu.getPos()) ? ClientLoaderState.ownerName() : "";
        if (!owner.isEmpty()) {
            drawPanelLine(graphics, Component.translatable("gui.talesloader.owner", owner),
                    x, topPos + 96, CreateStyle.TEXT_DIM);
        }

        // Compass hint: north is up. Drawn onto the map itself so it cannot collide with the title.
        int compassX = leftPos + ChunkLoaderMenu.GRID_X + 3 * ChunkLoaderMenu.CELL_SIZE / 2 - font.width("N") / 2;
        graphics.drawString(font, "N", compassX, topPos + ChunkLoaderMenu.GRID_Y + 3,
                CreateStyle.TEXT_ON_DARK, true);
    }

    /** Draws one line of the side panel, trimmed so it can never run past the brass frame. */
    private void drawPanelLine(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), ChunkLoaderMenu.PANEL_WIDTH),
                x, y, color, false);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
        renderGridTooltip(graphics, mouseX, mouseY);
        renderFuelSlotTooltip(graphics, mouseX, mouseY);
    }

    private void renderGridTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = cellAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        ChunkPos center = new ChunkPos(menu.getPos());
        ChunkPos chunk = ChunkLoaderBlockEntity.chunkAt(center, index);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.talesloader.map_chunk", chunk.x, chunk.z));
        if (index == ChunkLoaderBlockEntity.CENTER_INDEX) {
            lines.add(Component.translatable("gui.talesloader.cell.center").withStyle(ChatFormatting.AQUA));
        } else if ((menu.getSelectionMask() & (1 << index)) != 0) {
            lines.add(Component.translatable("gui.talesloader.cell.active").withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.talesloader.cell.toggle_off").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isBlocked(index)) {
            lines.add(Component.translatable("gui.talesloader.cell.blocked", ClientLoaderState.blockedBy(index))
                    .withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("gui.talesloader.cell.free").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.talesloader.cell.toggle_on").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** The gauge tooltip is handled by {@link TooltipArea}; the empty fuel slot needs its own check. */
    private void renderFuelSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int slotX = leftPos + ChunkLoaderMenu.FUEL_SLOT_X;
        int slotY = topPos + ChunkLoaderMenu.FUEL_SLOT_Y;
        boolean overEmptySlot = !menu.slots.get(0).hasItem() && menu.getCarried().isEmpty()
                && mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16;
        if (!overEmptySlot) {
            return;
        }
        graphics.renderComponentTooltip(font, fuelTooltip(), mouseX, mouseY);
    }

    private List<Component> fuelTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.talesloader.fuel_slot"));
        if (menu.isFull()) {
            lines.add(Component.translatable("gui.talesloader.tank_full").withStyle(ChatFormatting.RED));
        }
        int rate = Math.max(1, menu.getConsumptionRate());
        for (Map.Entry<Item, Long> entry : FuelValues.all().entrySet()) {
            lines.add(Component.literal(" ")
                    .append(entry.getKey().getDescription().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" - " + TimeFormat.ticksToShort(entry.getValue() / rate))
                            .withStyle(ChatFormatting.GOLD)));
        }
        lines.add(Component.translatable("gui.talesloader.no_automation").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellAt((int) mouseX, (int) mouseY);
        if (index >= 0) {
            if (index == ChunkLoaderBlockEntity.CENTER_INDEX || isBlocked(index)) {
                playUiSound(AllSoundEvents.DENY.getMainEvent(), 0.4F, 1.0F);
            } else {
                PacketDistributor.sendToServer(new ToggleChunkC2S(menu.getPos(), index));
                playUiSound(AllSoundEvents.CONTROLLER_CLICK.getMainEvent(), 0.4F,
                        (menu.getSelectionMask() & (1 << index)) != 0 ? 0.8F : 1.2F);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Index of the 3x3 cell under the cursor, or -1. */
    private int cellAt(int mouseX, int mouseY) {
        int originX = leftPos + ChunkLoaderMenu.GRID_X;
        int originY = topPos + ChunkLoaderMenu.GRID_Y;
        int span = 3 * ChunkLoaderMenu.CELL_SIZE;
        if (mouseX < originX || mouseX >= originX + span || mouseY < originY || mouseY >= originY + span) {
            return -1;
        }
        int col = (mouseX - originX) / ChunkLoaderMenu.CELL_SIZE;
        int row = (mouseY - originY) / ChunkLoaderMenu.CELL_SIZE;
        return row * 3 + col;
    }
}
