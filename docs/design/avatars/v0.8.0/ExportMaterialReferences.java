import com.runmoni.app.avatar.*;
import com.runmoni.app.domain.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;

/** Reference exports from the previous verified pure renderer; not new artwork approval. */
public class ExportMaterialReferences {
 static final int W=256,H=384;
 static int[] cell(BufferedImage s,int r,int f){return s.getRGB(f*W,r*H,W,H,null,0,W);}
 static void put(BufferedImage s,int[] p,int r,int f){s.setRGB(f*W,r*H,W,H,p,0,W);}
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
  Path a=root.resolve("app/src/main/assets/avatars");
  BufferedImage src=ImageIO.read(a.resolve("v0.3.0/running-motion.png").toFile()),fabric=ImageIO.read(a.resolve("running-kit-v1/fabric-mask.png").toFile()),coverage=ImageIO.read(a.resolve("wardrobe-v1/coverage.png").toFile()),hands=ImageIO.read(a.resolve("wardrobe-v1/hand-protection.png").toFile());
  StringBuilder lua=new StringBuilder("return {cells={\n");
  for(int r=0;r<2;r++)for(int f=0;f<8;f++){
   var gender=r==0?AvatarGender.MALE:AvatarGender.FEMALE;var anchor=AvatarEquipmentLayers.INSTANCE.anchors(f,gender);
   lua.append(String.format("{row=%d,frame=%d,gender='%s',eyeX=%d,eyeY=%d,waistX=%d,waistY=%d},\n",r,f,gender.getWireValue(),anchor.getEye().getX(),anchor.getEye().getY(),anchor.getWaist().getX(),anchor.getWaist().getY()));
  }
  lua.append("}}\n");Files.writeString(out.resolve("references.lua"),lua);
  for(String key:new String[]{"headwear-1","headwear-2","costume-1","costume-4","costume-5"}){
   BufferedImage sheet=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB),maskSheet=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB);
   int variant=Integer.parseInt(key.substring(key.length()-1));boolean costume=key.startsWith("costume");
   for(int r=0;r<2;r++)for(int f=0;f<8;f++){
    var gender=r==0?AvatarGender.MALE:AvatarGender.FEMALE;int[] source=cell(src,r,f),fm=cell(fabric,r,f),cm=cell(coverage,r,f),hm=cell(hands,r,f);
    var kind=costume?(variant==1?AvatarHeadwearLayers.Kind.SANTA:variant==4?AvatarHeadwearLayers.Kind.BEAR:AvatarHeadwearLayers.Kind.GOLDEN):(variant==1?AvatarHeadwearLayers.Kind.CAP:AvatarHeadwearLayers.Kind.VISOR);
    int[] base=costume?AvatarCostumeLayers.INSTANCE.render(source,fm,cm,hm,f,gender,variant):AvatarHeadwearLayers.INSTANCE.render(source,source,f,gender,kind);
    int[] front=AvatarHeadwearLayers.INSTANCE.occlusionMask(source,f,gender,kind),ext=AvatarHeadwearLayers.INSTANCE.extensionMask(source,f,gender,kind),allow=new int[source.length];var eye=AvatarEquipmentLayers.INSTANCE.anchors(f,gender).getEye();int headEnd=r==0?182:176;
    for(int i=0;i<allow.length;i++){
     int x=i%W,y=i/W,region=(fm[i]>>>16)&255,flags=(cm[i]>>>16)&255;
     boolean head=front[i]!=0||ext[i]!=0;
     boolean neck=x>=eye.getX()-14&&x<=eye.getX()+10&&y>=eye.getY()+20&&y<=eye.getY()+45;
     boolean body=costume&&(region>=1&&region<=3||(flags&6)!=0)&&!((y<headEnd||neck)&&region!=1)&&hm[i]==0&&(source[i]>>>24)>0;
     if((head||body)&&(base[i]>>>24)>0&&base[i]!=source[i])allow[i]=0xffffffff;
    }
    put(sheet,base,r,f);put(maskSheet,allow,r,f);
   }
   ImageIO.write(sheet,"png",out.resolve(key+"-base.png").toFile());ImageIO.write(maskSheet,"png",out.resolve(key+"-allow.png").toFile());
  }
  System.out.println("Exported5 reference/explicit allowance sheets and16 pose anchors; source unchanged");
 }
}
