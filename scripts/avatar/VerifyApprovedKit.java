import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import com.runmoni.app.avatar.GarmentPalette;
/** Host verification of explicit masks and the actual runtime palette algorithm. */
public class VerifyApprovedKit {
 private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
 private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 public static void main(String[] args)throws Exception {
  long start=System.nanoTime();Path root=Path.of("");
  Path src=root.resolve("docs/design/avatars/v0.3.0/running-motion-art-source.png");
  require(sha(src).equals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"),"Source checksum changed");
  BufferedImage source=ImageIO.read(src.toFile()),mask=ImageIO.read(root.resolve("docs/design/avatars/running-kit-v1/fabric-mask.png").toFile()),lock=ImageIO.read(root.resolve("docs/design/avatars/running-kit-v1/protected-mask.png").toFile());
  require(source.getWidth()==2048&&source.getHeight()==768,"Source dimensions");require(mask.getWidth()==2048&&mask.getHeight()==768&&lock.getWidth()==2048&&lock.getHeight()==768,"Mask dimensions");
  int cases=0;long outside=0,protectedChecks=0,alpha=0,disabled=0;
  for(int row=0;row<2;row++)for(int frame=0;frame<8;frame++){
   int[] s=source.getRGB(frame*256,row*384,256,384,null,0,256),m=mask.getRGB(frame*256,row*384,256,384,null,0,256),p=lock.getRGB(frame*256,row*384,256,384,null,0,256),untouched=s.clone();int[] counts=new int[4];
   for(int i=0;i<m.length;i++){
    if(m[i]==0)continue;int region=(m[i]>>>16)&255;
    require(m[i]>>>24==255&&region>=1&&region<=3&&(m[i]&255)==0,"Invalid mask encoding");
    require(p[i]==0,"Protected overlap");require((s[i]>>>24)>0,"Transparent source labeled");require(row==0||i/256>=12,"Female padding labeled");counts[region]++;
   }
   for(int i=1;i<4;i++)require(counts[i]>0,"Missing region");
   for(int top=0;top<6;top++)for(int bottom=0;bottom<6;bottom++)for(int shoes=0;shoes<6;shoes++){
    int[] selected={0,top,bottom,shoes};int[] r=GarmentPalette.recolorRunningKit(s,m,top,bottom,shoes);int[] changed=new int[4];
    for(int i=0;i<r.length;i++){
     require((r[i]>>>24)==(s[i]>>>24),"Alpha changed");alpha++;
     if(m[i]==0){require(r[i]==s[i],"Outside mask changed");outside++;}
     else {int region=(m[i]>>>16)&255;if(selected[region]==0){require(r[i]==s[i],"Other/original region changed");disabled++;}else if(r[i]!=s[i])changed[region]++;}
     if(p[i]!=0){require(r[i]==s[i],"Protected pixel changed");protectedChecks++;}
    }
    for(int region=1;region<4;region++)if(selected[region]!=0)require(changed[region]>0,"Selected region did not change");
    if(top==0&&bottom==0&&shoes==0)require(Arrays.equals(r,s),"Original colors differ");
    require(Arrays.equals(s,untouched),"Source input mutated");cases++;
   }
  }
  for(int bad:new int[]{0x80010000,0xff040000,0xff010001}){
   boolean rejected=false;try{GarmentPalette.recolorRunningKit(new int[]{0xffcc2211},new int[]{bad},1,2,3);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"Bad label accepted");
  }
  int[] a={0x7fcc2211};require(Arrays.equals(a,GarmentPalette.recolorRunningKit(a,new int[]{0xff018000},99,-1,999)),"Unknown colors must preserve source");
  require(sha(src).equals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"),"Source file mutated");
  String json="{\"scope\":\"host raw PNG ARGB and production GarmentPalette; not Android/user approval\",\"cells\":16,\"combinationsPerCell\":216,\"cases\":"+cases+",\"outsideMaskPixelChecks\":"+outside+",\"protectedPixelChecks\":"+protectedChecks+",\"alphaPixelChecks\":"+alpha+",\"disabledRegionPixelChecks\":"+disabled+",\"changedOutsideMask\":0,\"changedProtectedPixels\":0,\"changedAlpha\":0,\"sourceMutated\":false,\"invalidMaskCasesRejected\":3,\"seconds\":"+((System.nanoTime()-start)/1e9)+"}";
  Path out=Path.of(args.length==0?"work/kit-colors/host-verification.json":args[0]);Files.createDirectories(out.getParent());Files.writeString(out,json+"\n");System.out.println(json);
 }
}
