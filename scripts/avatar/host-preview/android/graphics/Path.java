package android.graphics;

import java.awt.geom.Path2D;

public final class Path {
    final Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
    public void moveTo(float x, float y) { path.moveTo(x, y); }
    public void lineTo(float x, float y) { path.lineTo(x, y); }
    public void close() { path.closePath(); }
}
