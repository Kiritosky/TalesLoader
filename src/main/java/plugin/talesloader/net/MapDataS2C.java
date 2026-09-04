package plugin.talesloader.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import plugin.talesloader.Talesloader;

import java.util.List;
import java.util.UUID;

/** Force-loaded chunks around a centre point, drawn by the chunk map screen. */
public record MapDataS2C(int centerX, int centerZ, int radius, List<Claim> claims) implements CustomPacketPayload {
    public static final Type<MapDataS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "map_data"));

    private static final StreamCodec<ByteBuf, List<Claim>> CLAIM_LIST =
            Claim.STREAM_CODEC.apply(ByteBufCodecs.list(2048));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapDataS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapDataS2C::centerX,
            ByteBufCodecs.VAR_INT, MapDataS2C::centerZ,
            ByteBufCodecs.VAR_INT, MapDataS2C::radius,
            CLAIM_LIST, MapDataS2C::claims,
            MapDataS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Claim(int chunkX, int chunkZ, UUID ownerId, String ownerName, boolean active) {
        public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Claim::chunkX,
                ByteBufCodecs.VAR_INT, Claim::chunkZ,
                UUIDUtil.STREAM_CODEC, Claim::ownerId,
                ByteBufCodecs.stringUtf8(32), Claim::ownerName,
                ByteBufCodecs.BOOL, Claim::active,
                Claim::new);
    }
}
