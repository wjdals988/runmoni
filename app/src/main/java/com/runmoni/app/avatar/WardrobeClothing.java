package com.runmoni.app.avatar;

/** Fixed-frame garment material rendering. All coverage is explicitly authored data. */
public final class WardrobeClothing {
    private WardrobeClothing() { }
    private static final int[] CHARCOAL = {0x111b28,0x1b2938,0x2b3c4d,0x445b69,0x7d929c};
    private static final int[][] WAIST_X = {{142,148,140,141,150,138,140,130},{142,157,149,141,145,159,147,140}};
    private static final int[][] WAIST_Y = {{252,258,257,252,252,260,258,251},{233,239,235,233,233,239,238,233}};

    public static int[] render(int[] source, int[] fabric, int[] coverage,
                             int top, int bottom, int shoes, int topColor, int bottomColor, int shoeColor,
                             int row, int frame) {
        if (source.length != fabric.length || source.length != coverage.length)
            throw new IllegalArgumentException("Wardrobe mask dimensions differ");
        int selectedTop = GarmentPalette.checkedColor(topColor);
        int selectedBottom = GarmentPalette.checkedColor(bottomColor);
        int selectedShoes = GarmentPalette.checkedColor(shoeColor);
        if (selectedTop == 0) selectedTop = top == 1 ? 1 : top == 2 ? 3 : top == 3 ? 5 : 0;
        if (selectedShoes == 0) selectedShoes = shoes == 1 ? 2 : shoes == 2 ? 3 : shoes == 3 ? 5 : 0;
        int[] result = GarmentPalette.recolorRunningKit(source, fabric, selectedTop, selectedBottom, selectedShoes);
        int pose = Math.floorMod(frame,8), startY = row==0 ? 190 : 177, endY=WAIST_Y[row][pose]-3;
        for (int i=0;i<result.length;i++) {
            int label=coverage[i], flags=(label>>>16)&255, shade=(label>>>8)&255, cuff=label&255;
            if (label!=0 && ((label>>>24)!=255 || flags<1 || flags>7 || cuff>3))
                throw new IllegalArgumentException("Invalid wardrobe coverage at " + i);
            if ((source[i]>>>24)==0) continue;
            int region=(fabric[i]>>>16)&255;
            if (bottom==1 && selectedBottom==0 && region==2)
                result[i]=(source[i]&0xff000000)|ramp(CHARCOAL,(fabric[i]>>>8)&255);
            if (bottom==1 && (flags&4)!=0)
                result[i]=(source[i]&0xff000000)|(selectedBottom==0 ? ramp(CHARCOAL,shade) : GarmentPalette.shade(selectedBottom,shade));
            int sleeve = top==1 ? 1 : top==2 || top==3 ? 2 : 0;
            if (sleeve!=0 && (flags&sleeve)!=0) {
                int clothShade = (cuff&sleeve)!=0 ? Math.max(0,shade-45) : shade;
                result[i]=(source[i]&0xff000000)|GarmentPalette.shade(selectedTop,clothShade);
            }
            if (top==3 && region==1) {
                int x=i%256,y=i/256;
                // The zipper follows each original cell's authored chest axis.
                // It is clipped to fabric, so the front arm always occludes it.
                int axis=WAIST_X[row][pose]+22-(y-startY)*12/(endY-startY);
                if (y>=startY && y<=endY && Math.abs(x-axis)<=1)
                    result[i]=(source[i]&0xff000000)|0xd7e4e0;
            }
        }
        return result;
    }

    private static int ramp(int[] palette,int shade) {
        int position=shade*4,index=position/255,fraction=position%255,rgb=0;
        for(int shift=16;shift>=0;shift-=8) {
            int a=(palette[index]>>>shift)&255,b=(palette[Math.min(index+1,4)]>>>shift)&255;
            rgb|=((a*(255-fraction)+b*fraction+127)/255)<<shift;
        }
        return rgb;
    }
}
