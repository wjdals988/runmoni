package com.runmoni.app.avatar;

/** Source-specific sock material over authored visible shins, never a new leg.
 * R: crew1/knee2 flags; G: material shade; B: cuff1/cuff2/rib4 flags.
 * Alpha and every pixel outside the selected mask are copied unchanged.
 */
public final class SockClothing {
    private SockClothing() { }
    public static final String VERSION = "approved-socks-v2-knit";
    private static final int[] CREW = {0x526574, 0x8398a3, 0xb7c6c9, 0xdce5e3, 0xf1f5eb};
    private static final int[] KNEE = {0x172331, 0x263b49, 0x3d5864, 0x64848a, 0xa1bdb7};
    private static final int[] CUFF = {0x16434b, 0x245d64, 0x3b7f80, 0x70b1a5, 0xb9d8c1};

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
            // A graphite knee sock reads as knitted sportswear over the dark
            // tights, rather than a wide pale wrap. The crew sock stays light.
            // Both retain the authored moving shin, cuff and rib labels.
            if (!cuff && (detail & 4) != 0) shade = Math.max(0, shade - 12);
            if (cuff) shade = Math.max(0, shade - 12);
            out[i] = (source[i] & 0xff000000) | ramp(cuff ? CUFF : variant == 1 ? CREW : KNEE, shade);
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
