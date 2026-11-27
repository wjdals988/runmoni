from pathlib import Path
from PIL import Image,ImageDraw,ImageChops,ImageFilter,ImageColor

HERE=Path(__file__).resolve().parent
ROOT=next(p for p in HERE.parents if (p/"app/src/main/assets/avatars").is_dir())
ASSET=ROOT/'app/src/main/assets/avatars'
SOURCE=Image.open(ASSET/'reference-review/approved-running.png').convert('RGBA')
COVER=Image.open(ASSET/'wardrobe-v1/coverage.png').convert('RGBA')
HANDS=Image.open(ASSET/'wardrobe-v1/hand-protection.png').convert('RGBA')
SIZE=(256,384)
INK='#12172a'
FOOT_TRIM={
 (0,0):[[(32,321),(42,307),(48,305),(49,312),(42,326)],[(173,359),(186,354),(196,349),(209,345),(224,344)]],
 (0,1):[[(90,339),(95,334),(102,341),(103,351),(110,357)],[(124,375),(132,373),(141,369),(152,370),(165,375)]],
 (1,0):[[(42,292),(46,281),(52,278),(55,286),(49,298)],[(178,341),(193,337),(204,333),(222,328),(234,328)]],
 (1,1):[[(107,305),(112,294),(122,293),(126,304),(121,313)],[(119,353),(130,349),(141,350),(152,352),(164,353)]],
}
RACE='race'; NIGHT='night'; TRAIL='trail'
STYLES=(RACE,NIGHT,TRAIL)

# The original canvas, body edges, and eye/ear/hand pixels stay in the bottom layer.
# Coordinates are fitted separately to the stride and cross-body pose in each row.
TORSO={
 (0,0):(115,181,186,255), (0,1):(117,186,184,257),
 (1,0):(110,165,186,233), (1,1):(120,168,196,235),
}
BOTTOM={
 (0,0):[(97,238),(152,242),(182,258),(163,286),(130,302),(93,292)],
 (0,1):[(107,248),(158,251),(176,277),(160,304),(121,313),(102,288)],
 (1,0):[(99,215),(151,221),(180,245),(190,280),(210,300),(206,324),(177,332),(159,311),(139,279),(112,292),(79,300),(70,273),(93,258)],
 (1,1):[(103,221),(163,224),(190,241),(199,265),(205,278),(189,294),(153,305),(145,326),(133,341),(117,336),(112,315),(127,286),(110,277),(102,251)],
}
SHOES={
 (0,0):[[(20,287),(60,278),(78,312),(62,362),(18,365)],[(156,329),(233,324),(247,350),(220,390),(156,385)]],
 (0,1):[[(77,312),(104,318),(132,365),(129,389),(87,389),(79,370)],[(124,348),(169,349),(180,384),(124,390)]],
 (1,0):[[(23,256),(71,256),(77,296),(66,337),(21,337)],[(161,302),(233,295),(249,330),(238,369),(168,371)]],
 (1,1):[[(92,272),(122,271),(141,291),(134,333),(112,347),(96,336)],[(104,332),(161,327),(177,364),(151,378),(101,377)]],
}
SHELL_CUFFS={
 (0,0):[[(59,226),(64,223),(77,229),(87,238)],[(202,228),(206,224),(211,232)]],
 (0,1):[[(122,234),(130,237),(133,241)],[(191,225),(199,228),(202,235)]],
 (1,0):[[(66,209),(72,208),(83,216),(88,221)],[(205,208),(210,212),(216,218)]],
 (1,1):[[(102,219),(111,221),(116,227)],[(203,213),(211,218),(215,224)]],
}

PALETTES={
 RACE:{'top':('#103d50','#126975','#229c9e','#59d3b7','#a9e9ce'), 'short':('#151b38','#1d2e54','#2a4167','#42678c'), 'sole':('#b3d1c6','#edf5dc'), 'shoe':('#213e51','#25717c','#37a08c','#79dab3')},
 NIGHT:{'top':('#272044','#473560','#6d5487','#8f74a5','#be9ac3'), 'short':('#151829','#211e35','#302e48','#57516b'), 'sole':('#b0a2bd','#e4dbeb'), 'shoe':('#2b2142','#453257','#68507f','#a382aa')},
 TRAIL:{'top':('#52302e','#733f34','#a9694a','#ca8e62','#efb973'), 'short':('#222c29','#35433a','#4d5c44','#748364'), 'sole':('#91785a','#dbbe85'), 'shoe':('#2d352c','#445746','#627859','#9eae71')},
}

def rgba(color):
 return ImageColor.getcolor(color,'RGBA')

def empty():
 return Image.new('RGBA',SIZE)

def body_layer(pose,style,source,cover,hands):
 palette=PALETTES[style]; gender,frame=pose
 top,legs,feet,arm=empty(),empty(),empty(),empty()
 trousers=Image.new('1',SIZE); ImageDraw.Draw(trousers).polygon(BOTTOM[pose],fill=1)
 trainers=Image.new('1',SIZE)
 for coords in SHOES[pose]: ImageDraw.Draw(trainers).polygon(coords,fill=1)
 tp,lp,fp,ap=(layer.load() for layer in (top,legs,feet,arm))
 sleeve_mask=cover.getchannel('A').filter(ImageFilter.MaxFilter(3)).load()
 trou,shoe,base,cm,hand=(layer.load() for layer in (trousers,trainers,source,cover,hands))
 for y in range(160,384):
  for x in range(256):
   r,g,b,a=base[x,y]
   if a<234:
    continue
   cov,shade,_,cov_a=cm[x,y]
   long=style==NIGHT and (cov&2 or (not cov_a and sleeve_mask[x,y] and y<227+7*(gender==0))) and not hand[x,y][3]
   shirt=(r>86 and r>g*1.4 and g<b*1.83 and r>b*1.41) if gender==0 else (g>58 and g>r*1.75 and b>r*1.2)
   white=r>118 and abs(r-g)<35 and abs(g-b)<34
   x0,y0,x1,y1=TORSO[pose]
   if y0<=y<=y1 and x0-20<=x<=x1+4 and shirt and not hand[x,y][3]:
    if gender==0: level=0 if r<165 else 1 if g<62 else 2 if g<101 else 3 if g<159 else 4
    else: level=0 if g<81 else 1 if g<140 else 2 if g<182 else 3 if g<215 else 4
    tp[x,y]=rgba(palette['top'][level])
   elif y0<=y<=y1 and x0<=x<=x1 and white and gender==0 and not hand[x,y][3] and (x<x1-20):
    tp[x,y]=rgba(palette['top'][3])
   if long and r>g*1.12 and g>b*1.16 and r>76:
    level=1+(shade>=130)+(shade>=202)
    if x<x0: level=max(0,level-1)
    ap[x,y]=rgba(palette['top'][level])
   chroma=b-r
   if trou[x,y] and not hand[x,y][3]:
    is_fabric=(chroma>8 and 15<=r<100 and g>=r-8)
    if is_fabric or white:
     level=2 if white else 0 if r<21 else 1 if r<35 else 2 if r<51 else 3
     if style==RACE and gender==1:
      distal=y> (291-0.27*(x-150) if frame==0 else 293+0.32*(x-150))
      if distal: level=max(0,level-1)
     lp[x,y]=rgba(palette['short'][level])
   if shoe[x,y]:
    is_upper=(g>r*1.42 and b>r*1.14 and g>62) if gender==0 else (r>85 and r>g*1.45 and r>b*1.45)
    if is_upper:
     max_light=g if gender==0 else r
     mid=b if gender==0 else g
     level=0 if max_light<124 else 1 if mid<78 else 2 if mid<128 else 3
     fp[x,y]=rgba(palette['shoe'][level])
    elif white and (r+g+b)>410:
     fp[x,y]=rgba(palette['sole'][int(r>165 and g>165 and b>165)])
 return top,legs,feet,arm


def footwear_detail(pose,style,feet):
 layer=empty(); p=Pen(layer)
 for line in FOOT_TRIM[pose]:
  if style==RACE:
   p.line(line,'#256c73',5)
   p.line(line[1:-1],'#b5e5c5',2)
  elif style==NIGHT:
   p.line(line,'#4b3759',5)
   p.line(line[1:3],'#e8b9c5',2)
   p.line(line[3:5],'#b78fa8',2)
  else:
   p.line(line,'#3b4a3b',8)
   p.line(line[1:4],'#a0a76e',2)
   p.line([(x,y+3) for x,y in line[2:5]],'#677752',2)
 return use_alpha(layer,feet.getchannel('A'))

class Pen:
 def __init__(self,layer,transform=None):
  self.draw=ImageDraw.Draw(layer); self.transform=transform or (lambda x,y:(x,y))
 def coords(self,points):
  return [self.transform(x,y) for x,y in points]
 def poly(self,points,fill):
  self.draw.polygon(self.coords(points),fill=fill)
 def line(self,points,fill,width=1):
  self.draw.line(self.coords(points),fill=fill,width=width,joint='curve')
 def rect(self,a,b,fill):
  self.draw.rectangle((*self.transform(*a),*self.transform(*b)),fill=fill)

def use_alpha(layer,mask):
 result=layer.copy(); result.putalpha(ImageChops.multiply(layer.getchannel('A'),mask)); return result

def merged(layers):
 out=empty()
 for _,im in layers:
  out.alpha_composite(im)
 return out
