package com.runmoni.app.avatar;

import java.util.Arrays;

/**
 * A small, deterministic software pixel-art renderer shared by Android and the
 * offline desktop exporter. All wearables attach to the same anatomical rig;
 * changing hair never swaps the face, neck or body. No Android/AWT dependency.
 */
public final class AvatarRasterizer {
    public static final int WIDTH = 96;
    public static final int HEIGHT = 112;
    public static final int FRAME_COUNT = 8;
    public static final int FRAME_MILLIS = 90;
    public static final int NECK_X = 48, NECK_Y = 51;
    public static final int HIP_X = 49, HIP_Y = 77;
    private static final int INK = 0xff172238;
    private static final int SKIN = 0xfff3b779, SKIN_LIGHT = 0xffffd497;
    private static final int SKIN_SHADE = 0xffd88d59, SKIN_DEEP = 0xffbb6e48;
    private static final int WHITE = 0xfffff5e1;
    // Indices 0..5 are catalogue garments; 6..10 are private costume palettes.
    private static final int[] TOP = {0xfff07867,0xff68c6b7,0xff638cb3,0xffe3b951,0xffdf7d8b,0xff81c6b4,0xffcf5260,0xff397d92,0xffed8b83,0xffa47c60,0xffe6be53};
    private static final int[] TOP_SHADE = {0xffca5159,0xff36988f,0xff416687,0xffbf843b,0xffa84d68,0xff489a8c,0xff9c354d,0xff25546d,0xffbf5f72,0xff765745,0xffb38b3d};
    private static final int[] TOP_LIGHT = {0xffffaa89,0xffa4e5ce,0xff98bad2,0xffffdb86,0xffffb2b3,0xffb9e4cf,0xfff68c88,0xff74b9bd,0xffffbeb0,0xffcfa77d,0xffffe390};
    private static final int[] BOTTOM = {0xff293b57,0xff27384e,0xff53756a,0xffae5c65,0xff303c57,0xff335568,0xffbd4d5d,0xff397d92,0xffed8b83,0xffa47c60,0xffe6be53};
    private static final int[] SHOE = {0xff6cc6ad,0xfff07c6d,0xff75a8d0,0xffe5c35e,0xff364153,0xffa47c60,0xffe6be53};
    private static final int[] SHOE_SHADE = {0xff358f89,0xffc64959,0xff487592,0xffb18a3d,0xff20283b,0xff765745,0xffb38b3d};
    // near knee x/y and ankle x/y relative to a fixed hip. Far leg is 4 phases opposite.
    private static final int[][] LEG = {
        {60,87,70,102}, {55,91,57,102}, {45,91,36,102}, {38,84,43,94},
        {39,80,28,90}, {48,81,38,91}, {59,81,48,92}, {64,85,65,99}
    };
    // Shoulder-relative elbow/wrist, with a roughly right-angle elbow throughout.
    // Phase zero: same-side leg reaches forward while this arm swings backward.
    private static final int[][] ARM = {
        {-10,11,-1,19}, {-7,13,3,18}, {-2,14,9,16}, {3,14,14,12},
        {6,12,15,7}, {3,14,14,12}, {-2,14,9,16}, {-7,13,3,18}
    };
    private static final int[] BOB = {0,1,0,-1,0,1,0,-1};

    private AvatarRasterizer() { }

    public static int frameAt(long elapsedMillis) {
        return (int) (Math.max(0L, elapsedMillis) / FRAME_MILLIS % FRAME_COUNT);
    }

    public static int bobForFrame(int frame, boolean running) {
        return running ? BOB[Math.floorMod(frame, FRAME_COUNT)] : 0;
    }

    /** Optional testing/export landmark: neck is filled underneath the common chin and collar. */
    public static int neckY(int frame, boolean running) { return NECK_Y + bobForFrame(frame, running); }

    /** Compatibility with the first 9-slot renderer and legacy head-accessory IDs. */
    public static int[] render(int hair, int top, int bottom, int shoes, int accessory,
            int eyewear, int wrist, int waist, int hydration, int frame, boolean running) {
        int old=variant(accessory);
        return render(hair,0,top,bottom,0,shoes,old==1?3:old==2?1:0,old==3?1:0,
                eyewear,wrist,waist,hydration,frame,running);
    }

    /** Twelve independent choices: tops/bottoms have six variants; all other slots have four. */
    public static int[] render(int hair, int expression, int top, int bottom, int socks,
            int shoes, int headwear, int ear, int eyewear, int wrist, int waist,
            int hydration, int frame, boolean running) {
        return render(hair,expression,top,bottom,socks,shoes,headwear,ear,eyewear,wrist,waist,hydration,0,frame,running);
    }

    /** Costume 0 normal, 1 Santa, 2 sport swimsuit, 3 athletic bikini, 4 bear, 5 original golden mascot. */
    public static int[] render(int hair, int expression, int top, int bottom, int socks,
            int shoes, int headwear, int ear, int eyewear, int wrist, int waist,
            int hydration, int costume, int frame, boolean running) {
        Raster r = new Raster();
        int h = variant(hair), t = Math.floorMod(top,6), b = Math.floorMod(bottom,6), s = variant(shoes);
        int a = variant(headwear), e = variant(eyewear), w = variant(wrist), sock=variant(socks), exp=variant(expression), ears=variant(ear);
        int belt = variant(waist), vest = variant(hydration), f = Math.floorMod(frame, FRAME_COUNT);
        int suit=Math.floorMod(costume,6);
        if(suit!=0){t=suit+5;b=suit+5;}
        if(suit==1||suit>=4){sock=3;s=suit==1?4:suit==4?5:6;a=0;}
        int dy = bobForFrame(f, running);

        // Hair behind the body cannot erase the common face or neck.
        hairBack(r, h, dy, running ? f : 0);
        if(suit>=4)hoodBack(r,suit,dy);
        leg(r,b,s,sock,running ? (f+4)%8 : -1,false,dy);
        arm(r,t,running ? (f+4)%8 : -1,false,dy,0);
        leg(r,b,s,sock,running ? f : -1,true,dy);

        // Torso turns toward screen-right; the face stays in the approved 3/4 view.
        // Project all torso wearables together so their attachment points stay shared.
        Raster body=running?new Raster():r;
        body.poly(INK,36,72+dy,58,72+dy,62,80+dy,54,84+dy,37,82+dy,34,78+dy);
        body.poly(BOTTOM[b],38,74+dy,57,74+dy,59,79+dy,52,81+dy,38,79+dy);
        body.line(39,76+dy,55,77+dy,2,shade(BOTTOM[b],1.2));
        torso(body,t,dy);
        if(suit==2||suit==3) swimBottom(body,suit,dy);
        if(b==4) {
            // High-waist bike shorts overlap a crop top's lower skin/base layer.
            body.poly(INK,37,70+dy,58,71+dy,59,78+dy,36,78+dy);
            body.poly(BOTTOM[b],39,72+dy,56,73+dy,57,77+dy,38,76+dy);
            body.line(40,72+dy,55,73+dy,1,shade(BOTTOM[b],1.5));
        }
        if(vest!=0) hydration(body,vest,dy);
        if(belt!=0) belt(body,belt,dy);
        if(running)r.projectTorso(body);
        arm(r,t,running ? f : -1,true,dy,w);

        // One permanent skin bridge, rendered after clothing, below the same face.
        r.rect(42,45+dy,12,13,INK);
        r.rect(44,46+dy,9,11,SKIN_SHADE);
        r.rect(47,47+dy,6,9,SKIN);
        r.poly(INK,39,55+dy,43,54+dy,47,57+dy,53,56+dy,56,53+dy,57,57+dy,52,60+dy,45,60+dy);
        r.line(44,56+dy,49,58+dy,1,TOP_LIGHT[t]);
        face(r,exp,dy);
        hairFront(r,h,dy);
        if(suit==1)santaHat(r,dy);
        if(suit>=4)hoodFront(r,suit,dy);
        if(a==1) headgear(r,2,dy);
        else if(a==2) visor(r,dy);
        else if(a==3) headgear(r,1,dy);
        if(ears!=0) earGear(r,ears,dy);
        if(e!=0) glasses(r,e,dy);
        if(running)r.leanForward();
        return r.pixels;
    }

    private static int variant(int value) { return Math.floorMod(value,4); }

    private static void leg(Raster r,int b,int s,int socks,int phase,boolean near,int dy) {
        int hx=near ? 52:43, hy=77+dy;
        int kx,ky,ax,ay;
        if(phase<0) { kx=near?54:42;ky=91+dy;ax=near?56:40;ay=103; }
        else { int[] p=LEG[phase];int offset=near?0:-7;kx=52+(int)Math.round((p[0]-52)*.85)+offset;ky=p[1]+dy;ax=52+(int)Math.round((p[2]-52)*.8)+offset;ay=p[3]+dy; }
        int pants=near?BOTTOM[b]:shade(BOTTOM[b],.77);
        int skin=near?SKIN:SKIN_SHADE;
        boolean longPants=b==1||b==6||b>=9,swim=b==7||b==8;
        r.line(hx,hy,kx,ky,11,INK);r.line(kx,ky,ax,ay,10,INK);
        r.line(hx,hy,kx,ky,7,longPants?pants:skin);r.line(kx,ky,ax,ay,6,longPants?pants:skin);
        if(!longPants&&!swim) {
            double length=b==4?.55:b==5?.30:2.0/3;
            int tx=hx+(int)Math.round((kx-hx)*length),ty=hy+(int)Math.round((ky-hy)*length);
            r.line(hx,hy,tx,ty,10,INK);r.line(hx,hy,tx,ty,7,pants);
            r.line(hx-2,hy+1,tx-2,ty,1,shade(pants,1.55));
            if(b!=4)r.line(tx-2,ty-1,tx+2,ty,1,WHITE);
            if(b==5){r.line(hx-3,hy+2,tx-3,ty,1,WHITE);r.line(tx-2,ty-2,tx,ty,2,INK);}
        } else if(longPants) {
            r.line(hx-2,hy+2,kx-2,ky,1,shade(pants,1.55));
            r.line(kx-2,ky,ax-2,ay-3,1,shade(pants,1.35));
        }
        if(near&&!longPants)r.line(kx+1,ky+2,ax+1,ay-3,2,SKIN_LIGHT);
        if(socks!=3) {
            double start=socks==0?.84:socks==1?.58:.12;
            int tx=kx+(int)Math.round((ax-kx)*start),ty=ky+(int)Math.round((ay-ky)*start);
            r.line(tx,ty,ax,ay,8,INK);r.line(tx,ty,ax,ay,5,near?WHITE:0xffb9c9c5);
            if(socks>0){r.line(tx-2,ty,tx+2,ty,1,0xff5eac9f);r.line(tx-2,ty+2,tx+2,ty+2,1,0xff718391);}
        }
        shoe(r,ax,ay,s,near);
    }

    private static void shoe(Raster r,int x,int y,int s,boolean near) {
        int col=near?SHOE[s]:shade(SHOE[s],.8),dark=near?SHOE_SHADE[s]:shade(SHOE_SHADE[s],.8);
        // Costume feet use the runner's shoe silhouette, with a small fabric trim.
        r.poly(INK,x-5,y-3,x+3,y-4,x+6,y-1,x+13,y,x+15,y+3,x+13,y+7,x-6,y+7,x-8,y+4);
        r.poly(col,x-4,y-2,x+2,y-2,x+5,y+1,x+11,y+1,x+12,y+3,x+9,y+4,x-5,y+4);
        r.poly(dark,x-6,y+1,x-2,y+1,x+1,y+4,x+10,y+4,x+11,y+5,x-5,y+5);
        r.line(x-5,y+5,x+12,y+5,2,WHITE);
        r.rect(x-5,y+7,16,1,INK);
        r.line(x+1,y,x+5,y+1,1,WHITE);r.line(x-1,y+2,x+3,y+3,1,WHITE);
        r.poly(WHITE,x+3,y+3,x+5,y+3,x+9,y+1,x+8,y+3,x+5,y+5);
        if(s>=5)r.line(x-3,y-2,x+2,y-2,2,TOP_LIGHT[s==5?9:10]);
    }

    private static void arm(Raster r,int top,int phase,boolean near,int dy,int wrist) {
        int sx=phase<0?(near?59:36):(near?55:47),sy=58+dy,ex,ey,wx,wy;
        if(phase<0) {ex=near?64:31;ey=72+dy;wx=near?64:29;wy=80+dy;}
        else {int[]p=ARM[phase];ex=sx+p[0];ey=sy+p[1];wx=sx+p[2];wy=sy+p[3];}
        int skin=near?SKIN:SKIN_SHADE;
        r.line(sx,sy,ex,ey,10,INK);r.line(ex,ey,wx,wy,9,INK);
        r.line(sx,sy,ex,ey,6,skin);r.line(ex,ey,wx,wy,5,skin);
        int color=near?TOP[top]:shade(TOP[top],.78);
        if(top==2||top==3||top==6||top>=9) {
            r.line(sx,sy,ex,ey,9,INK);r.line(ex,ey,wx,wy,8,INK);
            r.line(sx,sy,ex,ey,6,color);r.line(ex,ey,wx,wy,5,color);
            r.line(wx-2,wy,wx+2,wy,2,TOP_LIGHT[top]);
        } else if(top==1||top==5) {
            int tx=sx+(ex-sx)*2/5,ty=sy+(ey-sy)*2/5;
            r.line(sx,sy,tx,ty,11,INK);r.line(sx,sy,tx,ty,7,color);
            r.line(tx-2,ty,tx+2,ty,1,TOP_LIGHT[top]);
        }
        r.ellipse(wx-4,wy-4,9,9,INK);r.ellipse(wx-3,wy-3,7,7,skin);
        if(near) {r.rect(wx,wy-3,2,2,SKIN_LIGHT);r.rect(wx+2,wy+1,1,2,SKIN_SHADE);}
        if(near&&wrist!=0) {
            int band=wearColor(wrist);
            int cx=wx+(ex-wx)/3,cy=wy+(ey-wy)/3;
            r.line(cx-3,cy-1,cx+3,cy+1,4,INK);
            r.line(cx-3,cy-1,cx+3,cy+1,2,band);
            r.rect(cx-3,cy-3,6,6,INK);r.rect(cx-2,cy-2,4,4,0xffd9f2ec);
            r.rect(cx-1,cy-1,2,2,0xff315e70);
        }
    }

    private static void torso(Raster r,int t,int dy) {
        r.poly(INK,39,52+dy,54,52+dy,62,58+dy,59,75+dy,55,79+dy,36,77+dy,34,59+dy);
        if(t>=6){costumeTorso(r,t,dy);return;}
        if(t>=4) {
            // A continuous torso under both cropped garments prevents belly/waist seams.
            r.poly(SKIN_SHADE,40,54+dy,53,54+dy,59,59+dy,56,75+dy,38,75+dy,37,60+dy);
            r.poly(SKIN,43,55+dy,53,55+dy,57,60+dy,54,74+dy,41,73+dy,41,61+dy);
            r.line(44,69+dy,51,70+dy,1,SKIN_LIGHT);r.rect(48,72+dy,1,1,SKIN_SHADE);
            if(t==4) {
                // Adult athletic bra top: broad straps, opaque chest panel and support band.
                r.line(41,54+dy,40,61+dy,4,INK);r.line(54,54+dy,56,61+dy,4,INK);
                r.line(41,54+dy,40,61+dy,2,TOP[t]);r.line(54,54+dy,56,61+dy,2,TOP[t]);
                r.poly(INK,38,58+dy,45,59+dy,50,59+dy,57,57+dy,59,60+dy,57,67+dy,38,66+dy,36,62+dy);
                r.poly(TOP[t],39,60+dy,45,61+dy,51,61+dy,56,59+dy,57,61+dy,55,65+dy,39,64+dy,38,62+dy);
                r.line(40,64+dy,55,65+dy,2,TOP_SHADE[t]);r.line(40,60+dy,44,61+dy,1,TOP_LIGHT[t]);
                r.line(51,62+dy,53,61+dy,1,WHITE);
            } else {
                r.poly(TOP[t],40,54+dy,53,54+dy,59,59+dy,57,66+dy,53,69+dy,38,67+dy,37,60+dy);
                r.poly(TOP_SHADE[t],37,60+dy,40,61+dy,41,65+dy,55,66+dy,53,68+dy,38,66+dy);
                r.poly(TOP_LIGHT[t],43,55+dy,51,55+dy,53,60+dy,44,61+dy,40,59+dy);
                r.line(39,67+dy,53,68+dy,1,INK);r.line(51,61+dy,49,64+dy,1,WHITE);
            }
            return;
        }
        r.poly(TOP[t],40,54+dy,53,54+dy,59,59+dy,56,75+dy,38,75+dy,37,60+dy);
        r.poly(TOP_SHADE[t],37,59+dy,41,62+dy,40,72+dy,55,72+dy,56,75+dy,38,75+dy);
        r.poly(TOP_LIGHT[t],43,55+dy,51,55+dy,53,60+dy,45,62+dy,41,60+dy);
        r.line(42,69+dy,53,66+dy,1,TOP_LIGHT[t]);
        r.line(40,74+dy,54,74+dy,1,TOP_SHADE[t]);
        if(t==0) {
            r.line(39,55+dy,38,65+dy,1,WHITE);
            r.line(55,55+dy,58,61+dy,1,WHITE);
        }
        if(t==3) {r.line(49,58+dy,48,74+dy,1,INK);r.rect(49,63+dy,1,3,WHITE);r.line(40,70+dy,44,69+dy,1,INK);}
        else {r.line(51,61+dy,49,64+dy,1,WHITE);r.line(54,61+dy,52,64+dy,1,WHITE);}
    }

    private static void costumeTorso(Raster r,int t,int dy){
        if(t==7||t==8){
            r.poly(SKIN_SHADE,40,54+dy,53,54+dy,59,59+dy,56,75+dy,38,75+dy,37,60+dy);
            r.poly(SKIN,43,55+dy,53,55+dy,57,60+dy,54,74+dy,41,73+dy,41,61+dy);
            if(t==7){
                r.line(41,54+dy,40,63+dy,4,INK);r.line(54,54+dy,56,63+dy,4,INK);
                r.line(41,54+dy,40,63+dy,2,TOP[t]);r.line(54,54+dy,56,63+dy,2,TOP[t]);
                r.poly(INK,39,59+dy,45,61+dy,51,61+dy,57,59+dy,58,64+dy,55,76+dy,38,77+dy,37,65+dy);
                r.poly(TOP[t],40,61+dy,45,63+dy,51,63+dy,56,61+dy,56,65+dy,53,76+dy,40,75+dy,39,65+dy);
                r.poly(TOP_SHADE[t],39,65+dy,43,67+dy,43,75+dy,40,75+dy);
                r.line(50,65+dy,54,64+dy,1,TOP_LIGHT[t]);r.line(43,74+dy,53,69+dy,1,TOP_LIGHT[t]);
            }else{
                // Opaque athletic two-piece swimwear, adult and nonsexual.
                r.line(41,54+dy,40,61+dy,3,INK);r.line(54,54+dy,56,61+dy,3,INK);
                r.line(41,54+dy,40,61+dy,1,TOP[t]);r.line(54,54+dy,56,61+dy,1,TOP[t]);
                r.poly(INK,38,58+dy,46,60+dy,50,60+dy,57,58+dy,59,61+dy,56,66+dy,39,66+dy,36,62+dy);
                r.poly(TOP[t],39,60+dy,46,62+dy,50,62+dy,56,60+dy,56,63+dy,54,64+dy,40,64+dy,38,62+dy);
                r.line(40,64+dy,54,65+dy,1,TOP_SHADE[t]);r.line(40,60+dy,44,61+dy,1,TOP_LIGHT[t]);
                r.line(44,69+dy,51,70+dy,1,SKIN_LIGHT);r.rect(48,72+dy,1,1,SKIN_SHADE);
            }
            return;
        }
        r.poly(TOP[t],40,54+dy,53,54+dy,59,59+dy,56,75+dy,38,75+dy,37,60+dy);
        r.poly(TOP_SHADE[t],37,60+dy,41,62+dy,40,72+dy,55,72+dy,56,75+dy,38,75+dy);
        r.poly(TOP_LIGHT[t],43,55+dy,51,55+dy,53,60+dy,44,62+dy,40,60+dy);
        if(t==6){
            r.line(39,55+dy,45,58+dy,3,WHITE);r.line(51,58+dy,56,54+dy,3,WHITE);
            r.line(48,59+dy,48,74+dy,3,WHITE);r.line(38,75+dy,56,76+dy,3,WHITE);
            r.rect(37,70+dy,21,5,INK);r.rect(46,70+dy,6,5,0xffd9b25f);r.rect(48,71+dy,3,3,INK);
            r.rect(49,61+dy,1,1,INK);r.rect(49,66+dy,1,1,INK);
        }else{
            r.line(48,59+dy,48,74+dy,1,TOP_SHADE[t]);
            r.ellipse(40,61+dy,14,11,TOP_LIGHT[t]);
            if(t==10){
                r.ellipse(43,60+dy,10,10,INK);r.ellipse(44,61+dy,8,8,0xff63bda8);
                r.poly(WHITE,49,62+dy,46,65+dy,49,65+dy,46,68+dy,51,64+dy,48,64+dy);
            }else{
                r.line(45,63+dy,45,69+dy,1,TOP[t]);r.line(50,63+dy,50,69+dy,1,TOP[t]);
            }
        }
    }

    private static void swimBottom(Raster r,int suit,int dy){
        int c=TOP[suit+5];
        // Narrow the opaque lower garment over the shared hip roots, no new body rig.
        r.poly(INK,36,73+dy,58,73+dy,59,78+dy,52,80+dy,48,85+dy,43,84+dy,38,79+dy,35,78+dy);
        r.poly(c,38,75+dy,56,75+dy,56,77+dy,50,78+dy,47,82+dy,44,81+dy,40,77+dy,38,77+dy);
        r.line(39,75+dy,55,75+dy,1,TOP_LIGHT[suit+5]);
    }

    private static void hoodBack(Raster r,int suit,int dy){
        int t=suit+5;
        // Original round-eared hoods, with no licensed character face/ear/tail markings.
        r.ellipse(20,5+dy,14,15,INK);r.ellipse(22,7+dy,10,11,TOP[t]);
        r.ellipse(57,5+dy,14,15,INK);r.ellipse(59,7+dy,10,11,TOP[t]);
        r.ellipse(23,8+dy,7,8,TOP_SHADE[t]);r.ellipse(60,8+dy,7,8,TOP_SHADE[t]);
        r.ellipse(22,9+dy,48,48,INK);r.ellipse(24,11+dy,44,44,TOP[t]);
        r.poly(TOP_SHADE[t],25,25+dy,31,21+dy,35,48+dy,49,53+dy,39,54+dy,27,45+dy,24,36+dy);
    }

    private static void hoodFront(Raster r,int suit,int dy){
        int t=suit+5;
        r.line(28,25+dy,27,40+dy,3,INK);r.line(28,41+dy,35,48+dy,3,INK);
        r.line(35,49+dy,42,52+dy,3,INK);r.line(56,50+dy,63,43+dy,3,INK);
        r.line(65,25+dy,65,39+dy,3,INK);
        r.line(26,25+dy,25,38+dy,2,TOP_LIGHT[t]);r.line(27,43+dy,35,50+dy,2,TOP_LIGHT[t]);
        r.line(58,51+dy,65,43+dy,2,TOP_LIGHT[t]);r.line(67,26+dy,67,38+dy,2,TOP_LIGHT[t]);
        r.line(31,17+dy,41,12+dy,2,TOP_LIGHT[t]);r.line(41,12+dy,54,13+dy,2,TOP_LIGHT[t]);
    }

    private static void santaHat(Raster r,int dy){
        r.poly(INK,24,24+dy,27,14+dy,34,7+dy,49,3+dy,60,8+dy,68,16+dy,65,21+dy,55,15+dy,58,25+dy);
        r.poly(TOP[6],27,23+dy,30,15+dy,36,9+dy,48,6+dy,58,11+dy,65,17+dy,63,18+dy,55,12+dy,54,23+dy);
        r.poly(TOP_LIGHT[6],33,15+dy,38,10+dy,46,8+dy,40,13+dy,36,21+dy);
        r.poly(INK,25,23+dy,58,22+dy,62,25+dy,61,29+dy,25,30+dy,23,27+dy);
        r.poly(WHITE,26,25+dy,58,24+dy,60,26+dy,59,27+dy,26,28+dy);
        r.ellipse(62,16+dy,10,10,INK);r.ellipse(64,18+dy,6,6,WHITE);
    }

    private static void face(Raster r,int expression,int dy) {
        // Identical polygon, eyes and chin for every hairstyle and outfit.
        r.poly(INK,32,18+dy,43,13+dy,57,16+dy,65,25+dy,66,38+dy,61,47+dy,54,51+dy,42,51+dy,32,46+dy,27,36+dy,27,25+dy);
        r.poly(SKIN_SHADE,33,20+dy,43,15+dy,56,18+dy,63,26+dy,64,37+dy,59,45+dy,53,49+dy,42,49+dy,33,44+dy,29,35+dy,29,26+dy);
        r.poly(SKIN,37,20+dy,51,18+dy,61,24+dy,63,31+dy,62,39+dy,57,46+dy,48,48+dy,38,44+dy,34,35+dy);
        r.poly(SKIN_LIGHT,43,22+dy,56,22+dy,60,26+dy,60,29+dy,51,28+dy,43,30+dy,38,27+dy);
        // Ear overlaps the outline and sideburn, with an inset highlight.
        r.ellipse(25,31+dy,10,12,INK);r.ellipse(27,33+dy,7,8,SKIN_SHADE);
        r.rect(29,35+dy,3,4,SKIN);r.rect(29,35+dy,1,3,SKIN_DEEP);
        if(expression==2) {r.line(39,28+dy,45,30+dy,1,INK);r.line(56,30+dy,61,28+dy,1,INK);}
        else {r.line(40,29+dy,44,29+dy,1,INK);r.line(57,29+dy,60,29+dy,1,INK);}
        r.rect(39,32+dy,7,9,INK);r.rect(40,32+dy,2,6,WHITE);r.rect(43,34+dy,2,6,0xff293e54);r.rect(39,40+dy,6,1,SKIN_DEEP);
        r.rect(56,32+dy,6,8,INK);r.rect(57,32+dy,2,5,WHITE);r.rect(60,34+dy,1,5,0xff293e54);
        if(expression==1||expression==3){
            r.rect(55,31+dy,8,10,SKIN);r.line(56,36+dy,58,34+dy,1,INK);r.line(58,34+dy,61,36+dy,1,INK);
        }
        if(expression==1){r.rect(38,31+dy,9,11,SKIN);r.line(39,36+dy,42,34+dy,1,INK);r.line(42,34+dy,45,36+dy,1,INK);}
        r.poly(SKIN_SHADE,53,36+dy,55,39+dy,52,40+dy,51,39+dy);
        r.line(47,43+dy,50,45+dy,1,INK);r.line(50,45+dy,55,44+dy,1,INK);
        if(expression==1){r.poly(INK,46,42+dy,56,42+dy,54,46+dy,49,47+dy);r.line(48,43+dy,54,43+dy,1,WHITE);}
        if(expression==2){r.rect(46,42+dy,10,5,SKIN);r.line(48,44+dy,54,44+dy,1,INK);}
        r.rect(36,40+dy,4,2,0xffe9a173);r.rect(59,40+dy,3,2,0xffe9a173);
    }

    private static void hairBack(Raster r,int h,int dy,int phase) {
        int dark=h==1||h==3?0xff382d37:0xff1e2941;
        if(h==2) {
            int sway=(phase==2||phase==3)?-2:(phase==6||phase==7)?1:0;
            r.poly(INK,29,13+dy,22,9+dy,14,14+dy,11,24+dy,7,33+dy,10,43+dy,18,46+dy,15,53+dy,23,51+dy,29,41+dy,31,26+dy);
            r.poly(dark,26,15+dy,21,12+dy,17,16+dy,14+sway,29+dy,10+sway,35+dy,13+sway,41+dy,22,39+dy,20,47+dy,25,42+dy,29,26+dy);
            r.poly(0xff45536b,21,14+dy,24,16+dy,20+sway,26+dy,15+sway,31+dy,17+sway,22+dy);
            r.poly(0xff34445c,17+sway,34+dy,22,30+dy,22,38+dy,16+sway,42+dy);
            r.rect(25,16+dy,5,3,0xff64baa8);
        }
        if(h==3) {
            r.poly(INK,28,15+dy,38,9+dy,55,12+dy,63,22+dy,65,40+dy,69,47+dy,62,50+dy,57,44+dy,31,47+dy,25,44+dy,22,35+dy);
            r.poly(dark,29,17+dy,38,12+dy,54,15+dy,60,22+dy,62,40+dy,64,46+dy,59,44+dy,30,44+dy,26,41+dy,25,33+dy);
            r.line(26,26+dy,28,40+dy,2,0xff67504d);r.line(60,29+dy,62,44+dy,1,0xff67504d);
        }
    }

    private static void hairFront(Raster r,int h,int dy) {
        int base=h==1||h==3?0xff5a403c:0xff2e3c55;
        int mid=h==1||h==3?0xff805b48:0xff465570;
        int high=h==1||h==3?0xffab7b54:0xff66738a;
        if(h==0) {
            r.poly(INK,27,32+dy,22,26+dy,24,20+dy,21,19+dy,28,16+dy,26,11+dy,35,12+dy,37,6+dy,44,10+dy,50,5+dy,54,11+dy,61,9+dy,61,15+dy,67,17+dy,64,22+dy,66,27+dy,60,29+dy,57,23+dy,50,25+dy,46,22+dy,39,27+dy,34,25+dy,32,35+dy);
            r.poly(base,27,29+dy,25,24+dy,30,19+dy,28,17+dy,34,16+dy,32,13+dy,39,15+dy,39,10+dy,44,14+dy,49,10+dy,52,16+dy,59,13+dy,59,18+dy,63,19+dy,61,23+dy,61,25+dy,57,20+dy,50,23+dy,46,19+dy,38,24+dy,32,22+dy,30,31+dy);
            r.poly(mid,28,20+dy,36,16+dy,42,17+dy,36,21+dy,30,24+dy);
            r.poly(mid,42,13+dy,45,15+dy,52,14+dy,53,18+dy,47,18+dy,45,21+dy,40,20+dy);
            r.poly(high,34,16+dy,38,15+dy,39,17+dy,34,19+dy);
            r.rect(48,14+dy,3,2,high);r.line(55,17+dy,59,17+dy,1,mid);
        } else {
            r.poly(INK,26,34+dy,23,26+dy,25,18+dy,30,13+dy,36,10+dy,48,8+dy,58,13+dy,64,21+dy,64,28+dy,59,27+dy,55,21+dy,50,24+dy,45,23+dy,38,28+dy,33,28+dy,31,36+dy);
            r.poly(base,27,31+dy,26,25+dy,28,19+dy,33,15+dy,39,13+dy,47,11+dy,56,15+dy,61,22+dy,61,25+dy,56,18+dy,50,22+dy,45,21+dy,37,25+dy,31,25+dy,29,32+dy);
            r.poly(mid,29,21+dy,34,16+dy,40,14+dy,49,13+dy,52,15+dy,44,16+dy,38,21+dy,32,24+dy);
            r.poly(high,33,17+dy,38,15+dy,43,15+dy,38,17+dy,34,20+dy);
            r.line(46,17+dy,52,15+dy,1,mid);r.line(53,18+dy,59,22+dy,1,mid);
            if(h==1) {r.line(48,13+dy,45,21+dy,1,INK);r.line(50,14+dy,47,21+dy,1,base);}
            if(h==3) {r.poly(base,27,26+dy,32,26+dy,31,41+dy,28,44+dy,25,41+dy);r.line(27,30+dy,28,40+dy,1,mid);}
        }
    }

    private static void headgear(Raster r,int a,int dy) {
        if(a==1) {
            r.poly(INK,25,24+dy,60,20+dy,64,23+dy,63,28+dy,27,31+dy,24,28+dy);
            r.poly(0xff72d5bd,27,25+dy,60,22+dy,62,24+dy,61,26+dy,27,29+dy);
            r.line(29,25+dy,57,22+dy,1,0xffbef2d8);r.line(54,23+dy,53,26+dy,1,WHITE);
        } else if(a==2) {
            r.poly(INK,25,23+dy,26,16+dy,31,11+dy,43,8+dy,53,10+dy,60,17+dy,62,22+dy,70,24+dy,73,28+dy,68,31+dy,59,27+dy,27,29+dy);
            r.poly(WHITE,28,21+dy,29,17+dy,33,13+dy,43,11+dy,51,13+dy,57,18+dy,58,23+dy,29,26+dy);
            r.poly(0xffabc4cb,29,21+dy,40,18+dy,55,19+dy,58,23+dy,29,26+dy);
            r.poly(WHITE,42,12+dy,50,14+dy,53,18+dy,42,18+dy);
            r.poly(0xff4d6d80,57,24+dy,68,26+dy,69,28+dy,62,26+dy,29,28+dy,29,26+dy);
            r.line(48,13+dy,47,16+dy,1,INK);r.line(51,14+dy,50,17+dy,1,INK);
        } else {
            // Open-ear headset follows the ear and does not cover the face/eyes.
            r.line(27,28+dy,24,32+dy,3,INK);r.line(24,32+dy,26,42+dy,3,INK);
            r.line(27,28+dy,25,32+dy,1,0xff8bb9c5);r.line(25,32+dy,27,40+dy,1,0xff8bb9c5);
            r.rect(28,38+dy,5,4,INK);r.rect(29,39+dy,3,2,0xff81c9bc);
        }
    }

    private static void visor(Raster r,int dy) {
        r.poly(INK,25,23+dy,59,21+dy,62,24+dy,70,25+dy,73,29+dy,68,32+dy,59,28+dy,26,30+dy,24,27+dy);
        r.poly(WHITE,27,24+dy,58,23+dy,60,25+dy,28,28+dy);
        r.poly(0xff9fbdc6,59,25+dy,68,27+dy,69,29+dy,60,27+dy,28,29+dy,28,27+dy);
        r.line(51,24+dy,50,26+dy,1,INK);
    }

    private static void earGear(Raster r,int type,int dy) {
        if(type==1){headgear(r,3,dy);return;}
        if(type==2){
            r.ellipse(27,35+dy,5,6,INK);r.ellipse(28,36+dy,3,4,WHITE);
            r.line(29,38+dy,29,42+dy,2,WHITE);r.rect(29,42+dy,1,1,0xff8eb7ba);
            return;
        }
        // A thin behind-the-head sports headset remains separate from the hat slot.
        r.line(26,30+dy,23,27+dy,3,INK);r.line(23,27+dy,23,22+dy,3,INK);
        r.line(23,22+dy,28,18+dy,3,INK);
        r.line(26,30+dy,24,27+dy,1,0xff88c7bd);r.line(24,27+dy,24,23+dy,1,0xff88c7bd);
        r.ellipse(25,32+dy,8,11,INK);r.ellipse(27,34+dy,4,7,0xff709ba8);
        r.line(32,40+dy,36,42+dy,2,INK);r.rect(35,41+dy,3,2,0xff9bbebd);
    }

    private static void glasses(Raster r,int style,int dy) {
        int rim=wearColor(style);
        r.line(30,32+dy,62,31+dy,3,INK);
        r.poly(INK,36,30+dy,48,30+dy,49,38+dy,44,42+dy,37,39+dy);
        r.poly(INK,52,30+dy,64,30+dy,63,39+dy,58,41+dy,52,38+dy);
        r.poly(rim,38,32+dy,46,32+dy,46,37+dy,43,39+dy,39,37+dy);
        r.poly(rim,54,32+dy,62,32+dy,61,37+dy,58,39+dy,54,37+dy);
        r.line(39,33+dy,44,33+dy,1,0xffe6f5ed);r.line(55,33+dy,59,33+dy,1,0xffe6f5ed);
        r.line(48,33+dy,52,33+dy,1,rim);
    }

    private static void belt(Raster r,int style,int dy) {
        int c=wearColor(style);
        r.poly(INK,35,72+dy,58,73+dy,59,80+dy,37,79+dy,34,77+dy);
        r.poly(c,37,74+dy,56,75+dy,57,78+dy,38,77+dy);
        r.rect(43,74+dy,10,6,INK);r.rect(44,75+dy,8,3,shade(c,.7));
        r.line(45,75+dy,50,75+dy,1,WHITE);r.rect(50,76+dy,1,2,0xffb8c8ce);
    }

    private static void hydration(Raster r,int style,int dy) {
        int c=style==1?0xff33465b:wearColor(style);
        r.poly(INK,36,54+dy,42,54+dy,43,69+dy,40,74+dy,34,70+dy);
        r.poly(c,37,56+dy,40,56+dy,41,68+dy,39,71+dy,36,69+dy);
        r.poly(INK,53,54+dy,58,55+dy,60,69+dy,56,74+dy,51,71+dy);
        r.poly(c,54,56+dy,57,57+dy,58,68+dy,55,71+dy,53,69+dy);
        r.line(41,61+dy,53,62+dy,2,INK);r.line(42,62+dy,52,63+dy,1,0xffa9bfc5);
        r.line(41,68+dy,53,69+dy,1,INK);
        for(int x:new int[]{37,54}) {
            r.rect(x,60+dy,5,10,INK);r.rect(x+1,61+dy,3,7,0xff8bb7ca);
            r.rect(x+1,58+dy,2,4,WHITE);r.rect(x+1,63+dy,3,3,0xffd4e6e6);
        }
    }

    private static int wearColor(int style) {
        return style==1?0xff65c7b4:style==2?0xffed8b78:0xff759cb8;
    }

    private static int shade(int c,double amount) {
        return 0xff000000|Math.min(255,(int)(((c>>>16)&255)*amount))<<16|
                Math.min(255,(int)(((c>>>8)&255)*amount))<<8|Math.min(255,(int)((c&255)*amount));
    }

    private static final class Raster {
        final int[] pixels=new int[WIDTH*HEIGHT];
        void projectTorso(Raster body) {
            for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
                int c=body.pixels[y*WIDTH+x];
                if((c>>>24)!=0)put(HIP_X+(int)Math.round((x-HIP_X)*.78),y,c);
            }
        }
        void leanForward() {
            int[] upright=pixels.clone();Arrays.fill(pixels,0);
            for(int y=0;y<HEIGHT;y++) {
                int shift=(int)Math.round(Math.max(0,77-y)*.07);
                for(int x=0;x<WIDTH;x++)if((upright[y*WIDTH+x]>>>24)!=0)put(x+shift,y,upright[y*WIDTH+x]);
            }
        }
        void put(int x,int y,int c) {if(x>=0&&x<WIDTH&&y>=0&&y<HEIGHT)pixels[y*WIDTH+x]=c;}
        void rect(int x,int y,int w,int h,int c) {
            for(int yy=Math.max(0,y);yy<Math.min(HEIGHT,y+h);yy++)
                Arrays.fill(pixels,yy*WIDTH+Math.max(0,x),yy*WIDTH+Math.max(0,Math.min(WIDTH,x+w)),c);
        }
        void ellipse(int x,int y,int w,int h,int c) {
            double rx=w/2.0,ry=h/2.0,cx=x+rx,cy=y+ry;
            for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)
                if(Math.pow((xx+.5-cx)/rx,2)+Math.pow((yy+.5-cy)/ry,2)<=1)put(xx,yy,c);
        }
        void line(int x1,int y1,int x2,int y2,int width,int c) {
            double dx=x2-x1,dy=y2-y1,len=dx*dx+dy*dy,rad=width/2.0;
            for(int y=(int)Math.floor(Math.min(y1,y2)-rad);y<=Math.ceil(Math.max(y1,y2)+rad);y++)
                for(int x=(int)Math.floor(Math.min(x1,x2)-rad);x<=Math.ceil(Math.max(x1,x2)+rad);x++){
                    double t=len==0?0:Math.max(0,Math.min(1,((x-x1)*dx+(y-y1)*dy)/len));
                    double ex=x-(x1+t*dx),ey=y-(y1+t*dy);
                    if(ex*ex+ey*ey<=rad*rad)put(x,y,c);
                }
        }
        void poly(int c,int... points) {
            int n=points.length/2,minX=WIDTH,minY=HEIGHT,maxX=0,maxY=0;
            for(int i=0;i<n;i++){minX=Math.min(minX,points[i*2]);maxX=Math.max(maxX,points[i*2]);minY=Math.min(minY,points[i*2+1]);maxY=Math.max(maxY,points[i*2+1]);}
            for(int y=Math.max(0,minY);y<=Math.min(HEIGHT-1,maxY);y++)for(int x=Math.max(0,minX);x<=Math.min(WIDTH-1,maxX);x++){
                boolean inside=false;double xx=x+.5,yy=y+.5;
                for(int i=0,j=n-1;i<n;j=i++){
                    double xi=points[i*2],yi=points[i*2+1],xj=points[j*2],yj=points[j*2+1];
                    if((yi>yy)!=(yj>yy)&&xx<(xj-xi)*(yy-yi)/(yj-yi)+xi)inside=!inside;
                }
                if(inside)put(x,y,c);
            }
        }
    }
}
