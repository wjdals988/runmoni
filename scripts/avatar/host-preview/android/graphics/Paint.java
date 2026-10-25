package android.graphics;

public final class Paint {
    private int color = 0xff000000;
    private boolean antiAlias, filterBitmap, dither;
    public int getColor() { return color; }
    public void setColor(int value) { color = value; }
    public boolean isAntiAlias() { return antiAlias; }
    public void setAntiAlias(boolean value) { antiAlias = value; }
    public boolean isFilterBitmap() { return filterBitmap; }
    public void setFilterBitmap(boolean value) { filterBitmap = value; }
    public boolean isDither() { return dither; }
    public void setDither(boolean value) { dither = value; }
}
