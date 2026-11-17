-- Own fixed-frame drawing recipes, executed by Aseprite. No body/image generation.
local input=assert(app.params.input)
local output=assert(app.params.output)
local original=assert(app.params.original)
local data=dofile(input..'/references.lua')
-- Bounded CLI batches may export one physical slot at a time. Drawing recipes
-- and pose references remain identical; separate processes release editor memory.
local onlySlot=app.params.slot
if onlySlot then
 local subset={}
 for _,style in ipairs(data.styles) do
  if style.slot==onlySlot then table.insert(subset,style) end
 end
 assert(#subset==3,'Expected exactly three authored styles in the selected slot')
 data.styles=subset
end
local originalSheet=Image{fromFile=original}
local W,H=256,384
local pc=app.pixelColor
local palettes={
 {0x155563,0x187b85,0x21a3a8,0x43c5bf,0x89e1d2},
 {0x932f49,0xc5465a,0xf36870,0xff9290,0xffc3af},
 {0x14377b,0x205ab5,0x3487ec,0x68acff,0xb1d7ff},
 {0x49376f,0x7452a4,0xa374df,0xc39aef,0xe3c9ff},
 {0x81501d,0xaf792f,0xe1ac49,0xf3cc72,0xffe5a6}}
local INK=0x182637
local CREAM=0xe4eee5
local function clamp(n) return math.max(0,math.min(255,math.floor(n))) end
local function rgb(n) return pc.rgba((n>>16)&255,(n>>8)&255,n&255,255) end
local function shade(color,value)
 local p=palettes[color];local n=clamp(value)*4;local i=math.floor(n/255)+1;local t=n%255
 local a,b=p[i],p[math.min(5,i+1)];local v=0
 for shift=16,0,-8 do v=v|math.floor((((a>>shift)&255)*(255-t)+((b>>shift)&255)*t+127)/255)<<shift end
 return v
end
-- Authored heel/toe axes from the approved sixteen cells, not regenerated limbs.
local shoeAxes={
 {{38,293,67,348,61,302,43,340},{168,335,228,368,181,342,221,354}},
 {{94,319,124,368,116,326,100,358},{124,355,168,378,129,359,161,373}},
 {{70,289,96,342,91,298,75,333},{109,352,166,378,114,359,157,371}},
 {{32,289,61,341,55,297,37,333},{177,338,237,371,183,345,228,359}},
 {{48,293,78,347,72,302,54,339},{187,334,246,369,192,341,238,355}},
 {{90,313,98,326,97,315,91,324},{74,360,125,382,81,365,117,376}},
 {{72,297,100,351,95,306,78,342},{95,354,155,378,101,360,147,371}},
 {{29,293,58,347,52,301,35,339},{174,326,231,363,180,333,223,349}},
 {{46,270,75,325,70,279,51,317},{176,316,236,348,182,323,228,337}},
 {{110,281,136,329,132,288,115,321},{112,332,170,354,118,337,162,348}},
 {{90,263,116,313,112,270,95,305},{111,332,168,354,117,337,160,348}},
 {{39,266,67,319,62,273,44,312},{176,314,233,348,182,321,225,336}},
 {{59,269,87,323,82,277,64,316},{184,314,241,348,190,321,233,336}},
 {{121,282,148,329,143,289,126,321},{106,335,164,354,112,340,156,348}},
 {{77,279,105,331,100,287,82,324},{101,334,158,355,107,339,150,349}},
 {{38,270,67,323,62,277,43,316},{178,306,232,340,184,313,224,329}}}
local hasTint={top=true,bottom=true,costume=true,shoes=true}
local gear={ear=true,eyewear=true,wrist=true,waist=true,hydration=true}
local contact=Image(W*3,H*2*math.ceil(#data.styles/3),ColorMode.RGB);contact:clear(rgb(0xf1f6f2))
local styleIndex=0
for _,s in ipairs(data.styles) do
 local baseSheet=Image{fromFile=input..'/'..s.key..'-base.png'}
 local allowSheet=Image{fromFile=input..'/'..s.key..'-allow.png'}
 local roleSheet=Image{fromFile=input..'/'..s.key..'-roles.png'}
 local spr=Sprite(W,H,ColorMode.RGB)
 local originalLayer=spr.layers[1];originalLayer.name='Approved original reference';originalLayer.isVisible=false
 local referenceLayer=spr:newLayer();referenceLayer.name='Existing construction reference';referenceLayer.isVisible=false
 local foundationLayer=spr:newLayer();foundationLayer.name='Runtime foundation'
 local detailLayer=spr:newLayer();detailLayer.name='Aseprite authored construction'
 local tintLayer=spr:newLayer();tintLayer.name='Material role and shade';tintLayer.isVisible=false
 local all=Image(W*8,H*2,ColorMode.RGB)
 local writes=0
 for n,c in ipairs(data.cells) do
  if n>1 then spr:newEmptyFrame() end
  spr.frames[n].duration=0.09
  local base=Image(W,H,ColorMode.RGB);base:drawImage(baseSheet,Point(-c.frame*W,-c.row*H),255,BlendMode.SRC)
  local source=Image(W,H,ColorMode.RGB);source:drawImage(originalSheet,Point(-c.frame*W,-c.row*H),255,BlendMode.SRC)
  local foundation=gear[s.slot] and source or base
  local detail=Image(W,H,ColorMode.RGB)
  local tint=Image(W,H,ColorMode.RGB)
  local composite=Image(W,H,ColorMode.RGB);composite:drawImage(foundation,Point(0,0),255,BlendMode.SRC)
  local function allowed(x,y)
   return x>=0 and y>=0 and x<W and y<H and pc.rgbaA(allowSheet:getPixel(x+c.frame*W,y+c.row*H))==255
  end
  local function role(x,y) return roleSheet:getPixel(x+c.frame*W,y+c.row*H) end
  local function ink(x,y,value,materialRole,materialShade)
   x=math.floor(x);y=math.floor(y)
   if not allowed(x,y) then return end
   detail:drawPixel(x,y,rgb(value))
   local b=foundation:getPixel(x,y)
   composite:drawPixel(x,y,pc.rgba((value>>16)&255,(value>>8)&255,value&255,pc.rgbaA(b)))
   if materialRole and materialRole>=1 and materialRole<=3 then tint:drawPixel(x,y,pc.rgba(materialRole,clamp(materialShade),0,255)) else tint:drawPixel(x,y,0) end
   writes=writes+1
  end
  local function dab(x,y,w,h,delta)
   for yy=math.floor(y),math.floor(y+h-1) do for xx=math.floor(x),math.floor(x+w-1) do
    if allowed(xx,yy) then
     local b=base:getPixel(xx,yy)
     ink(xx,yy,(clamp(pc.rgbaR(b)+delta)<<16)|(clamp(pc.rgbaG(b)+delta)<<8)|clamp(pc.rgbaB(b)+delta))
    end
   end end
  end
  local ordinal=s.ordinal
  local tc=s.slot=='costume' and ({3,1,4})[ordinal] or ({1,3,5})[ordinal]
  local bc=s.slot=='costume' and ({3,1,4})[ordinal] or ({3,4,1})[ordinal]
  local sc=s.slot=='costume' and ({3,1,4})[ordinal] or ({3,5,2})[ordinal]
  local topY=c.eyeY+(c.row==0 and 44 or 41)
  local function material(x,y,delta,force)
   if not allowed(x,y) then return end
   local p=role(x,y);local region=force or pc.rgbaR(p);local level=clamp(pc.rgbaG(p)+delta)
   if region<1 or region>3 then return end
   ink(x,y,shade(region==1 and tc or region==2 and bc or sc,level),region,level)
  end
  -- Row runs follow the already-authored cloth silhouette on bent as well as straight limbs.
  local runs={}
  for y=0,H-1 do
   runs[y]={};local start=nil
   for x=0,W do
    local inside=x<W and allowed(x,y) and pc.rgbaR(role(x,y))==2
    if inside and not start then start=x end
    if not inside and start then table.insert(runs[y],{start,x-1});start=nil end
   end
  end
  if s.slot=='hair' then
   -- Connected tapered planes follow the source hair flow. No sprinkled noise.
   -- Dark source contour remains untouched inside the explicit hair allowance.
   for y=0,H-1 do for x=0,W-1 do
    if allowed(x,y) then
     local b=base:getPixel(x,y)
     local brightest=math.max(pc.rgbaR(b),pc.rgbaG(b),pc.rgbaB(b))
     local px,py=x-c.eyeX,y-c.eyeY
     local delta=nil
     if brightest>=(c.row==0 and 54 or 62) and brightest<175 then
      if ordinal==1 then
       local left=c.row==0 and -53 or -48;local right=c.row==0 and 32 or 31
       if px>=left and px<=right then
        local t=(px-left)/(right-left)
        local curve=(c.row==0 and -55 or -43)-(c.row==0 and 15 or 20)*math.sin(math.pi*t)+(c.row==0 and 10*t or 0)
        local width=2+3.2*math.sin(math.pi*t)
        local distance=py-curve
        if math.abs(distance)<=width then delta=distance<0 and 27 or 16
        elseif distance>width and distance<width+2.0 then delta=-11 end
       end
      elseif ordinal==2 then
       if py>=-62 and py<=-23 then
        local t=(py+62)/39;local center=-48+8*t
        local width=2+4*math.sin(math.pi*t)
        if math.abs(px-center)<=width then delta=px<center and 17 or 30 end
       end
       if px>=-14 and px<=32 then
        local t=(px+14)/46;local center=-83+25*t*t
        if math.abs(py-center)<=2+3*math.sin(math.pi*t) then delta=py<center and 28 or 14 end
       end
      else
       if c.row==0 then
        -- Connected upper-back sweep sits on the actual inner crown.
        -- The v2 curve lay mostly outside the visible bright hair, despite a nonempty cel.
        if px>=-47 and px<=6 then
         local t=(px+47)/53;local curve=-66-12*math.sin(math.pi*t)+t
         if math.abs(py-curve)<=2+3.3*math.sin(math.pi*t) then delta=py<curve and 27 or 14 end
        end
        if py>=-57 and py<=-32 and math.abs(px-(-61+(py+57)*.2))<3 then delta=17 end
       else
        -- The original ponytail is a separate, explicitly bounded source surface.
        if px>=-122 and px<=-72 then
         local t=(px+122)/50;local curve=-24-13*t
         if math.abs(py-curve)<=2+4*math.sin(math.pi*t) then delta=py<curve and 31 or 17 end
        end
        if px>=-92 and px<=-60 then
         local t=(px+92)/32;local curve=-90-10*math.sin(math.pi*t)
         if math.abs(py-curve)<=3 then delta=21 end
        end
       end
      end
      if delta then dab(x,y,1,1,delta) end
     end
    end
   end end
  elseif s.slot=='face' then
   local x,y=c.eyeX-3,c.eyeY+13
   if ordinal==1 then
    for dx=0,10 do ink(x+dx,y+math.floor(dx/6),CREAM);ink(x+dx,y+4+math.floor(dx/6),0xc5d4ca) end
   elseif ordinal==2 then
    for dy=0,5 do for dx=0,3 do ink(x+3+dx+math.floor(dy/2),y+dy,dy<3 and 0x236f75 or 0x144d5b) end end
   else
    for dy=0,5 do for dx=0,11 do
     local edge=dy==0 or dy==5 or dx==0 or dx==11
     ink(x+dx,y+dy,edge and 0x9c634e or (dx%4==0 and 0xe7b083 or 0xf0c497))
    end end
   end
  elseif s.slot=='headwear' then
   local bandY=c.eyeY-(c.row==0 and 54 or 47)
   if ordinal==1 then
    for y=bandY-45,bandY+3 do for x=c.eyeX-65,c.eyeX-10 do
     if allowed(x,y) then
      local b=base:getPixel(x,y);local light=math.max(pc.rgbaR(b),pc.rgbaG(b),pc.rgbaB(b))
      if light>85 and light<245 then dab(x,y,1,1,-24) end
      if ((x-c.eyeX)%8)<2 and ((y-bandY)%8)<2 then dab(x,y,2,2,18) end
     end
    end end
    for y=bandY-36,bandY-3 do dab(c.eyeX-10+math.floor((y-bandY)*.18),y,2,1,-26) end
   elseif ordinal==2 then
    for y=bandY-3,bandY+1 do for x=c.eyeX-51,c.eyeX+54 do dab(x,y,1,1,y==bandY-3 and -23 or 24) end end
    for x=c.eyeX+20,c.eyeX+84 do dab(x,bandY+18+math.floor((x-c.eyeX-20)/8),1,2,18) end
   else
    for y=bandY-5,bandY+14 do for x=c.eyeX-66,c.eyeX+57 do
     if (x-c.eyeX)%7<=1 then dab(x,y,1,1,20) elseif (x-c.eyeX)%7==4 then dab(x,y,1,1,-17) end
    end end
    for x=c.eyeX-5,c.eyeX+8 do dab(x,bandY+2,1,3,26) end
   end
  elseif hasTint[s.slot] or s.slot=='socks' then
   for y=0,H-1 do for x=0,W-1 do
    if allowed(x,y) then
     local p=role(x,y);local region=pc.rgbaR(p);local level=pc.rgbaG(p)
     local localX=x-c.waistX;local localY=y-topY
     if region==1 then
      local axis=c.waistX+22-math.floor((y-topY)*12/math.max(1,c.waistY-3-topY))
      if ordinal==1 then
       if x<axis-17 then material(x,y,-45) end
       if math.abs(x-(axis-18))<=1 or y==topY+14 and x>axis-14 then material(x,y,23) end
       if y>=topY+7 and y<topY+28 and math.abs(x-(c.shoulderX+9+(y-topY)*.7))<2 then material(x,y,-50) end
      elseif ordinal==2 then
       if y>=topY+7 and y<=topY+35 and math.abs(x-axis)<=2 then material(x,y,-70) end
       if y>=topY+8 and y<=topY+32 and math.abs(x-axis)<=0 then ink(x,y,y%6<3 and CREAM or 0x789596) end
       if y>=topY+24 and y<=topY+38 and x>=axis+9 and x<=axis+22 and (y==topY+24 or y==topY+38 or x==axis+9 or x==axis+22) then material(x,y,-38) end
      else
       if localY>=14 and localY<=19 and localX>-16 and localX<28 then ink(x,y,localY<17 and 0xb7d9d3 or 0x284959) end
       if localY>=27 and localY<=40 and localX>=-15 and localX<=10 and (localY==27 or localX==-15) then material(x,y,-64) end
       if localY>=30 and localY<=34 and localX>=-8 and localX<=8 then ink(x,y,localY==30 and CREAM or 0x779597) end
      end
     elseif region==2 then
      for _,run in ipairs(runs[y]) do if x>=run[1] and x<=run[2] then
       local w=run[2]-run[1]+1;local q=x-run[1]
       if ordinal==1 then
        if q<math.max(3,math.floor(w*.25)) then material(x,y,-35);if q%7<2 and y%8<2 then material(x,y,12) end end
       elseif ordinal==2 then
        local seam=math.floor(w*.34)
        if q<seam then material(x,y,-48) elseif q<=seam+2 then material(x,y,18) end
       else
        if q>=4 and q<=6 then ink(x,y,0xc0d6d0) end
        if y>=c.waistY+10 and y<=c.waistY+15 and q>=w*.4 and q<=w*.75 then material(x,y,y==c.waistY+10 and 27 or -54) end
       end
      end end
     elseif region==3 then
      for _,a in ipairs(shoeAxes[n]) do if x>=a[1] and x<=a[3] and y>=a[2] and y<=a[4] then
       local dx,dy=a[7]-a[5],a[8]-a[6];local length=math.sqrt(dx*dx+dy*dy)
       local along=((x-a[5])*dx+(y-a[6])*dy)/length
       local cross=((x-a[5])*(-dy)+(y-a[6])*dx)/length
       local t=along/length
       if ordinal==1 then
        local wave=2+math.sin(t*4)*4
        if math.abs(cross-wave)<2 then material(x,y,-65) elseif cross<wave and t<.55 then material(x,y,-24) end
       elseif ordinal==2 then
        if t>.16 and t<.79 and (math.floor(along/6)%2==0 or math.floor((cross+along*.35)/6)%2==0) then material(x,y,-46) end
        if t>.37 and t<.43 then material(x,y,24) end
       else
        if t>.2 and t<.76 and math.floor((along+cross*.5)/7)%3==0 then material(x,y,27) end
        if t>.78 and t<.84 then material(x,y,-38) end
       end
      end end
     elseif region==4 then
      local detailFlag=pc.rgbaB(p);local cuff=(detailFlag&((s.slot=='costume' and ordinal>1 or s.base==2) and 2 or 1))~=0
      local cval=base:getPixel(x,y)
      if ordinal==1 and cuff then ink(x,y,(y%5<2) and 0x28575f or CREAM)
      elseif ordinal==2 then
       if (detailFlag&4)~=0 then ink(x,y,0x779d9e) elseif cuff then ink(x,y,0x1e4a59) end
      elseif ordinal==3 and (x-c.waistX)%7<2 and (y-c.waistY)%8<2 then dab(x,y,1,1,-38) end
     end
    end
   end end
  elseif gear[s.slot] then
   local k=s.ordinal
   local dx=c.wristX-c.elbowX;local dy=c.wristY-c.elbowY;local length=math.max(1,math.sqrt(dx*dx+dy*dy));local ux,uy=dx/length,dy/length
   for y=0,H-1 do for x=0,W-1 do
    if allowed(x,y) then
     local p=base:getPixel(x,y);local value=(pc.rgbaR(p)<<16)|(pc.rgbaG(p)<<8)|pc.rgbaB(p)
     local paint=true
     if s.slot=='ear' then
      local ex,ey=x-c.earX,y-c.earY
      if k==1 then
       paint=not(ex<-6 and ey%8>=5)
       if ex>=3 and ey>=-2 and ey<=3 then value=ey<0 and 0xaccac3 or 0x2c6b72 end
      elseif k==2 then
       paint=ey<=6
       if ex>=0 and ex<=4 and ey>=-2 and ey<=3 then value=0xc0d4cc end
      else
       if ex>=-2 and ex<=3 and ey>=-5 and ey<=7 then value=(ey%4<2) and 0x9ab9b5 or 0x2c4f5e end
      end
     elseif s.slot=='eyewear' then
      local ex,ey=x-c.eyeX,y-c.eyeY
      if k==1 then
       if ey>=-6 and ey<=5 then value=shade(3,clamp(92-ex*.8-ey*4)) end
       if ey==-7 and ex>=-2 and ex<=18 then value=0xc1e5df end
      elseif k==2 then
       paint=ey<=7
       if ey<=-8 then value=0x223342 elseif ey>=5 then value=0xb3d7d0 end
      else
       if ey>=-6 and ey<=5 and (ex+ey)%12<4 then value=0xa6cad8 end
       if ex>=39 and ey>=-4 and ey<=3 then value=0x2d608c end
      end
     elseif s.slot=='wrist' then
      local px=x-(c.wristX-ux*5);local py=y-(c.wristY-uy*5);local u=px*ux+py*uy;local v=-px*uy+py*ux
      if k==1 then
       local radius=math.sqrt(u*u+v*v)
       if radius<5.4 and radius>3.1 then value=0x8ad4c4 elseif radius<=3.1 then value=0x1d3d4b end
      elseif k==2 then
       paint=math.abs(v)<=5.5
       if math.abs(u)<4 and math.abs(v)<3 then value=u<0 and 0x9bccc9 or 0x284c60 end
      else
       if math.abs(u)<4 and math.abs(v)<4 then value=(math.floor(v+4)%4<2) and 0x9fd6ca or 0x204b59 end
      end
     elseif s.slot=='waist' then
      local ex,ey=x-c.waistX,y-c.waistY
      if k==1 then paint=ey<=3;if ey>=-5 and ey<=-3 and ex>=-8 and ex<=17 then value=ey==-5 and 0xc2d8d0 or 0x223b49 end
      elseif k==2 then
       paint=not(ex>=1 and ex<=4 and ey>=-10)
       if ex==-2 or ex==7 then value=0x182c3a end
       if ey==-6 and ex>=-9 and ex<=17 then value=0xa9d5cf end
      else
       if ex>=7 and ex<=16 and ey>=-8 and ey<=4 then value=ex<=10 and 0xb0d1c9 or 0x49778a end
       if ex>=10 and ex<=13 and ey<=-8 then value=CREAM end
       if math.abs(ey-ex*.32+4)<1.5 then value=0x91b7ad end
      end
     elseif s.slot=='hydration' then
      local ex,ey=x-c.waistX,y-topY
      if k==1 then
       if (x-c.waistX)%7<2 and (y-topY)%7<2 then value=0x88b6b1 end
       paint=not(ex>9 and ex<23 and ey<21)
      elseif k==2 then
       if (ex>=-24 and ex<=-17 or ex>=19 and ex<=24) and ey>=22 and ey<=43 then value=(x%4<2) and 0xc0d7d0 or 0x638e9b end
       if ey>=12 and ey<=15 then value=0x2a4a58 end
      else
       local height=math.max(1,c.waistY-topY)
       local left=c.shoulderX+14+(c.waistX-20-c.shoulderX-14)*ey/height
       local right=c.waistX+30
       paint=math.abs(x-left)<7 or math.abs(x-right)<6 or math.abs(ey-height*.32)<3
       if math.abs(ey-height*.32)<1.3 then value=0xb1d8cc end
      end
     end
     if paint then ink(x,y,value) end
    end
   end end
  end
  spr:newCel(originalLayer,n,source,Point(0,0))
  spr:newCel(referenceLayer,n,base,Point(0,0))
  spr:newCel(foundationLayer,n,foundation,Point(0,0))
  spr:newCel(detailLayer,n,detail,Point(0,0))
  if hasTint[s.slot] then spr:newCel(tintLayer,n,tint,Point(0,0)) end
  all:drawImage(composite,Point(c.frame*W,c.row*H),255,BlendMode.SRC)
  local dir=output..'/cels/'..s.key;app.fs.makeAllDirectories(dir)
  detail:saveAs(dir..'/'..c.gender..'-'..c.frame..'.png')
  if hasTint[s.slot] then tint:saveAs(dir..'/'..c.gender..'-'..c.frame..'-tint.png') end
  if c.frame==0 then
   local row=math.floor(styleIndex/3);local col=styleIndex%3
   contact:drawImage(composite,Point(col*W,c.row*H+row*H*2))
  end
 end
 originalLayer.isEditable=false;referenceLayer.isEditable=false;foundationLayer.isEditable=false;tintLayer.isEditable=false
 local male=spr:newTag(1,8);male.name='male';local female=spr:newTag(9,16);female.name='female'
 app.fs.makeAllDirectories(output..'/projects');app.fs.makeAllDirectories(output..'/previews')
 spr:saveAs(output..'/projects/'..s.key..'.aseprite')
 all:saveAs(output..'/previews/'..s.key..'-sheet.png')
 print(s.key..': '..writes..' actual Aseprite construction writes across16 cels')
 spr:close();styleIndex=styleIndex+1;collectgarbage('collect')
end
-- Long contact is deliberately generated from actual composites, never marketed as Android.
contact:saveAs(output..'/previews/atelier-contact.png')
