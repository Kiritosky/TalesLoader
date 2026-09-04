package plugin.talesloader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import plugin.talesloader.net.LoaderInfoS2C;
import plugin.talesloader.net.MapDataS2C;

/** Client side reactions to server payloads. Only ever touched on the client. */
@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleLoaderInfo(LoaderInfoS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLoaderState.update(payload.pos(), payload.ownerName(), payload.blockedOwners(), payload.trusted());
            if (Minecraft.getInstance().screen instanceof TrustedScreen trustedScreen) {
                trustedScreen.refresh();
            }
        });
    }

    public static void handleMapData(MapDataS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen current = minecraft.screen;
            if (current instanceof ChunkMapScreen map) {
                map.setData(payload);
                return;
            }
            Screen parent = current instanceof ChunkLoaderScreen ? current : null;
            minecraft.setScreen(new ChunkMapScreen(payload, parent));
        });
    }
}
