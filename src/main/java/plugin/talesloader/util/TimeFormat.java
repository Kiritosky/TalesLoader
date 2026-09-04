package plugin.talesloader.util;

/** Formats tick counts as {@code HH:MM:SS} for the GUI and tooltips. */
public final class TimeFormat {
    private TimeFormat() {
    }

    public static String ticksToClock(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** Short form used in listings, e.g. {@code 12h 30m}. */
    public static String ticksToShort(long ticks) {
        long totalMinutes = ticks / (20L * 60L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
