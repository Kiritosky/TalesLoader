package plugin.talesloader.client;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ConfirmationScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import plugin.talesloader.net.TrustC2S;

import javax.annotation.Nullable;
import java.util.List;

/** Manages the players who may use a loader besides its owner. */
@OnlyIn(Dist.CLIENT)
public class TrustedScreen extends AbstractSimiScreen {
    private static final int PANEL_WIDTH = 180;
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 20;
    private static final int MAX_ROWS = 6;

    private final BlockPos loaderPos;
    @Nullable
    private final Screen parent;
    private EditBox nameBox;
    private int listY;

    public TrustedScreen(BlockPos loaderPos, @Nullable Screen parent) {
        super(Component.translatable("gui.talesloader.trusted_title"));
        this.loaderPos = loaderPos;
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<String> trusted = trusted();
        int rows = Math.min(trusted.size(), MAX_ROWS);
        // With nobody on the list the wrapped hint takes the place of the rows, and it may wrap
        // to more than one line depending on the language.
        int listHeight = rows > 0 ? rows * ROW_HEIGHT : hintLines().size() * 10 + 6;
        setWindowSize(PANEL_WIDTH, 26 + 18 + 10 + listHeight + 10 + 18 + MARGIN);
        super.init();

        int inner = PANEL_WIDTH - MARGIN * 2;
        int boxY = guiTop + 26;
        nameBox = new EditBox(font, guiLeft + MARGIN + 1, boxY + 1, inner - 24, 16,
                Component.translatable("gui.talesloader.player_name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.talesloader.player_name"));
        addRenderableWidget(nameBox);

        IconButton add = new IconButton(guiLeft + MARGIN + inner - 18, boxY, AllIcons.I_ADD);
        add.withCallback(this::submit);
        add.setToolTip(Component.translatable("gui.talesloader.add"));
        addRenderableWidget(add);

        listY = boxY + 28;
        for (int i = 0; i < rows; i++) {
            String name = trusted.get(i);
            IconButton remove = new IconButton(guiLeft + MARGIN + inner - 18, listY + i * ROW_HEIGHT,
                    AllIcons.I_TRASH);
            remove.withCallback(() -> confirmRemoval(name));
            remove.setToolTip(Component.translatable("gui.talesloader.remove", name));
            addRenderableWidget(remove);
        }

        IconButton done = new IconButton(guiLeft + PANEL_WIDTH - MARGIN - 18,
                guiTop + windowHeight - MARGIN - 18, AllIcons.I_CONFIRM);
        done.withCallback(this::onClose);
        done.setToolTip(Component.translatable("gui.done"));
        addRenderableWidget(done);
    }

    /** Removing someone's access is not undoable from here, so it goes through Create's prompt. */
    private void confirmRemoval(String name) {
        new ConfirmationScreen()
                .centered()
                .withText(Component.translatable("gui.talesloader.remove_confirm", name))
                .withAction(confirmed -> {
                    if (confirmed) {
                        PacketDistributor.sendToServer(new TrustC2S(loaderPos, name, false));
                    }
                })
                .open(this);
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) {
            PacketDistributor.sendToServer(new TrustC2S(loaderPos, name, true));
            nameBox.setValue("");
        }
    }

    private List<FormattedCharSequence> hintLines() {
        return font.split(Component.translatable("gui.talesloader.no_trusted"), PANEL_WIDTH - MARGIN * 2);
    }

    private List<String> trusted() {
        return ClientLoaderState.matches(loaderPos) ? ClientLoaderState.trusted() : List.of();
    }

    /** Called when fresh loader info arrives so the list stays current. */
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameBox.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Dim the world like vanilla does, without the extra blur pass. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        CreateStyle.window(graphics, guiLeft, guiTop, windowWidth, windowHeight);
        graphics.drawString(font, title, guiLeft + MARGIN, guiTop + 9, CreateStyle.TEXT, false);

        List<String> trusted = trusted();
        if (trusted.isEmpty()) {
            // Wrapped instead of trimmed - the sentence is longer than the panel in both languages.
            List<FormattedCharSequence> wrapped = hintLines();
            for (int i = 0; i < wrapped.size(); i++) {
                graphics.drawString(font, wrapped.get(i), guiLeft + MARGIN, listY + 4 + i * 10,
                        CreateStyle.TEXT_DIM, false);
            }
            return;
        }
        for (int i = 0; i < Math.min(trusted.size(), MAX_ROWS); i++) {
            int rowY = listY + i * ROW_HEIGHT;
            CreateStyle.field(graphics, guiLeft + MARGIN, rowY, PANEL_WIDTH - MARGIN * 2 - 22, 18);
            graphics.drawString(font, font.plainSubstrByWidth(trusted.get(i), PANEL_WIDTH - MARGIN * 2 - 34),
                    guiLeft + MARGIN + 6, rowY + 5, CreateStyle.TEXT_ON_DARK, false);
        }
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
