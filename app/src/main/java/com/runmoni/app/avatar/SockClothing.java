package com.runmoni.app.avatar;

/** Source-specific sock material over authored visible shins, never a new leg.
 * R: crew1/knee2 flags; G: material shade; B: cuff1/cuff2/rib4 flags.
 * Alpha and every pixel outside the selected mask are copied unchanged.
 */
public final class SockClothing {
    private SockClothing() { }
    private static final int[] CREAM = {0x718e89, 0xa6bdb3, 0xcbd9ce, 0xe8eee1, 0xf8faef};
    private static final int[] CUFF = {0x1d4946, 0x28675c, 0x49957d, 0x83c2a5, 0xc1e4ca};

    public static int[] render(int[] source, int[] mask, int variant) {
        if (source.length != mask.length || variant < 0 || variant > 2)
            throw new IllegalArgumentException("Invalid sock material inputs");
        int[] out = source.clone();
        int selected = variant == 1 ? 1 : variant == 2 ? 2 : 0;
        for (int i = 0; i < out.length; i++) {
            int label = mask[i];
            if (label == 0) continue;
            int flags = (label >>> 16) & 255, detail = label & 255;
            if ((label >>> 24) != 255 || flags < 1 || flags > 3 || detail > 7)
                throw new IllegalArgumentException("Invalid sock label at " + i);
            if (selected == 0 || (flags & selected) == 0 || (source[i] >>> 24) == 0) continue;
            int shade = (label >>> 8) & 255;
            boolean cuff = (detail & selected) != 0;
            // Subtle authored rib columns avoid a flat white block while the
            // continuous source shade retains the approved pixel highlights.
            if (!cuff && (detail & 4) != 0) shade = Math.max(0, shade - 8);
            out[i] = (source[i] & 0xff000000) | ramp(cuff ? CUFF : CREAM, shade);
        }
        return out;
    }

    private static int ramp(int[] palette, int shade) {
        int position = shade * 4, index = position / 255, fraction = position % 255, rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (palette[index] >>> shift) & 255;
            int b = (palette[Math.min(index + 1, 4)] >>> shift) & 255;
            rgb |= ((a * (255 - fraction) + b * fraction + 127) / 255) << shift;
        }
        return rgb;
    }
}
