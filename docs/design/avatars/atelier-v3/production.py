from functools import lru_cache
from PIL import Image, ImageColor, ImageChops, ImageFilter
from paint import ASSET
from atelier import (RACE,NIGHT,TRAIL,TORSO,BOTTOM,SHOES,FOOT_TRIM,
    THIGH,SHELL_CUFFS,LEG_SEAMS,PALETTES,Pen,sprite_source,body_layer,body_details,
    head,shoes,tights,trail_side_pocket,empty,merged,use_alpha)

POSES=tuple((gender,frame) for gender in range(2) for frame in range(8))

# Each plate stays tied to coordinates in the approved eight-frame source.
ANCHORS=(
 ((150,145,110,154,142,252),(152,150,112,159,148,258),(144,145,104,154,140,257),(148,144,108,153,141,252),(156,144,116,153,150,252),(144,150,104,159,138,260),(146,150,106,159,140,258),(142,144,102,153,130,251)),
 ((156,129,118,136,142,233),(171,132,133,139,157,239),(159,131,121,138,149,235),(155,131,117,138,141,233),(156,130,118,137,145,233),(174,131,136,138,159,239),(156,130,118,137,147,238),(155,130,117,137,140,233)),
)
TORSO.update({
 (0,3):(112,180,184,253),(0,4):(121,180,193,254),(0,5):(107,185,176,257),(0,6):(106,187,178,259),(0,7):(98,181,176,255),
 (1,3):(108,165,186,233),(1,4):(112,166,188,235),(1,5):(122,169,196,236),(1,6):(117,166,188,237),(1,7):(108,165,185,235),
})
THIGH.update({
 (0,3):(136,266),(0,4):(145,266),(0,5):(133,279),(0,6):(132,279),(0,7):(125,266),
 (1,3):(136,247),(1,4):(141,248),(1,5):(150,257),(1,6):(142,253),(1,7):(136,248),
})
SHELL_CUFFS.update({
 (0,3):[[(60,225),(66,224),(76,229),(82,235)],[(200,225),(207,222),(212,226)]],
 (0,4):[[(69,223),(77,224),(85,229),(92,231)],[(204,225),(211,220),(216,225)]],
 (0,5):[[(111,237),(116,236),(124,240)],[(183,226),(188,223),(191,225)]],
 (0,6):[[(75,231),(83,231),(92,235)],[(185,233),(191,230),(199,234)]],
 (0,7):[[(45,221),(54,221),(62,228),(68,229)],[(186,222),(192,220),(198,226)]],
 (1,3):[[(65,208),(73,209),(81,215),(85,220)],[(200,209),(207,210),(214,214)]],
 (1,4):[[(70,210),(75,211),(82,216),(89,220)],[(204,208),(210,209),(215,214)]],
 (1,5):[[(119,217),(125,218),(132,224)],[(207,207),(213,210),(221,214)]],
 (1,6):[[(94,218),(101,219),(108,224)],[(199,214),(207,217),(213,219)]],
 (1,7):[[(63,209),(69,210),(77,216),(84,218)],[(201,208),(207,210),(212,213)]],
})
# Separate bounding guides prevent paint leak into the shoe or opposing limb.
BOTTOM.update({
 (0,3):[(95,237),(151,240),(176,258),(160,279),(133,293),(102,296),(88,282)],
 (0,4):[(107,239),(162,243),(185,260),(168,282),(140,292),(107,292),(98,273)],
 (0,5):[(91,247),(145,250),(179,275),(168,307),(130,309),(98,301)],
 (0,6):[(96,246),(149,249),(171,276),(161,301),(121,311),(99,298)],
 (0,7):[(90,235),(140,238),(171,256),(158,283),(120,294),(87,289),(79,277)],
 (1,3):[(97,212),(155,220),(180,245),(190,280),(208,297),(199,319),(176,327),(158,310),(135,274),(115,280),(94,293),(74,293),(68,275),(93,255)],
 (1,4):[(101,216),(156,222),(183,245),(192,282),(215,307),(204,324),(182,332),(164,312),(142,279),(114,293),(86,298),(72,284),(88,266)],
 (1,5):[(105,219),(168,225),(190,243),(203,268),(208,284),(190,298),(159,310),(151,336),(129,342),(118,323),(128,289),(108,274)],
 (1,6):[(100,219),(160,223),(181,242),(188,268),(191,288),(171,306),(159,335),(137,343),(121,334),(115,313),(118,288),(101,271)],
 (1,7):[(98,214),(150,221),(181,247),(193,278),(209,296),(203,323),(180,328),(157,307),(138,278),(109,292),(79,298),(71,280),(90,262)],
})
SHOES.update({
 (0,3):[[(12,275),(65,275),(70,313),(55,355),(12,353)],[(153,329),(237,327),(250,354),(227,385),(151,385)]],
 (0,4):[[(19,279),(72,279),(83,314),(64,357),(18,357)],[(158,328),(243,323),(255,350),(230,384),(154,385)]],
 (0,5):[[(70,303),(106,304),(120,331),(111,355),(72,353)],[(64,352),(125,348),(142,377),(130,384),(63,384)]],
 (0,6):[[(60,289),(104,286),(113,315),(100,357),(63,358),(52,332)],[(88,349),(150,346),(167,378),(157,386),(88,386)]],
 (0,7):[[(10,279),(65,277),(73,315),(54,355),(12,357)],[(145,312),(234,313),(249,336),(230,379),(149,380)]],
 (1,3):[[(19,254),(72,254),(79,286),(71,312),(30,336),(17,324)],[(156,297),(235,296),(250,322),(233,362),(157,362)]],
 (1,4):[[(20,258),(76,258),(83,289),(69,332),(22,340)],[(161,300),(239,300),(255,326),(236,366),(161,369)]],
 (1,5):[[(95,270),(135,269),(147,292),(137,335),(106,343),(98,325)],[(95,331),(156,327),(181,358),(165,373),(94,372)]],
 (1,6):[[(54,267),(95,266),(116,287),(107,327),(77,350),(59,327)],[(94,327),(160,329),(179,353),(169,375),(91,375)]],
 (1,7):[[(20,253),(70,255),(78,288),(68,324),(25,335),(18,317)],[(156,295),(236,295),(252,314),(236,363),(155,363)]],
})
FOOT_TRIM.update({
 (0,3):[[(31,312),(38,301),(47,294),(49,303),(41,316)],[(172,362),(187,353),(199,350),(213,343),(227,345)]],
 (0,4):[[(36,316),(43,305),(51,301),(56,309),(50,319)],[(176,357),(190,350),(202,345),(221,341),(238,343)]],
 (0,5):[[(82,327),(91,317),(97,316),(102,322),(99,332)],[(76,370),(86,367),(98,364),(111,365),(128,372)]],
 (0,6):[[(69,335),(75,324),(80,316),(88,313),(91,320)],[(102,373),(113,369),(126,368),(140,370),(150,374)]],
 (0,7):[[(27,321),(34,306),(42,299),(46,306),(39,322)],[(162,352),(178,346),(195,340),(209,331),(228,331)]],
 (1,3):[[(36,308),(44,295),(54,289),(57,296),(47,309)],[(172,341),(190,336),(207,332),(222,324),(237,324)]],
 (1,4):[[(41,313),(47,299),(55,293),(60,299),(52,312)],[(177,345),(195,337),(209,334),(225,329),(240,330)]],
 (1,5):[[(111,312),(119,302),(126,296),(129,305),(123,316)],[(113,355),(126,351),(139,349),(154,352),(168,356)]],
 (1,6):[[(69,315),(77,304),(85,297),(94,296),(95,305)],[(109,355),(122,351),(136,351),(150,355),(165,358)]],
 (1,7):[[(38,310),(44,296),(51,290),(57,296),(52,307)],[(175,335),(190,329),(205,325),(223,319),(237,321)]],
})
LEG_SEAMS.update({
 3:[[(143,289),(151,303),(157,314),(170,327)],[(72,303),(86,298),(101,293)]],
 4:[[(147,288),(152,300),(165,315),(174,329)],[(82,302),(92,299),(108,293)]],
 5:[[(157,286),(150,301),(135,310),(114,318)],[(95,339),(108,326)]],
 6:[[(154,285),(147,299),(130,309),(113,312)],[(124,334),(131,323)]],
 7:[[(134,288),(145,300),(155,310),(164,322)],[(69,302),(85,297),(100,293)]],
})

ITEMS={
 'headwear-7':(RACE,'headwear'),'headwear-8':(NIGHT,'headwear'),'headwear-9':(TRAIL,'headwear'),
 'top-10':(RACE,'top'),'top-11':(NIGHT,'top'),'top-12':(TRAIL,'top'),
 'bottom-9':(RACE,'bottom'),'bottom-10':(NIGHT,'bottom'),'bottom-11':(TRAIL,'bottom'),
 'shoes-8':(RACE,'shoes'),'shoes-9':(NIGHT,'shoes'),'shoes-10':(TRAIL,'shoes'),
 'hydration-7':(TRAIL,'hydration'),'waist-7':(RACE,'waist'),'eyewear-7':(NIGHT,'eyewear'),
}
ROLES={'top':1,'bottom':2,'shoes':3}
FABRIC=Image.open(ASSET/'running-kit-v1/fabric-mask.png').convert('RGBA')


def footwear_fabric(pose,style,source,starting):
 gender,frame=pose
 shape=FABRIC.crop((frame*256,gender*384,(frame+1)*256,(gender+1)*384))
 authorized=Image.new('L',(256,384));authorized.putdata([255 if r==3 and a else 0 for r,_,_,a in shape.get_flattened_data()])
 nearby=authorized.filter(ImageFilter.MaxFilter(11)).load()
 palette=[ImageColor.getcolor(c,'RGBA') for c in PALETTES[style]['shoe']]
 layer=starting.copy();target=layer.load();src=source.load();labeled=shape.load()
 for y in range(244,384):
  for x in range(256):
   if target[x,y][3]:continue
   rr,gg,bb,aa=src[x,y];role,shade,_,marked=labeled[x,y]
   if aa<234:continue
   if marked and role==3:
    target[x,y]=palette[min(3,shade//68)]
    continue
   if not nearby[x,y]:continue
   if gender==0:
    pigment=gg>65 and gg>rr*1.37 and bb>rr*1.12
    level=(gg>114)+(bb>92)+(bb>128)
   else:
    pigment=rr>100 and rr>gg*1.45 and rr>bb*1.45 and bb>=gg*.64
    level=(rr>130)+(gg>78)+(gg>122)
   if pigment:target[x,y]=palette[level]
 return layer


@lru_cache(maxsize=48)
def fitted_body(pose,style):
 source,coverage,hands=sprite_source(pose)
 body=body_layer(pose,style,source,coverage,hands)
 return source,coverage,hands,body,body_details(pose,style,body)



def alpha_union(images):
 mask=Image.new('L',(256,384))
 for image in images: mask=ImageChops.lighter(mask,image.getchannel('A'))
 return mask


def trail_short_sleeves(pose,source,coverage,hands):
 layer=empty(); target=layer.load(); src=source.load(); cov=coverage.load(); hand=hands.load()
 colors=[ImageColor.getcolor(c,'RGBA') for c in ('#4d3e2e','#6f553d','#966e46','#b58957','#c69762')]
 for y in range(167,232):
  for x in range(50,235):
   r,g,b,source_a=src[x,y]; flags,shade,_,a=cov[x,y]
   if a and flags&1 and source_a>=234 and not hand[x,y][3] and r>g*1.13 and g>b*1.12:
    target[x,y]=colors[(shade>84)+(shade>125)+(shade>166)+(shade>205)]
 return layer


def trail_tee(pose,top,sleeves):
 x0,y0,x1,y1=TORSO[pose]
 def fit(x,y): return round(x0+x*(x1-x0)/74),round(y0+y*(y1-y0)/74)
 layer=empty(); p=Pen(layer,fit)
 p.poly([(-35,4),(9,1),(25,9),(36,22),(50,23),(61,16),(78,10),(80,25),(66,34),(49,33),(37,32),(24,28),(12,30),(-12,37),(-29,31)],'#7a6b4e')
 p.poly([(-30,6),(6,2),(23,11),(32,19),(23,19),(7,22),(-13,26),(-27,25)],'#9f8c66')
 p.line([(-26,29),(-11,31),(9,27),(23,25),(32,27)],'#e1ae72',3)
 p.line([(47,29),(58,26),(70,19)],'#bd9465',2)
 p.line([(21,50),(17,59),(17,65)],'#6d503d',3)
 p.line([(19,68),(32,71),(53,68),(60,63)],'#765741',3)
 return use_alpha(layer,alpha_union((top,sleeves)))



def hydration(form,bottles):
 vest=form.copy();p=vest.load()
 sleeve={ImageColor.getcolor(c,'RGBA') for c in ('#b78a58','#d4a46d')}
 for y in range(160,265):
  for x in range(256):
   if p[x,y] in sleeve:p[x,y]=(0,0,0,0)
 return vest,bottles


def belt(pose,source,top,bottom,hands):
 g,f=pose;_,_,_,_,x,y=ANCHORS[g][f]; layer=empty()
 def local(u,v): return x+u,y+v
 p=Pen(layer,local)
 p.poly([(-33,-8),(-18,-11),(2,-8),(23,-9),(34,-12),(36,-4),(29,6),(9,9),(-10,7),(-24,4),(-34,0)],'#2a2638')
 p.poly([(-31,-6),(-19,-8),(0,-6),(19,-6),(32,-10),(32,-5),(27,3),(8,6),(-9,5),(-21,1),(-31,-1)],'#6b4c43')
 p.poly([(-14,-7),(3,-8),(17,-6),(24,-3),(22,5),(9,8),(-11,5),(-17,1)],'#8a5448')
 p.poly([(-11,-5),(2,-5),(16,-3),(19,-1),(16,5),(5,5),(-10,3)],'#ac7154')
 p.line([(-7,-3),(4,-3),(12,-1)],'#f2ca88',2)
 p.poly([(7,-9),(10,-14),(16,-17),(18,-14),(16,-5),(12,-2),(9,-4)],'#ad5a4a')
 p.line([(11,-13),(16,-14)],'#e6a16e',2)
 p.poly([(-21,-5),(-15,-7),(-12,-3),(-13,1),(-20,1)],'#d3b999')
 p.line([(-19,-4),(-15,-3)],'#665451',2)
 allowed=ImageChops.multiply(alpha_union((top,bottom)),ImageChops.invert(hands.getchannel('A')))
 return use_alpha(layer,allowed)


def shield(pose,source):
 g,f=pose;x,y,ex,ey,_,_=ANCHORS[g][f]; layer=empty();p=Pen(layer,lambda u,v:(x+u,y+v))
 p.line([(ex-x+3,ey-y-7),(-15,-8),(-9,-9)],'#202a3a',4)
 p.line([(ex-x+4,ey-y-8),(-13,-10),(-8,-10)],'#647e90',2)
 p.poly([(-12,-11),(6,-13),(24,-12),(36,-12),(48,-13),(59,-9),(58,-1),(53,7),(42,10),(36,4),(30,4),(22,8),(13,11),(0,11),(-7,7),(-11,0)],'#172638')
 p.poly([(-9,-8),(6,-10),(24,-9),(35,-9),(47,-10),(55,-7),(55,-2),(50,5),(42,7),(37,1),(29,0),(20,6),(10,8),(0,7),(-5,4),(-8,-1)],'#41657a')
 p.poly([(-8,-7),(4,-9),(18,-8),(14,-5),(4,0),(-6,1)],'#7b9d9c')
 p.poly([(40,-8),(50,-8),(54,-6),(52,-4),(43,0),(39,-3)],'#6e9398')
 p.line([(-7,-7),(3,-8),(12,-7)],'#c4d2ad',2)
 p.line([(6,5),(14,4),(18,2)],'#57919d',2)
 p.line([(40,-7),(48,-7)],'#c1cba7',2)
 p.line([(23,-8),(30,-8),(36,-8)],'#203b52',2)
 return use_alpha(layer,source.getchannel('A').point(lambda a: 255 if a>=234 else 0))


def shoe_details(pose,style,base):
 if style==RACE:
  ink=shoes(pose,style,base)
 elif style==NIGHT:
  ink=empty();q=Pen(ink)
  for points in FOOT_TRIM[pose]:
   q.line(points,'#2c4a61',7);q.line(points[1:4],'#aec9bc',4)
   q.line([(x,y+3) for x,y in points[2:]],'#5f8891',2)
 else:
  ink=empty();q=Pen(ink)
  for points in FOOT_TRIM[pose]:
   q.line(points,'#523e33',9);q.line(points[1:4],'#a6875c',3)
   for x,y in points[1:-1]:
    q.line([(x-4,y+2),(x+1,y-3)],'#d4b986',2)
   q.line([(x,y+8) for x,y in points[1:]],'#6d573f',4)
   for x,y in points[1:-1]: q.rect((x-2,y+7),(x+1,y+9),'#524031')
 return use_alpha(ink,base.getchannel('A'))


def asset(key,pose):
 style,slot=ITEMS[key]
 if slot=='headwear':
  gender,frame=pose
  eye_x,eye_y,_,_,_,_=ANCHORS[gender][frame]
  base_x,base_y,_,_,_,_=ANCHORS[gender][0]
  crown,bill=head(gender,style,(eye_x-base_x,eye_y-base_y))
  return [('Crown or band',crown),('Brim and underside',bill)]
 source,coverage,hands,body,parts=fitted_body(pose,style)
 top,bottom,feet,sleeves=body;form,details,cuffs,legs=parts
 if slot=='top':
  if style==TRAIL:
   short=trail_short_sleeves(pose,source,coverage,hands)
   return [('Top fabric',top),('Short sleeve',short),('Yoke and binding',trail_tee(pose,top,short))]
  return [('Top fabric',top),('Top panels and zip',form),('Race bib' if style==RACE else 'Reflective tape',details),('Shell sleeve',sleeves),('Cuff binding',cuffs)]
 if slot=='bottom':
  long,stitch=tights(pose,style,source,coverage)
  return [('Bottom fabric',bottom),('Compression tight',long),('Tight stitching',stitch),('Pocket or side seam',legs)]
 if slot=='shoes':
  feet=footwear_fabric(pose,style,source,feet)
  return [('Upper and sole',feet),('Lacing, heel and tread',shoe_details(pose,style,feet))]
 if slot=='hydration':
  vest,bottles=hydration(form,details)
  return [('Vest straps and pockets',vest),('Flasks and buckles',bottles),('Side mesh outside torso',trail_side_pocket(pose,TRAIL,source))]
 if slot=='waist':return [('Belt, pocket and gel',belt(pose,source,top,bottom,hands))]
 if slot=='eyewear':return [('Lens and frame',shield(pose,source))]
 raise ValueError(key)


def tint_cel(key,detail):
 style,slot=ITEMS[key];role=ROLES[slot]
 name={'top':'top','bottom':'short','shoes':'shoe'}[slot]
 colors=PALETTES[style][name]
 stops=[(c,round(i*255/(len(colors)-1))) for i,c in enumerate(colors)]
 if key=='bottom-10':stops.extend(zip(('#293a50','#354b63','#455f73','#607d8b'),(44,102,156,208)))
 if key=='top-12':stops.extend(zip(('#4d3e2e','#6f553d','#966e46','#b58957','#c69762'),(0,57,119,178,218)))
 palette={ImageColor.getcolor(c,'RGBA'):(role,value,0,255) for c,value in stops}
 out=empty();out.putdata([palette.get(pixel,(0,0,0,0)) for pixel in detail.get_flattened_data()]);return out
