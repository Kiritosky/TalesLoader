package plugin.talesloader.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Create-flavoured drawing helpers. The window is assembled from Create's brass frame nine-slice
 * (the same one {@code ValueSettingsScreen} uses), so it works at any size without shipping a GUI
 * texture of our own.
 */
@OnlyIn(Dist.CLIENT)
public final class CreateStyle {
    /** Body colour of Create's player inventory texture, so both halves of the window match. */
    public static final int PANEL = 0xFFC6C6C6;
    public static final int PANEL_SHADE = 0xFFA8A8A8;
    /** Interior of Create's value settings panel - used for the chunk map field. */
    public static final int DARK_FIELD = 0xFF0E0000;
    public static final int BRASS = 0xFFCB9E59;
    public static final int BRASS_LIGHT = 0xFFF4C86A;
    public static final int BRASS_DARK = 0xFF9C6846;
    public static final int TEXT = 0x404040;
    public static final int TEXT_DIM = 0x707070;
    public static final int TEXT_ON_DARK = 0xDDDDDD;

    /** Both frame corners are 4x4, the edges 3px thick. */
    private static final int FRAME = 4;
    private static final int EDGE = 3;

    private CreateStyle() {
    }

    /** Filled Create window: grey body inside a brass frame. Coordinates are the outer bounds. */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + EDGE, y + EDGE, x + width - EDGE, y + height - EDGE, PANEL);
        frame(graphics, x, y, width, height);
    }

    /** Recessed dark field (chunk map, gauges) inside a brass frame. */
    public static void field(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + EDGE, y + EDGE, x + width - EDGE, y + height - EDGE, DARK_FIELD);
        frame(graphics, x, y, width, height);
    }

    /** Brass frame only - ported from {@code ValueSettingsScreen#renderBrassFrame}. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + width - FRAME, y);
        AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + height - FRAME);
        AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + width - FRAME, y + height - FRAME);

        if (height > 2 * FRAME) {
            UIRenderHelper.drawStretched(graphics, x, y + FRAME, EDGE, height - 2 * FRAME, 0,
                    AllGuiTextures.BRASS_FRAME_LEFT);
            UIRenderHelper.drawStretched(graphics, x + width - EDGE, y + FRAME, EDGE, height - 2 * FRAME, 0,
                    AllGuiTextures.BRASS_FRAME_RIGHT);
        }
        if (width > 2 * FRAME) {
            UIRenderHelper.drawCropped(graphics, x + FRAME, y, width - 2 * FRAME, EDGE, 0,
                    AllGuiTextures.BRASS_FRAME_TOP);
            UIRenderHelper.drawCropped(graphics, x + FRAME, y + height - EDGE, width - 2 * FRAME, EDGE, 0,
                    AllGuiTextures.BRASS_FRAME_BOTTOM);
        }
    }

    /** Create's 18x18 slot frame around a 16x16 slot at {@code (x, y)}. */
    public static void slot(GuiGraphics graphics, int x, int y) {
        AllGuiTextures.JEI_SLOT.render(graphics, x - 1, y - 1);
    }

    /**
     * Horizontal gauge in Create's brass palette. {@code progress} is clamped to [0, 1].
     */
    public static void gauge(GuiGraphics graphics, int x, int y, int width, int height,
                             float progress, int fill, int fillHighlight) {
        graphics.fill(x, y, x + width, y + height, DARK_FIELD);
        graphics.fill(x, y, x + width, y + 1, BRASS_DARK);
        graphics.fill(x, y + height - 1, x + width, y + height, BRASS_DARK);
        graphics.fill(x, y, x + 1, y + height, BRASS_DARK);
        graphics.fill(x + width - 1, y, x + width, y + height, BRASS_DARK);

        int inner = width - 2;
        int filled = Math.round(inner * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled <= 0) {
            return;
        }
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fill);
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, fillHighlight);
    }
}
