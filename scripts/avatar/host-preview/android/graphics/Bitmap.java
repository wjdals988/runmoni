package android.graphics;

import java.awt.image.BufferedImage;

/** Host-only compatibility surface. This is not Android rasterization. */
public final class Bitmap {
    public enum Config { ARGB_8888 }
    private final BufferedImage image;
    public Bitmap(BufferedImage image) { this.image = image; }
    public BufferedImage getImage() { return image; }
    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }
    public int getAllocationByteCount() { return Math.multiplyExact(Math.multiplyExact(getWidth(), getHeight()), 4); }
    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }
    public static Bitmap createBitmap(int[] pixels, int width, int height, Config config) {
        Bitmap result = createBitmap(width, height, config);
        result.image.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        image.getRGB(x, y, width, height, pixels, offset, stride);
    }
    public Bitmap copy(Config config, boolean mutable) {
        int[] pixels = image.getRGB(0, 0, getWidth(), getHeight(), null, 0, getWidth());
        return createBitmap(pixels, getWidth(), getHeight(), config);
    }
    // BufferedImage storage belongs to GC; the production renderer copies before recycling.
    public void recycle() { }
}
