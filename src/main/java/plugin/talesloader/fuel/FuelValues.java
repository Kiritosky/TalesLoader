package plugin.talesloader.fuel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import plugin.talesloader.Config;
import plugin.talesloader.Talesloader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fuel item lookup, backed by {@link Config#FUEL_ITEMS}. Rebuilt whenever the config is (re)loaded.
 */
@EventBusSubscriber(modid = Talesloader.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FuelValues {
    /** Written on the config thread, read on the server and render threads. */
    private static volatile Map<Item, Long> values = Map.of();

    private FuelValues() {
    }

    @SubscribeEvent
    static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        values = parse(Config.fuelItems());
    }

    private static Map<Item, Long> parse(java.util.List<? extends String> entries) {
        Map<Item, Long> parsed = new LinkedHashMap<>();
        for (String entry : entries) {
            int split = entry.lastIndexOf('=');
            if (split <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, split).trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            try {
                long amount = Long.parseLong(entry.substring(split + 1).trim());
                if (amount > 0L) {
                    parsed.put(BuiltInRegistries.ITEM.get(id), amount);
                }
            } catch (NumberFormatException ignored) {
                // validated by the config spec, skip anything that slipped through
            }
        }
        return Map.copyOf(parsed);
    }

    /** Fuel units a single item of this stack is worth, or 0 if it is not a valid fuel. */
    public static long valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        return all().getOrDefault(stack.getItem(), 0L);
    }

    public static boolean isFuel(ItemStack stack) {
        return valueOf(stack) > 0L;
    }

    /** Ordered view of the accepted fuels, used for the GUI tooltip. */
    public static Map<Item, Long> all() {
        Map<Item, Long> current = values;
        if (current.isEmpty()) {
            // Config not synced yet (e.g. right after login): fall back to the defaults.
            current = parse(Config.FUEL_ITEMS.getDefault());
            values = current;
        }
        return current;
    }
}
