package plugin.talesloader.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import plugin.talesloader.Talesloader;
import plugin.talesloader.net.RequestMapC2S;

@EventBusSubscriber(modid = Talesloader.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {
    private ClientInputHandler() {
    }

    @SubscribeEvent
    static void onClientTick(final ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pressed = false;
        while (ModKeyMappings.OPEN_MAP.consumeClick()) {
            pressed = true;
        }
        if (pressed && minecraft.player != null && minecraft.screen == null) {
            PacketDistributor.sendToServer(RequestMapC2S.INSTANCE);
        }
    }
}
