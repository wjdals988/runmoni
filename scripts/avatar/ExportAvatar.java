import com.runmoni.app.avatar.AvatarRasterizer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Desktop-only export of exactly the same pixel renderer used in the app. */
public final class ExportAvatar {
    private static BufferedImage image(int h,int t,int b,int s,int a,int e,int w,int belt,int vest,int f,boolean running){
        BufferedImage out=new BufferedImage(AvatarRasterizer.WIDTH,AvatarRasterizer.HEIGHT,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,out.getWidth(),out.getHeight(),AvatarRasterizer.render(h,t,b,s,a,e,w,belt,vest,f,running),0,out.getWidth());
        return out;
    }
    private static BufferedImage activeImage(int h,int expression,int t,int b,int socks,int s,
            int headwear,int ear,int e,int w,int belt,int vest,int f,boolean running){
        BufferedImage out=new BufferedImage(AvatarRasterizer.WIDTH,AvatarRasterizer.HEIGHT,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,out.getWidth(),out.getHeight(),AvatarRasterizer.render(h,expression,t,b,socks,s,headwear,ear,e,w,belt,vest,f,running),0,out.getWidth());
        return out;
    }
    private static BufferedImage costumeImage(int h,int expression,int costume,int frame,boolean running){
        BufferedImage out=new BufferedImage(AvatarRasterizer.WIDTH,AvatarRasterizer.HEIGHT,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,out.getWidth(),out.getHeight(),AvatarRasterizer.render(h,expression,4,4,2,1,0,0,0,0,0,0,costume,frame,running),0,out.getWidth());
        return out;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args.length==0?"work/avatar-v2":args[0]);dir.mkdirs();
        int scale=4,cw=96*scale,ch=112*scale;
        BufferedImage board=new BufferedImage(cw*4,ch*2+80,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=board.createGraphics();g.setColor(new Color(0xfff8ef));g.fillRect(0,0,board.getWidth(),board.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x172238));g.setFont(new Font("SansSerif",Font.BOLD,25));g.drawString("RUNMONI  /  SHARED RIG + INDEPENDENT GEAR",24,36);
        for(int h=0;h<4;h++)for(int row=0;row<2;row++){
            int t=(h+row)%4,b=(h+row)%4,s=(h+row)%4;
            BufferedImage im=image(h,t,b,s,row==0?0:2,row,row,row,row,0,false);
            g.drawImage(im,h*cw,row*ch+55,cw,ch,null);
        }
        g.dispose();ImageIO.write(board,"png",new File(dir,"runner-presets-v2.png"));
        BufferedImage atlas=new BufferedImage(96*8,112*4,BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag=atlas.createGraphics();
        for(int row=0;row<4;row++)for(int f=0;f<8;f++)
            ag.drawImage(image(row,row,row,row,row==3?2:0,row==3?1:0,row==3?1:0,row==3?1:0,row==3?1:0,f,true),f*96,row*112,null);
        ag.dispose();ImageIO.write(atlas,"png",new File(dir,"running-atlas-v2.png"));
        for(int h=0;h<4;h++)ImageIO.write(image(h,0,0,0,0,0,0,0,0,0,false),"png",new File(dir,"standing-hair-"+h+".png"));
        // New activewear sheet: all garments and gear are available to every body/hair preset.
        int[][] active={
            {2,0,4,4,2,1,0,0,0,1,0,0},
            {3,1,5,4,1,0,2,1,0,1,0,0},
            {0,0,0,5,0,2,3,2,0,1,0,0},
            {2,2,4,4,2,1,1,3,1,1,1,1}
        };
        String[] names={"BRA TOP + BIKER + KNEE SOCKS","CROPPED TECH TEE + BIKER","SINGLET + SPLIT SHORTS","CAP + EAR + ALL RUNNING GEAR"};
        BufferedImage activeBoard=new BufferedImage(cw*4,ch*2+100,BufferedImage.TYPE_INT_ARGB);
        Graphics2D a=activeBoard.createGraphics();a.setColor(new Color(0xfff8ef));a.fillRect(0,0,activeBoard.getWidth(),activeBoard.getHeight());
        a.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        a.setColor(new Color(0x172238));a.setFont(new Font("SansSerif",Font.BOLD,24));a.drawString("RUNMONI  /  ACTIVEWEAR + 12 INDEPENDENT CHOICES",24,35);
        for(int col=0;col<4;col++){
            int[]p=active[col];
            for(int row=0;row<2;row++){
                BufferedImage im=activeImage(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9],p[10],p[11],row==0?0:3,row==1);
                a.drawImage(im,col*cw,row*ch+60,cw,ch,null);
            }
            a.setFont(new Font("SansSerif",Font.PLAIN,16));a.drawString(names[col],col*cw+10,activeBoard.getHeight()-12);
        }
        a.dispose();ImageIO.write(activeBoard,"png",new File(dir,"activewear-presets-v2.png"));
        BufferedImage activeAtlas=new BufferedImage(96*8,112*4,BufferedImage.TYPE_INT_ARGB);
        Graphics2D aa=activeAtlas.createGraphics();
        for(int row=0;row<4;row++)for(int f=0;f<8;f++){
            int[]p=active[row];
            aa.drawImage(activeImage(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9],p[10],p[11],f,true),f*96,row*112,null);
        }
        aa.dispose();ImageIO.write(activeAtlas,"png",new File(dir,"activewear-running-atlas-v2.png"));
        int ccw=96*3,cch=112*3;
        String[] costumeNames={"NORMAL ACTIVEWEAR","SANTA RUN","SPORT SWIMSUIT","ATHLETIC BIKINI","BEAR PLUSH","GOLDEN RUN MASCOT"};
        BufferedImage costumes=new BufferedImage(ccw*6,cch*2+95,BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg=costumes.createGraphics();cg.setColor(new Color(0xfff8ef));cg.fillRect(0,0,costumes.getWidth(),costumes.getHeight());
        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        cg.setColor(new Color(0x172238));cg.setFont(new Font("SansSerif",Font.BOLD,23));cg.drawString("RUNMONI  /  SPECIAL FUN COSTUMES - SAME FACE, NECK AND 8-FRAME RIG",20,33);
        BufferedImage costumeAtlas=new BufferedImage(96*8,112*6,BufferedImage.TYPE_INT_ARGB);
        Graphics2D ca=costumeAtlas.createGraphics();
        for(int suit=0;suit<6;suit++){
            int hair=suit==1?0:suit==2?3:suit==4?1:2;
            for(int row=0;row<2;row++)cg.drawImage(costumeImage(hair,suit%4,suit,row==0?0:3,row==1),suit*ccw,row*cch+50,ccw,cch,null);
            cg.setFont(new Font("SansSerif",Font.PLAIN,15));cg.drawString(costumeNames[suit],suit*ccw+8,costumes.getHeight()-10);
            for(int f=0;f<8;f++)ca.drawImage(costumeImage(hair,suit%4,suit,f,true),f*96,suit*112,null);
        }
        cg.dispose();ca.dispose();ImageIO.write(costumes,"png",new File(dir,"costume-presets-v2.png"));ImageIO.write(costumeAtlas,"png",new File(dir,"costume-running-atlas-v2.png"));
        System.out.println("Exported exact app raster: "+AvatarRasterizer.WIDTH+"x"+AvatarRasterizer.HEIGHT+", 8 frames, "+dir.getCanonicalPath());
    }
}
