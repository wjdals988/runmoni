import com.runmoni.app.avatar.WardrobeClothing;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Host material evidence, never an Android screenshot or visual approval. */
public class VerifyWardrobe {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
  BufferedImage source=ImageIO.read(root.resolve("docs/design/avatars/v0.3.0/running-motion-art-source.png").toFile());
  BufferedImage fabric=ImageIO.read(root.resolve("docs/design/avatars/running-kit-v1/fabric-mask.png").toFile());
  BufferedImage coverage=ImageIO.read(root.resolve("docs/design/avatars/wardrobe-v1/coverage.png").toFile());
  BufferedImage protectedHands=ImageIO.read(root.resolve("docs/design/avatars/wardrobe-v1/hand-protection.png").toFile());
  int[][] variants={{0,0,0},{1,0,0},{2,0,0},{3,0,0},{0,1,0},{0,0,1},{0,0,2},{0,0,3},{3,1,2}};
  BufferedImage contact=new BufferedImage(variants.length*256,2*384,BufferedImage.TYPE_INT_ARGB);
  Map<Integer,BufferedImage> sheets=new HashMap<>();
  for(int v:new int[]{1,2,3,4,8}) sheets.put(v,new BufferedImage(2048,768,BufferedImage.TYPE_INT_ARGB));
  int cases=0;long changed=0;
  for(int row=0;row<2;row++) for(int frame=0;frame<8;frame++) {
   int[] base=source.getRGB(frame*256,row*384,256,384,null,0,256);
   int[] mask=fabric.getRGB(frame*256,row*384,256,384,null,0,256);
   int[] cover=coverage.getRGB(frame*256,row*384,256,384,null,0,256);
   int[] hands=protectedHands.getRGB(frame*256,row*384,256,384,null,0,256);
   for(int i=0;i<cover.length;i++) if(cover[i]!=0 && hands[i]!=0) throw new AssertionError("Coverage overlaps a protected hand");
   for(int v=0;v<variants.length;v++) {
    int[] item=variants[v];
    for(int color=0;color<6;color++) {
     int[] result=WardrobeClothing.render(base,mask,cover,item[0],item[1],item[2],color,color,color,row,frame);
     int diff=0;
     for(int i=0;i<base.length;i++) {
      if((base[i]>>>24)!=(result[i]>>>24)) throw new AssertionError("Alpha changed");
      if(base[i]!=result[i]) {
       diff++;
       if(mask[i]==0 && cover[i]==0) throw new AssertionError("Unlabeled pixel changed");
       if(mask[i]==0 && i/256<(row==0?182:176)) throw new AssertionError("Identity changed");
      }
     }
     if(v==0 && color==0 && diff!=0) throw new AssertionError("Original changed");
     if(v>0 && color==0 && diff<20) throw new AssertionError("Item did not change cell " + row+"/"+frame+"/"+v);
     if(frame==0 && color==0) contact.setRGB(v*256,row*384,256,384,result,0,256);
     if(color==0 && sheets.containsKey(v)) sheets.get(v).setRGB(frame*256,row*384,256,384,result,0,256);
     cases++;changed+=diff;
    }
   }
  }
  ImageIO.write(contact,"png",out.resolve("clothing-contact.png").toFile());
  for(var entry:sheets.entrySet()) ImageIO.write(entry.getValue(),"png",out.resolve("clothing-16cells-"+entry.getKey()+".png").toFile());
  System.out.println("{\"cases\":"+cases+",\"changedPixelsAcrossCases\":"+changed+",\"sourceIdentity\":true,\"alphaPreserved\":true,\"outsideMasksUnchanged\":true,\"visualApproval\":\"pending\",\"androidExecution\":false}");
 }
}
