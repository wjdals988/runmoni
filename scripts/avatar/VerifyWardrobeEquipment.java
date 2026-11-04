import com.runmoni.app.avatar.AvatarEquipmentLayers;
import com.runmoni.app.avatar.AvatarArtwork;
import com.runmoni.app.domain.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Executes the production Kotlin equipment bytecode, on a host JVM only. */
public class VerifyWardrobeEquipment {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
  BufferedImage source=ImageIO.read(root.resolve("docs/design/avatars/v0.3.0/running-motion-art-source.png").toFile());
  BufferedImage fabric=ImageIO.read(root.resolve("docs/design/avatars/running-kit-v1/fabric-mask.png").toFile());
  String[] slots={"headwear","ear","ear","ear","eyewear","eyewear","eyewear","wrist","wrist","wrist","waist","waist","waist","hydration","hydration","hydration","all"};
  int[] variants={3,1,2,3,1,2,3,1,2,3,1,2,3,1,2,3,1};
  BufferedImage head=new BufferedImage(8*256,768,BufferedImage.TYPE_INT_ARGB);
  BufferedImage body=new BufferedImage(11*256,768,BufferedImage.TYPE_INT_ARGB);
  BufferedImage full=new BufferedImage(2048,768,BufferedImage.TYPE_INT_ARGB);
  int cases=0;
  for(int row=0;row<2;row++) for(int frame=0;frame<8;frame++) {
   int[] base=source.getRGB(frame*256,row*384,256,384,null,0,256);
   int[] mask=fabric.getRGB(frame*256,row*384,256,384,null,0,256);
   AvatarGender gender=row==0?AvatarGender.MALE:AvatarGender.FEMALE;
   AvatarStyle original=new AvatarStyle(0,0,0,0,0,0,0,0,0,0,0,null,null,0,gender,new RunningKitColors());
   if(frame==0) { head.setRGB(0,row*384,256,384,base,0,256);body.setRGB(0,row*384,256,384,base,0,256); }
   for(int item=0;item<slots.length;item++) {
    AvatarStyle style=original;
    if(slots[item].equals("all")) {
     for(String slot:new String[]{"ear","eyewear","wrist","waist","hydration"}) style=AvatarCatalogueKt.withItem(style,slot,1);
     style=AvatarCatalogueKt.withItem(style,"headwear",3);
    } else style=AvatarCatalogueKt.withItem(style,slots[item],variants[item]);
    int[] before=base.clone(),maskBefore=mask.clone();
    int[] actual=AvatarEquipmentLayers.INSTANCE.render(base,base,mask,frame,gender,style);
    if(!Arrays.equals(before,base) || !Arrays.equals(maskBefore,mask)) throw new AssertionError("Input mutated");
    int changed=0;
    for(int i=0;i<base.length;i++) {
     if((base[i]>>>24)!=(actual[i]>>>24))throw new AssertionError("Alpha changed");
     if(base[i]!=actual[i]) {
      changed++;
      if((slots[item].equals("waist")||slots[item].equals("hydration")) && mask[i]==0)throw new AssertionError("Torso gear covered foreground limb");
     }
    }
    if(changed<=20) throw new AssertionError("No item output: "+row+"/"+frame+"/"+slots[item]+":"+variants[item]);
    if(frame==0 && item<7) head.setRGB((item+1)*256,row*384,256,384,actual,0,256);
    if(frame==0 && item>=7) body.setRGB((item-6)*256,row*384,256,384,actual,0,256);
    if(slots[item].equals("all"))full.setRGB(frame*256,row*384,256,384,actual,0,256);
    cases++;
   }
  }
  ImageIO.write(head,"png",out.resolve("gear-head-contact.png").toFile());
  ImageIO.write(body,"png",out.resolve("gear-body-contact.png").toFile());
  ImageIO.write(full,"png",out.resolve("gear-all-16cells.png").toFile());
  int ready=0,total=0;
  for(AvatarSlot slot:AvatarCatalogue.INSTANCE.getSlots())for(int v=0;v<slot.getVariants().size();v++) {
   total++; if(AvatarArtwork.INSTANCE.itemReady(slot.getKey(),v,AvatarGender.MALE))ready++;
  }
  System.out.println("{\"cases\":"+cases+",\"readyIds\":"+ready+",\"totalIds\":"+total+",\"inputUnchanged\":true,\"alphaPreserved\":true,\"torsoGearOutsideFabricUnchanged\":true,\"visualApproval\":\"pending\",\"androidExecution\":false}");
 }
}
