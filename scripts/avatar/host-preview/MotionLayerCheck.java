package com.runmoni.hostpreview;

import com.runmoni.app.avatar.GarmentPalette;
import android.graphics.Bitmap;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Real full-sheet masks/pixels, independent of the Android compatibility renderer. */
public final class MotionLayerCheck {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static int[] read(Path path) throws Exception {
        BufferedImage im=ImageIO.read(path.toFile());
        check(im.getWidth()==2048 && im.getHeight()==768,"Invalid motion sheet dimensions");
        return im.getRGB(0,0,2048,768,null,0,2048);
    }
    private static BufferedImage image(int[] p,int width,int height) {
        BufferedImage im=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0,0,width,height,p,0,width);return im;
    }
    public static void main(String[] args) throws Exception {
        Path assets=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        int[] approved=read(assets.resolve("approved-running.png"));
        int[] raw=read(assets.resolve("swimwear-motion-generated.png"));
        int[] identity=read(assets.resolve("motion-identity-mask.png"));
        int[] fabric=read(assets.resolve("motion-fabric-mask.png"));
        int[] locked=GarmentPalette.protectIdentity(approved,raw,identity);
        int lockedPixels=0, recoveredPixels=0;
        for(int i=0;i<locked.length;i++) {
            if(identity[i]!=0){
                lockedPixels++;if(raw[i]!=approved[i])recoveredPixels++;
                check(locked[i]==approved[i],"Identity lock changed source pixel");
                check(fabric[i]==0,"Fabric mask overlaps locked identity");
            } else check(locked[i]==raw[i],"Unlocked proposal changed");
        }
        check(lockedPixels>600000,"Identity locks incomplete");
        int combinations=0;
        for(int shorts=0;shorts<6;shorts++)for(int top=0;top<6;top++)for(int bottom=0;bottom<6;bottom++){
            int[] colored=GarmentPalette.recolor(locked,fabric,shorts,top,bottom);
            for(int i=0;i<colored.length;i++){
                check((colored[i]>>>24)==(locked[i]>>>24),"Motion alpha changed");
                if(fabric[i]==0)check(colored[i]==locked[i],"Motion color escaped fabric mask");
                if(identity[i]!=0)check(colored[i]==approved[i],"Motion color changed original identity");
            }
            combinations++;
        }
        ImageIO.write(image(locked,2048,768),"png",out.resolve("swimwear-identity-locked.png").toFile());
        int[] tinted=GarmentPalette.recolor(locked,fabric,3,2,4);
        var originalFrames=new ArrayList<Bitmap>();var coloredFrames=new ArrayList<Bitmap>();
        for(int f=0;f<8;f++){
            int[] clean=GarmentPalette.reviewPair(locked,f), crop=GarmentPalette.referencePair(locked,2048,768,f);
            for(int i=0;i<clean.length;i++)check(clean[i]==(i<512*12?0:crop[i]),"Padding cleanup changed character pixels");
            originalFrames.add(new Bitmap(image(clean,512,384)));
            coloredFrames.add(new Bitmap(image(GarmentPalette.reviewPair(tinted,f),512,384)));
        }
        PreviewImages.gif(originalFrames,out.resolve("swimwear-motion-review.gif"));
        PreviewImages.gif(coloredFrames,out.resolve("swimwear-motion-colors.gif"));
        BufferedImage comparison=new BufferedImage(1024,430,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=comparison.createGraphics();g.setColor(new Color(0xe6f0e8));g.fillRect(0,0,1024,430);
        g.setColor(new Color(0x182a23));g.setFont(new Font("SansSerif",Font.BOLD,17));
        g.drawString("APPROVED original / frame 0",12,28);g.drawString("SWIMSUIT / source identity locks / REVIEW",524,28);
        g.drawImage(image(GarmentPalette.referencePair(approved,2048,768,0),512,384),0,40,null);
        g.drawImage(originalFrames.get(0).getImage(),512,40,null);g.dispose();
        ImageIO.write(comparison,"png",out.resolve("motion-comparison.png").toFile());
        String report="{\n  \"paletteCombinationsPassed\":"+combinations+",\n  \"originalLockedPixels\":"+lockedPixels+
            ",\n  \"generatedDifferencesReplacedBySource\":"+recoveredPixels+",\n  \"outsideFabricChanges\":0,\n  \"frames\":8,\n  \"frameMillis\":90,\n  \"humanApproved\":false,\n  \"androidOsExecution\":false\n}\n";
        Files.writeString(out.resolve("motion-verification.json"),report);System.out.println(report);
    }
}
