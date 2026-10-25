package android.graphics;

public final class Color {
    public static final int TRANSPARENT = 0;
    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >>> 16) & 255; }
    public static int green(int color) { return (color >>> 8) & 255; }
    public static int blue(int color) { return color & 255; }
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    public static int parseColor(String value) {
        if (!value.startsWith("#")) throw new IllegalArgumentException("Host preview requires #RRGGBB/#AARRGGBB: " + value);
        long parsed = Long.parseLong(value.substring(1), 16);
        if (value.length() == 7) return (int) (0xff000000L | parsed);
        if (value.length() == 9) return (int) parsed;
        throw new IllegalArgumentException("Invalid color: " + value);
    }
}
