package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;

/** Java2D nearest-neighbor adapter, deliberately excluded from app source sets. */
public final class Canvas {
    private final Graphics2D graphics;
    private static final class State {
        final AffineTransform transform;
        final Shape clip;
        State(AffineTransform transform, Shape clip) { this.transform = transform; this.clip = clip; }
    }
    private final ArrayDeque<State> stack = new ArrayDeque<>();
    public Canvas(Bitmap bitmap) {
        graphics = bitmap.getImage().createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
    }
    public int save() {
        int checkpoint = stack.size() + 1;
        stack.push(new State(graphics.getTransform(), graphics.getClip()));
        return checkpoint;
    }
    public void restoreToCount(int checkpoint) {
        if (checkpoint < 1 || checkpoint > stack.size()) throw new IllegalArgumentException("Unbalanced Canvas restore");
        while (stack.size() >= checkpoint) {
            State saved = stack.pop();
            graphics.setTransform(saved.transform);
            graphics.setClip(saved.clip);
        }
    }
    public void translate(float x, float y) { graphics.translate(x, y); }
    public void rotate(float degrees) { graphics.rotate(Math.toRadians(degrees)); }
    public boolean clipRect(float left, float top, float right, float bottom) {
        graphics.clip(new Rectangle2D.Float(left, top, Math.max(0f, right - left), Math.max(0f, bottom - top)));
        Shape clip = graphics.getClip();
        return clip == null || !clip.getBounds2D().isEmpty();
    }
    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        graphics.drawImage(bitmap.getImage(), AffineTransform.getTranslateInstance(left, top), null);
    }
    private void color(Paint paint) { graphics.setColor(new java.awt.Color(paint.getColor(), true)); }
    public void drawPath(Path path, Paint paint) { color(paint); graphics.fill(path.path); }
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        color(paint); graphics.fill(new Rectangle2D.Float(left, top, right - left, bottom - top));
    }
    public void drawOval(RectF bounds, Paint paint) {
        color(paint); graphics.fill(new Ellipse2D.Float(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top));
    }
}
