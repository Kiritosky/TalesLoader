package plugin.talesloader;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Common config for the chunk loader. Fuel is measured in abstract "fuel units".
 * Consumption per server tick is {@code baseRate + perChunkRate * activeChunks},
 * so one unit of fuel is worth {@code 1 / (baseRate + perChunkRate)} ticks with a single active chunk.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 48 hours of runtime with a single active chunk at the default rates. */
    public static final long DEFAULT_MAX_FUEL = 48L * 3600L * 20L * 15L;

    public static final ModConfigSpec.IntValue BASE_RATE = BUILDER
            .comment("Fuel units consumed per server tick, regardless of how many chunks are active.")
            .defineInRange("consumption.baseRate", 10, 0, 10_000);

    public static final ModConfigSpec.IntValue PER_CHUNK_RATE = BUILDER
            .comment("Additional fuel units consumed per server tick for every active chunk (the loader's own chunk counts).")
            .defineInRange("consumption.perChunkRate", 5, 0, 10_000);

    public static final ModConfigSpec.LongValue MAX_FUEL = BUILDER
            .comment("Maximum fuel a single chunk loader can hold.",
                    "Default equals 48 hours of runtime with one active chunk.",
                    "Capped at 2^30-1 because the GUI transports the value in two 15 bit container slots.")
            .defineInRange("consumption.maxFuel", DEFAULT_MAX_FUEL, 1L, 1_073_741_823L);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FUEL_ITEMS = BUILDER
            .comment("Accepted fuel items, formatted as 'namespace:path=units'.",
                    "Reference: 15 units = 1 tick with one active chunk, 1080000 units = 1 hour.")
            .defineListAllowEmpty("fuel.items", List.of(
                    "minecraft:coal=360000",          // 20 min
                    "minecraft:charcoal=360000",      // 20 min
                    "minecraft:redstone_block=2160000",  // 2 h
                    "minecraft:coal_block=3240000",   // 3 h
                    "minecraft:diamond=4320000",      // 4 h
                    "minecraft:diamond_block=38880000", // 36 h
                    "minecraft:nether_star=51840000"  // 48 h
            ), Config::isValidFuelEntry);

    public static final ModConfigSpec.IntValue MAP_RADIUS = BUILDER
            .comment("Radius in chunks shown by the chunk map (4 = 9x9 chunks).")
            .defineInRange("gui.mapRadius", 4, 1, 16);

    public static final ModConfigSpec.BooleanValue OPS_BYPASS_OWNER = BUILDER
            .comment("Allow operators (permission level 2) to use and break chunk loaders they do not own.")
            .define("permissions.opsBypassOwner", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    // The spec is a SERVER config, so it is synced to every client on login and both sides agree on the
    // numbers. Before that sync (main menu, early startup) the defaults are used instead of throwing.

    public static int baseRate() {
        return SPEC.isLoaded() ? BASE_RATE.get() : BASE_RATE.getDefault();
    }

    public static int perChunkRate() {
        return SPEC.isLoaded() ? PER_CHUNK_RATE.get() : PER_CHUNK_RATE.getDefault();
    }

    public static long maxFuel() {
        return SPEC.isLoaded() ? MAX_FUEL.get() : MAX_FUEL.getDefault();
    }

    public static int mapRadius() {
        return SPEC.isLoaded() ? MAP_RADIUS.get() : MAP_RADIUS.getDefault();
    }

    public static boolean opsBypassOwner() {
        return SPEC.isLoaded() ? OPS_BYPASS_OWNER.get() : OPS_BYPASS_OWNER.getDefault();
    }

    public static List<? extends String> fuelItems() {
        return SPEC.isLoaded() ? FUEL_ITEMS.get() : FUEL_ITEMS.getDefault();
    }

    private static boolean isValidFuelEntry(Object raw) {
        if (!(raw instanceof String entry)) {
            return false;
        }
        int split = entry.lastIndexOf('=');
        if (split <= 0 || split == entry.length() - 1) {
            return false;
        }
        try {
            return Long.parseLong(entry.substring(split + 1).trim()) > 0L;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
