package plugin.talesloader.client;

import net.minecraft.util.Mth;

import java.util.UUID;

/** Stable, well separated colour per player, derived from the UUID so every client agrees. */
public final class PlayerColors {
    private PlayerColors() {
    }

    public static int rgb(UUID id) {
        int hash = id.hashCode();
        float hue = (Math.floorMod(hash, 360)) / 360.0F;
        float saturation = 0.55F + (Math.floorMod(hash >> 9, 3)) * 0.15F;
        float value = 0.80F + (Math.floorMod(hash >> 17, 2)) * 0.15F;
        return Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    public static int argb(UUID id, int alpha) {
        return (alpha << 24) | rgb(id);
    }
}
