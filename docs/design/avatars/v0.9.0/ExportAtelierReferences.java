import com.runmoni.app.avatar.*;
import com.runmoni.app.domain.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Reads approved reference/masks; exports independent reference/allowance sheets. Never edits inputs. */
public class ExportAtelierReferences {
 static final int W=256,H=384;
 static final String[] SLOTS={"hair","headwear","face","top","bottom","costume","socks","shoes","ear","eyewear","wrist","waist","hydration"};
 static final int[] START={4,4,4,7,6,6,5,5,4,4,4,4,4};
 static final int[][] BASE={{0,0,0},{1,2,3},{0,0,0},{1,2,3},{0,1,0},{0,0,0},{1,2,1},{2,3,1},{1,2,3},{1,2,3},{1,2,3},{1,2,3},{1,2,3}};
 static int[] cell(BufferedImage s,int r,int f){return s.getRGB(f*W,r*H,W,H,null,0,W);}
 static void put(BufferedImage s,int[] p,int r,int f){s.setRGB(f*W,r*H,W,H,p,0,W);}
 static AvatarStyle style(int r,String slot,int v){return new AvatarStyle(0,0,0,0,0,slot.equals("eyewear")?v:0,slot.equals("wrist")?v:0,slot.equals("waist")?v:0,slot.equals("hydration")?v:0,0,0,slot.equals("headwear")?v:0,slot.equals("ear")?v:0,0,r==0?AvatarGender.MALE:AvatarGender.FEMALE,new RunningKitColors(0,0,0));}
 static boolean inside(int x,int y,int[] p){boolean in=false;int j=p.length-2;for(int i=0;i<p.length;i+=2){if((p[i+1]>y)!=(p[j+1]>y)&&x<(p[j]-p[i])*(double)(y-p[i+1])/(p[j+1]-p[i+1])+p[i])in=!in;j=i;}return in;}
 static final int[] MALE_CROWN={-58,-69,-44,-85,-14,-93,12,-82,13,-66,-3,-53,-31,-49,-51,-55};
 static final int[] MALE_BACK={-65,-59,-52,-59,-52,-29,-65,-29};
 static final int[] PONY={-130,-36,-117,-29,-110,-50,-102,-83,-89,-106,-67,-112,-45,-100,-44,-83,-61,-69,-70,-42,-88,-20,-112,-17};
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
  Path a=root.resolve("app/src/main/assets/avatars");
  String[] paths={"v0.3.0/running-motion.png","running-kit-v1/fabric-mask.png","wardrobe-v1/coverage.png","wardrobe-v1/hand-protection.png","socks-v1/sock-mask.png"};
  BufferedImage[] sheets=new BufferedImage[paths.length];for(int n=0;n<paths.length;n++)sheets[n]=ImageIO.read(a.resolve(paths[n]).toFile());
  StringBuilder lua=new StringBuilder("return {cells={\n");
  for(int r=0;r<2;r++)for(int f=0;f<8;f++){
   var gender=r==0?AvatarGender.MALE:AvatarGender.FEMALE;var t=AvatarEquipmentLayers.INSTANCE.anchors(f,gender);
   lua.append(String.format("{row=%d,frame=%d,gender='%s',eyeX=%d,eyeY=%d,earX=%d,earY=%d,shoulderX=%d,shoulderY=%d,elbowX=%d,elbowY=%d,wristX=%d,wristY=%d,waistX=%d,waistY=%d},\n",r,f,gender.getWireValue(),t.getEye().getX(),t.getEye().getY(),t.getEar().getX(),t.getEar().getY(),t.getNearShoulder().getX(),t.getNearShoulder().getY(),t.getNearElbow().getX(),t.getNearElbow().getY(),t.getNearWrist().getX(),t.getNearWrist().getY(),t.getWaist().getX(),t.getWaist().getY()));
  }
  lua.append("},styles={\n");
  for(int s=0;s<SLOTS.length;s++)for(int k=0;k<3;k++){
   String slot=SLOTS[s],key=slot+"-"+(START[s]+k);int b=BASE[s][k];
   lua.append(String.format("{key='%s',slot='%s',variant=%d,ordinal=%d,base=%d},\n",key,slot,START[s]+k,k+1,b));
   BufferedImage ref=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB),mask=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB),roles=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB);
   for(int r=0;r<2;r++)for(int f=0;f<8;f++){
    AvatarGender g=r==0?AvatarGender.MALE:AvatarGender.FEMALE;int[] src=cell(sheets[0],r,f),fm=cell(sheets[1],r,f),cm=cell(sheets[2],r,f),hm=cell(sheets[3],r,f),sm=cell(sheets[4],r,f);
    int top=slot.equals("top")?b:slot.equals("costume")?(k==0?1:k==1?3:2):0;
    int bottom=slot.equals("bottom")?b:slot.equals("costume")?1:0;
    int shoes=slot.equals("shoes")?b:slot.equals("costume")?(k==0?2:k==1?1:3):0;
    int socks=slot.equals("socks")?b:slot.equals("costume")?(k==0?1:2):0;
    int tc=slot.equals("top")?(k==0?1:k==1?3:5):slot.equals("costume")?(k==0?3:k==1?1:4):0;
    int bc=slot.equals("bottom")?(k==0?3:k==1?4:1):slot.equals("costume")?(k==0?3:k==1?1:4):0;
    int sc=slot.equals("shoes")?(k==0?3:k==1?5:2):slot.equals("costume")?(k==0?3:k==1?1:4):0;
    int[] base=WardrobeClothing.render(src,fm,cm,top,bottom,shoes,tc,bc,sc,r,f);
    base=SockClothing.render(base,sm,socks);
    for(int i=0;i<base.length;i++)if(hm[i]!=0)base[i]=src[i];
    int[] equipment=AvatarEquipmentLayers.INSTANCE.render(base,src,fm,f,g,style(r,slot,b),hm);
    int[] allow=new int[src.length],role=new int[src.length];
    var anchor=AvatarEquipmentLayers.INSTANCE.anchors(f,g);int ex=anchor.getEye().getX(),ey=anchor.getEye().getY();
    int[] hair=AvatarHeadwearLayers.INSTANCE.occlusionMask(src,f,g,AvatarHeadwearLayers.Kind.CAP);
    int[] front=null,ext=null;
    if(slot.equals("headwear")){
     var kind=b==1?AvatarHeadwearLayers.Kind.CAP:b==2?AvatarHeadwearLayers.Kind.VISOR:AvatarHeadwearLayers.Kind.HEADBAND;
     front=AvatarHeadwearLayers.INSTANCE.occlusionMask(src,f,g,kind);ext=AvatarHeadwearLayers.INSTANCE.extensionMask(src,f,g,kind);
    }
    for(int i=0;i<src.length;i++){
     int x=i%W,y=i/W,region=(fm[i]>>>16)&255,flags=(cm[i]>>>16)&255,shade=(fm[i]>>>8)&255,rr=0;
     boolean ok=false;
     switch(slot){
      case "hair": ok=(r==0&&k==2?(inside(x-ex,y-ey,MALE_CROWN)||inside(x-ex,y-ey,MALE_BACK)):(hair[i]!=0||r==1&&k==2&&inside(x-ex,y-ey,PONY)))&&(src[i]>>>24)>=192;break;
      case "face":ok=x>=ex-5&&x<=ex+11&&y>=ey+12&&y<=ey+20&&(src[i]>>>24)>=192;break;
      case "headwear":ok=(front[i]!=0||ext[i]!=0)&&(equipment[i]>>>24)>0;break;
      case "top":case "costume":
       int sleeve=top==1?1:top==2||top==3?2:0;
       if(region==1||sleeve!=0&&(flags&sleeve)!=0){ok=hm[i]==0;rr=1;shade=region==1?shade:(cm[i]>>>8)&255;}
       if(!slot.equals("costume"))break;
      case "bottom":
       if(region==2||bottom==1&&(flags&4)!=0){ok=hm[i]==0;rr=2;shade=region==2?shade:(cm[i]>>>8)&255;}
       if(!slot.equals("costume"))break;
      case "socks":
       if(socks>0&&((sm[i]>>>16)&socks)!=0){ok=true;rr=4;shade=(sm[i]>>>8)&255;}
       if(!slot.equals("costume"))break;
      case "shoes":if(region==3){ok=true;rr=3;}break;
      default:ok=equipment[i]!=base[i]&&(src[i]>>>24)>16;break;
     }
     if(!slot.equals("headwear")&&(src[i]>>>24)==0)ok=false;
     if(ok){allow[i]=-1;role[i]=0xff000000|(rr<<16)|(shade<<8)|(rr==4?(sm[i]&255):0);}
    }
    put(ref,equipment,r,f);put(mask,allow,r,f);put(roles,role,r,f);
   }
   ImageIO.write(ref,"png",out.resolve(key+"-base.png").toFile());ImageIO.write(mask,"png",out.resolve(key+"-allow.png").toFile());ImageIO.write(roles,"png",out.resolve(key+"-roles.png").toFile());
  }
  lua.append("}}\n");Files.writeString(out.resolve("references.lua"),lua);
  System.out.println("Exported39 independent reference/allowance/role sheets +16 original pose anchors; source files untouched");
 }
}
