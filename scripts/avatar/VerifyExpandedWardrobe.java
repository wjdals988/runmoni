import com.runmoni.app.avatar.WardrobeClothing;
import com.runmoni.app.avatar.SockClothing;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Host output of production material code, not an Android screenshot or approval. */
public class VerifyExpandedWardrobe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]), out=Path.of(args[1]); Files.createDirectories(out);
        BufferedImage source=ImageIO.read(root.resolve("docs/design/avatars/v0.3.0/running-motion-art-source.png").toFile());
        BufferedImage fabric=ImageIO.read(root.resolve("docs/design/avatars/running-kit-v1/fabric-mask.png").toFile());
        BufferedImage coverage=ImageIO.read(root.resolve("docs/design/avatars/wardrobe-v1/coverage.png").toFile());
        BufferedImage socks=ImageIO.read(root.resolve("docs/design/avatars/socks-v1/sock-mask.png").toFile());
        BufferedImage protection=ImageIO.read(root.resolve("docs/design/avatars/socks-v1/shoe-protection.png").toFile());
        BufferedImage[] sheets={new BufferedImage(2048,768,2),new BufferedImage(2048,768,2),new BufferedImage(2048,768,2)};
        int cases=0; long changed=0;
        for(int row=0;row<2;row++) for(int f=0;f<8;f++) {
            int[] base=source.getRGB(f*256,row*384,256,384,null,0,256), original=base.clone();
            int[] mask=fabric.getRGB(f*256,row*384,256,384,null,0,256), cover=coverage.getRGB(f*256,row*384,256,384,null,0,256);
            int[] locked=protection.getRGB(f*256,row*384,256,384,null,0,256);
            int[] sock=socks.getRGB(f*256,row*384,256,384,null,0,256), originalMask=sock.clone();
            for(int variant=1;variant<=2;variant++) for(int color=0;color<6;color++) {
                int[] under=WardrobeClothing.render(base,mask,cover,2,1,2,color,color,color,row,f);
                int[] actual=SockClothing.render(under,sock,variant); int delta=0;
                for(int i=0;i<base.length;i++) {
                    if((actual[i]>>>24)!=(base[i]>>>24)) throw new AssertionError("Alpha changed");
                    if(actual[i]!=under[i]) {
                        delta++;
                        if(locked[i]!=0) throw new AssertionError("Protected footwear or sole changed");
                        if(sock[i]==0 || (((sock[i]>>>16)&255)&(variant==1?1:2))==0) throw new AssertionError("Outside selected mask changed");
                        if(i/256<250 || ((mask[i]>>>16)&255)==3) throw new AssertionError("Identity or shoe changed");
                    }
                }
                if(delta<=20) throw new AssertionError("Unchanged new sock in cell"+row+"/"+f);
                cases++; changed+=delta;
                if(color==0) {
                    int[] simple=SockClothing.render(base,sock,variant);
                    if(row==1) Arrays.fill(simple,0,256*12,0);
                    sheets[variant-1].setRGB(f*256,row*384,256,384,simple,0,256);
                }
            }
            int[] mixed=WardrobeClothing.render(base,mask,cover,row==0?1:2,row==0?2:1,2,0,0,0,row,f);
            mixed=SockClothing.render(mixed,sock,row==0?1:2);
            if(row==1) Arrays.fill(mixed,0,256*12,0);
            sheets[2].setRGB(f*256,row*384,256,384,mixed,0,256);
            for(int item=2;item<=3;item++) if(row==0) {
                int[] actual=WardrobeClothing.render(base,mask,cover,0,item,0,0,0,0,row,f);
                int delta=0;for(int i=0;i<base.length;i++) if(actual[i]!=base[i]) {
                    delta++;if(((mask[i]>>>16)&255)!=2 || (actual[i]>>>24)!=(base[i]>>>24)) throw new AssertionError("Shorts escaped fabric");
                }
                if(delta<=20)throw new AssertionError("Unchanged new shorts");cases++;changed+=delta;
            }
            if(!Arrays.equals(base,original) || !Arrays.equals(sock,originalMask))throw new AssertionError("Inputs mutated");
        }
        for(int i=0;i<3;i++) ImageIO.write(sheets[i],"png",out.resolve(new String[]{"host-crew-16.png","host-knee-16.png","host-mixed-16.png"}[i]).toFile());
        System.out.println("{\"cases\":"+cases+",\"changedPixelsAcrossCases\":"+changed+",\"alphaPreserved\":true,\"maskAndInputPreserved\":true,\"identityAndShoesPreservedBySocks\":true,\"independentShoeProtectionPreserved\":true,\"androidExecution\":false,\"visualApproval\":\"pending\"}");
    }
}
