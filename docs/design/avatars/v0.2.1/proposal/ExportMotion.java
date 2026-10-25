import com.runmoni.app.avatar.AvatarRasterizer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Code-native motion review; exported directly from the candidate app renderer. */
public class ExportMotion {
    public static void main(String[] args) throws Exception {
        File directory=new File(args[0]);directory.mkdirs();
        int[] costumes={0,1,4};
        for(int f=0;f<8;f++) {
            BufferedImage page=new BufferedImage(288,112*3,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=page.createGraphics();g.setColor(new Color(0xfff8ef));g.fillRect(0,0,page.getWidth(),page.getHeight());
            g.setColor(new Color(0xdce8df));
            for(int row=0;row<3;row++) {
                g.drawLine(4,row*112+110,284,row*112+110);
                for(int h=0;h<2;h++) {
                    BufferedImage avatar=new BufferedImage(96,112,BufferedImage.TYPE_INT_ARGB);
                    int[] pixels=AvatarRasterizer.render(h==0?0:2,0,0,0,0,0,0,0,0,1,0,0,costumes[row],f,true);
                    avatar.setRGB(0,0,96,112,pixels,0,96);
                    g.drawImage(avatar,36+h*120,row*112,null);
                }
            }
            g.dispose();ImageIO.write(page,"png",new File(directory,"frame-"+f+".png"));
        }
        BufferedImage atlas=new BufferedImage(96*8,112,BufferedImage.TYPE_INT_ARGB);
        for(int f=0;f<8;f++)atlas.setRGB(f*96,0,96,112,AvatarRasterizer.render(0,0,0,0,0,0,0,0,0,1,0,0,0,f,true),0,96);
        ImageIO.write(atlas,"png",new File(directory,"run-atlas.png"));
        System.out.println("Exported 8 frames, 3 outfits, same two hair styles: "+directory.getCanonicalPath());
    }
}
