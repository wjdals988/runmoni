import com.runmoni.app.avatar.AvatarRasterizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Checks the original face/neck regression against the actual candidate pixels. */
public class VerifyMotion {
    static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    static void connected(int[] pixels,int start,int goal){
        boolean[] seen=new boolean[pixels.length];int[] queue=new int[pixels.length];
        require((pixels[start]>>>24)!=0,"Transparent face landmark");
        int read=0,end=1;queue[0]=start;seen[start]=true;
        while(read<end){int p=queue[read++];if(p==goal)return;
            int x=p%96;int[] next={p-96,p+96,x>0?p-1:-1,x<95?p+1:-1};
            for(int q:next)if(q>=0&&q<pixels.length&&!seen[q]&&(pixels[q]>>>24)!=0){seen[q]=true;queue[end++]=q;}
        }
        throw new AssertionError("Disconnected face and torso");
    }
    public static void main(String[] args){
        int cases=0;
        for(int hair=0;hair<4;hair++)for(int top=0;top<6;top++)for(int suit=0;suit<6;suit++)for(int f=0;f<8;f++){
            int[] p=AvatarRasterizer.render(hair,0,top,0,0,0,0,0,0,0,0,0,suit,f,true);
            int bob=AvatarRasterizer.bobForFrame(f,true);
            for(int y=46;y<=58;y++)require((p[(y+bob)*96+48]>>>24)!=0,"Neck hole h="+hair+" t="+top+" c="+suit+" f="+f);
            connected(p,(40+bob)*96+50,(65+bob)*96+48);
            for(int x=0;x<96;x++){require(p[x]==0,"Top clipping");require(p[111*96+x]==0,"Bottom clipping");}
            for(int y=0;y<112;y++){require(p[y*96]==0,"Left clipping");require(p[y*96+95]==0,"Right clipping");}
            cases++;
        }
        for(int suit=0;suit<6;suit++){
            Set<Integer> frames=new HashSet<>();
            for(int f=0;f<8;f++)frames.add(Arrays.hashCode(AvatarRasterizer.render(2,0,0,0,0,0,0,0,0,0,0,0,suit,f,true)));
            require(frames.size()==8,"Repeated frame costume="+suit);
        }
        System.out.println("PASS: "+cases+" neck/connectivity/bounds cases; 6 costumes each have 8 distinct pixel frames.");
    }
}
