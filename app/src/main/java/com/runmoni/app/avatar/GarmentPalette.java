package com.runmoni.app.avatar;

/** Deterministic fabric-only color replacement. No skin detection or global hue filter. */
public final class GarmentPalette {
    private GarmentPalette() { }

    // Palette IDs are review settings, never inventory item IDs. Zero preserves source pixels.
    public static final int ORIGINAL = 0;
    public static final int COLORS = 6;
    private static final int[][] PALETTES = {
        {},
        {0x155563, 0x187b85, 0x21a3a8, 0x43c5bf, 0x89e1d2}, // mint
        {0x932f49, 0xc5465a, 0xf36870, 0xff9290, 0xffc3af}, // coral
        {0x14377b, 0x205ab5, 0x3487ec, 0x68acff, 0xb1d7ff}, // blue
        {0x49376f, 0x7452a4, 0xa374df, 0xc39aef, 0xe3c9ff}, // purple
        {0x81501d, 0xaf792f, 0xe1ac49, 0xf3cc72, 0xffe5a6}, // gold
    };

    public static int checkedColor(int value) {
        return value >= 0 && value < COLORS ? value : ORIGINAL;
    }

    /**
     * Mask: alpha=255, red=region (1 shorts, 2 bikini top, 3 bikini bottom),
     * green=shade (0..255), blue=0. Alpha=0 means copy the source bit-for-bit.
     * Invalid labels fail closed rather than coloring unrelated pixels.
     */
    public static int[] recolor(int[] source, int[] mask, int shorts, int top, int bottom) {
        if (source.length != mask.length) throw new IllegalArgumentException("Mask size mismatch");
        int[] colors = {ORIGINAL, checkedColor(shorts), checkedColor(top), checkedColor(bottom)};
        int[] result = source.clone();
        for (int i = 0; i < source.length; i++) {
            int label = mask[i];
            if ((label >>> 24) == 0) continue;
            int region = (label >>> 16) & 255;
            int shade = (label >>> 8) & 255;
            if ((label >>> 24) != 255 || region < 1 || region > 3 || (label & 255) != 0)
                throw new IllegalArgumentException("Invalid garment mask at pixel " + i);
            int selected = colors[region];
            if (selected == ORIGINAL || (source[i] >>> 24) == 0) continue;
            // Interpolate the authored ramp to retain the source's small shading
            // variations instead of introducing five hard, speckled color bands.
            int position = shade * 4, index = position / 255, fraction = position % 255;
            int a = PALETTES[selected][index], b = PALETTES[selected][Math.min(index + 1, 4)];
            int rgb = 0;
            for (int shift : new int[]{16, 8, 0}) {
                int lo = (a >>> shift) & 255, hi = (b >>> shift) & 255;
                int channel = (lo * (255 - fraction) + hi * fraction + 127) / 255;
                rgb |= channel << shift;
            }
            result[i] = (source[i] & 0xff000000) | rgb;
        }
        return result;
    }

    /** Direct crops of the approved 2x8 sheet. No bone rotation, scaling or pose synthesis. */
    public static int[] referencePair(int[] sheet, int width, int height, int frame) {
        if (width != 2048 || height != 768 || sheet.length != width * height)
            throw new IllegalArgumentException("Expected approved 2048x768 sheet");
        int f = Math.floorMod(frame, 8);
        int[] pair = new int[512 * 384];
        for (int y = 0; y < 384; y++) {
            System.arraycopy(sheet, y * width + f * 256, pair, y * 512, 256);
            System.arraycopy(sheet, (y + 384) * width + f * 256, pair, y * 512 + 256, 256);
        }
        return pair;
    }

    /** Whole-frame clothing edit with an authored identity lock, never a body-part rig. */
    public static int[] protectIdentity(int[] approved, int[] proposal, int[] identityMask) {
        if (approved.length != proposal.length || approved.length != identityMask.length)
            throw new IllegalArgumentException("Identity layer dimensions differ");
        int[] result = proposal.clone();
        for (int i = 0; i < result.length; i++) {
            if (identityMask[i] == 0) continue;
            if (identityMask[i] != 0xffffffff) throw new IllegalArgumentException("Invalid identity lock");
            result[i] = approved[i];
        }
        return result;
    }

    /** The sheet's previous-row shoe bleeds into the first 12px of some female cells.
     * Both characters' actual hair starts below this padding. Source files stay intact.
     */
    public static int[] reviewPair(int[] sheet, int frame) {
        int[] pair = referencePair(sheet, 2048, 768, frame);
        java.util.Arrays.fill(pair, 0, 512 * 12, 0);
        return pair;
    }
}
