package android.graphics;

import java.io.InputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class BitmapFactory {
    public static final class Options {
        public boolean inScaled;
        public Bitmap.Config inPreferredConfig;
    }
    public static Bitmap decodeStream(InputStream stream, Object padding, Options options) {
        try {
            BufferedImage source = ImageIO.read(stream);
            if (source == null) return null;
            Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            int[] pixels = source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth());
            result.getImage().setRGB(0, 0, source.getWidth(), source.getHeight(), pixels, 0, source.getWidth());
            return result;
        } catch (IOException exception) { return null; }
    }
}
