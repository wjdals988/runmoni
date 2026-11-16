import com.runmoni.app.avatar.*;
import com.runmoni.app.domain.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Executes the production material classes on the real approved cells. Not Android. */
public class PreviewPixelCouture {
 static final int W=256,H=384;
 static AvatarStyle style(AvatarGender g,int c){return new AvatarStyle(0,0,0,0,0,0,0,0,0,0,0,null,null,c,g,new RunningKitColors());}
 static int[] cell(BufferedImage s,int r,int f){return s.getRGB(f*W,r*H,W,H,null,0,W);}
 static void need(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int delta(int[] a,int[] b){int n=0;for(int i=0;i<a.length;i++)if(a[i]!=b[i])n++;return n;}
 static BufferedImage image(int[] p,int row){p=p.clone();if(row==1)Arrays.fill(p,0,256*12,0);var b=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,W,H,p,0,W);return b;}
 static void put(BufferedImage b,int[] p,int col,int row){b.setRGB(col*W,row*H,W,H,image(p,row).getRGB(0,0,W,H,null,0,W),0,W);}
 static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]),out=Path.of(args[1]);String tag=args[2];
  Path assets=root.resolve("app/src/main/assets/avatars");
  need(hash(assets.resolve("v0.3.0/running-motion.png")).equals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"),"Source changed");
  BufferedImage src=ImageIO.read(assets.resolve("v0.3.0/running-motion.png").toFile()),fm=ImageIO.read(assets.resolve("running-kit-v1/fabric-mask.png").toFile()),cm=ImageIO.read(assets.resolve("wardrobe-v1/coverage.png").toFile()),hm=ImageIO.read(assets.resolve("wardrobe-v1/hand-protection.png").toFile()),sm=ImageIO.read(assets.resolve("socks-v1/sock-mask.png").toFile());
  var kinds=AvatarHeadwearLayers.Kind.values();
  BufferedImage heads=new BufferedImage(W*kinds.length,H*2,BufferedImage.TYPE_INT_ARGB),contact=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB),gear=new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB);
  Map<String,BufferedImage> clips=new LinkedHashMap<>();for(var k:kinds)clips.put(k.name(),new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB));
  for(int c:new int[]{1,4,5})clips.put("COSTUME"+c,new BufferedImage(W*8,H*2,BufferedImage.TYPE_INT_ARGB));
  BufferedImage[][] previews=new BufferedImage[2][8];
  int headCases=0,costumeCases=0,gearCases=0;long frontOcclusion=0;Set<String> poseHashes=new HashSet<>();
  for(int row=0;row<2;row++)for(int f=0;f<8;f++){
   AvatarGender gender=row==0?AvatarGender.MALE:AvatarGender.FEMALE;
   int[] base=cell(src,row,f),fabric=cell(fm,row,f),coverage=cell(cm,row,f),hands=cell(hm,row,f),socks=cell(sm,row,f);
   int[][] originals={base.clone(),fabric.clone(),coverage.clone(),hands.clone(),socks.clone()};
   var anchor=AvatarEquipmentLayers.INSTANCE.anchors(f,gender).getEye();
   for(int ki=0;ki<kinds.length;ki++){
    var kind=kinds[ki];int[] actual=AvatarHeadwearLayers.INSTANCE.render(base,base,f,gender,kind),occ=AvatarHeadwearLayers.INSTANCE.occlusionMask(base,f,gender,kind),ext=AvatarHeadwearLayers.INSTANCE.extensionMask(base,f,gender,kind);
    int front=0;for(int i=0;i<base.length;i++){
     if(base[i]!=actual[i])need(occ[i]!=0||ext[i]!=0,"Headwear escaped its explicit masks "+kind+"/"+row+"/"+f+"/"+i);
     if(ext[i]==0)need((base[i]>>>24)==(actual[i]>>>24),"Headwear changed non-extension alpha");
     if(occ[i]!=0&&base[i]!=actual[i])front++;
     int x=i%W,y=i/W;
     // Independent central face/eyes/nose/mouth window, never a hair mask.
     if(x>=anchor.getX()-3&&x<=anchor.getX()+35&&y>=anchor.getY()-12&&y<=anchor.getY()+30)
      need(base[i]==actual[i],"Headwear covered the central original face "+kind+"/"+row+"/"+f+"/"+x+"/"+y);
    }
    need(front>30,"Headwear is still only background "+kind+"/"+row+"/"+f);
    need(delta(base,actual)>100,"Missing wearable silhouette");frontOcclusion+=front;headCases++;
    need(Arrays.equals(actual,AvatarHeadwearLayers.INSTANCE.render(base,base,f+8,gender,kind)),"Headwear wrapped pose differs");
    put(clips.get(kind.name()),actual,f,row);if(f==0)put(heads,actual,ki,row);
    if(f==0&&(kind==AvatarHeadwearLayers.Kind.CAP||kind==AvatarHeadwearLayers.Kind.VISOR)){
     int col=kind==AvatarHeadwearLayers.Kind.CAP?1:2;put(contact,actual,col,row);previews[row][col]=image(actual,row);
    }
   }
   if(f==0){put(contact,base,0,row);previews[row][0]=image(base,row);}
   for(int c:new int[]{1,4,5}){
    int[] actual=AvatarCostumeLayers.INSTANCE.render(base,fabric,coverage,hands,f,gender,c),occ=AvatarCostumeLayers.INSTANCE.hairOcclusionMask(base,f,gender,c),ext=AvatarCostumeLayers.INSTANCE.clothingExtensionMask(base,f,gender,c);
    for(int i=0;i<base.length;i++){
     if(hands[i]!=0)need(base[i]==actual[i],"Costume covered an independently protected hand");
     if(ext[i]==0)need((base[i]>>>24)==(actual[i]>>>24),"Costume changed body alpha");
     int x=i%W,y=i/W;
     if(x>=anchor.getX()-3&&x<=anchor.getX()+35&&y>=anchor.getY()-12&&y<=anchor.getY()+30)
      need(base[i]==actual[i],"Costume covered central face");
     boolean cloth=((fabric[i]>>>16)&255)>=1&&((fabric[i]>>>16)&255)<=3||((coverage[i]>>>16)&6)!=0;
     if(base[i]!=actual[i])need(cloth||occ[i]!=0||ext[i]!=0,"Costume escaped garment/head allowance");
    }
    need(delta(base,actual)>300,"Costume missing");costumeCases++;poseHashes.add(row+"/"+c+"/"+Arrays.hashCode(actual));
    put(clips.get("COSTUME"+c),actual,f,row);if(f==0){int col=c==1?3:c==4?4:5;put(contact,actual,col,row);previews[row][col]=image(actual,row);}
   }
   String[] slots={"headwear","ear","eyewear","wrist","waist","hydration"};
   for(String slot:slots)for(int v=1;v<=3;v++){
    AvatarStyle item=AvatarCatalogueKt.withItem(style(gender,0),slot,v);
    int[] actual=AvatarEquipmentLayers.INSTANCE.render(base,base,fabric,f,gender,item,hands);
    need(delta(base,actual)>20,"Invisible equipment "+slot+"/"+v+"/"+row+"/"+f);
    if(slot.equals("waist")||slot.equals("hydration"))for(int i=0;i<base.length;i++){
     if(fabric[i]==0||hands[i]!=0)need(base[i]==actual[i],"Torso gear covered original arm/hand");
    }
    gearCases++;
   }
   int[] under=WardrobeClothing.render(base,fabric,coverage,2,1,2,0,0,0,row,f);under=SockClothing.render(under,socks,2);
   AvatarStyle all=style(gender,0);for(String slot:slots)all=AvatarCatalogueKt.withItem(all,slot,slot.equals("headwear")?1:1);
   int[] actual=AvatarEquipmentLayers.INSTANCE.render(under,base,fabric,f,gender,all,hands);put(gear,actual,f,row);
   if(f==0){put(contact,actual,6,row);previews[row][6]=image(actual,row);}
   AvatarStyle pair=AvatarCatalogueKt.withItem(AvatarCatalogueKt.withItem(style(gender,0),"headwear",2),"ear",2);
   actual=AvatarEquipmentLayers.INSTANCE.render(base,base,fabric,f,gender,pair,hands);
   if(f==0){put(contact,actual,7,row);previews[row][7]=image(actual,row);}
   need(Arrays.equals(base,originals[0])&&Arrays.equals(fabric,originals[1])&&Arrays.equals(coverage,originals[2])&&Arrays.equals(hands,originals[3])&&Arrays.equals(socks,originals[4]),"Immutable input mutated");
  }
  need(poseHashes.size()==48,"Eight poses not retained per costume/gender");
  ImageIO.write(heads,"png",out.resolve("host-headwear-"+tag+".png").toFile());ImageIO.write(contact,"png",out.resolve("host-contact-"+tag+".png").toFile());ImageIO.write(gear,"png",out.resolve("host-gear-"+tag+".png").toFile());
  for(var entry:clips.entrySet())ImageIO.write(entry.getValue(),"png",out.resolve("host-"+entry.getKey().toLowerCase(Locale.ROOT)+"-"+tag+".png").toFile());
  for(boolean dark:new boolean[]{false,true}){
   var board=new BufferedImage(1056,928,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();g.setColor(new Color(dark?0x18232c:0xf7f9fa));g.fillRect(0,0,1056,928);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
   String[] labels={"Original","Running cap","Visor","Santa","Bear","Golden","Full gear","Visor + buds"};int y=20;
   for(int size:new int[]{80,112,140}){g.setColor(new Color(dark?0xe2eceb:0x24343d));g.setFont(new Font("SansSerif",Font.PLAIN,12));g.drawString("HOST production pixels / "+size+"px / nearest-neighbour / NOT Android",16,y);y+=18;
    for(int row=0;row<2;row++){for(int col=0;col<8;col++){int width=(int)Math.round(size*256.0/384);g.drawImage(previews[row][col],col*132+(132-width)/2,y,width,size,null);g.drawString(labels[col],col*132+8,y+size+13);}y+=size+28;}}
   g.dispose();ImageIO.write(board,"png",out.resolve("host-small-"+(dark?"dark":"light")+"-"+tag+".png").toFile());
  }
  System.out.println("{\"passed\":true,\"headwearCases\":"+headCases+",\"costumeCases\":"+costumeCases+",\"singleEquipmentCases\":"+gearCases+",\"frontOcclusionPixels\":"+frontOcclusion+",\"sourceInputsUnchanged\":true,\"originalCostumePoseCount\":48,\"androidExecution\":false,\"userVisualApproval\":false}");
 }
}
