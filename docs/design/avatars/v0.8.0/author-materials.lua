-- Original facial/body pixels are reference only. All new material ink is
-- painted by Aseprite into separate transparent, pose-bound cloth cels.
local input=assert(app.params.input)
local output=assert(app.params.output)
local original=assert(app.params.original)
local data=dofile(input..'/references.lua')
local originalSheet=Image{fromFile=original}
local W,H=256,384
local styles={'headwear-1','headwear-2','costume-1','costume-4','costume-5'}
local pc=app.pixelColor
local contact=Image(W*10,H*2,ColorMode.RGB)
contact:clear(pc.rgba(241,246,242,255))
local styleIndex=0
local function clamp(v) return math.max(0,math.min(255,v)) end
for _,style in ipairs(styles) do
  local baseSheet=Image{fromFile=input..'/'..style..'-base.png'}
  local allowance=Image{fromFile=input..'/'..style..'-allow.png'}
  local spr=Sprite(W,H,ColorMode.RGB)
  spr.layers[1].name='Original reference (preserved)'
  local originalLayer=spr.layers[1]
  originalLayer.isVisible=false
  local materialReference=spr:newLayer()
  materialReference.name='Current garment reference'
  local detailLayer=spr:newLayer()
  detailLayer.name='Authored material detail'
  local painted=0
  local sheet=Image(W*8,H*2,ColorMode.RGB)
  for n,c in ipairs(data.cells) do
    if n>1 then spr:newEmptyFrame() end
    spr.frames[n].duration=0.09
    local base=Image(W,H,ColorMode.RGB)
    local source=Image(W,H,ColorMode.RGB)
    base:drawImage(baseSheet,Point(-c.frame*W,-c.row*H),255,BlendMode.SRC)
    source:drawImage(originalSheet,Point(-c.frame*W,-c.row*H),255,BlendMode.SRC)
    spr:newCel(originalLayer,n,source,Point(0,0))
    spr:newCel(materialReference,n,base,Point(0,0))
    local detail=Image(W,H,ColorMode.RGB)
    local composite=Image(W,H,ColorMode.RGB)
    composite:drawImage(base,Point(0,0),255,BlendMode.SRC)
    local function allowed(x,y)
      return x>=0 and x<W and y>=0 and y<H and
        pc.rgbaA(allowance:getPixel(x+c.frame*W,y+c.row*H))>0
    end
    local function dab(x,y,width,height,delta)
      x=math.floor(x);y=math.floor(y)
      for yy=y,y+height-1 do for xx=x,x+width-1 do
        if allowed(xx,yy) then
          local p=base:getPixel(xx,yy)
          local r,g,b=pc.rgbaR(p),pc.rgbaG(p),pc.rgbaB(p)
          -- Leave the authored deep contour and existing brilliant trim quiet.
          -- This is material shading inside an explicit cloth mask, not a skin detector.
          if math.max(r,g,b)>74 and math.min(r,g,b)<246 then
            detail:drawPixel(xx,yy,pc.rgba(clamp(r+delta),clamp(g+delta),clamp(b+delta),255))
            composite:drawPixel(xx,yy,pc.rgba(clamp(r+delta),clamp(g+delta),clamp(b+delta),pc.rgbaA(p)))
            painted=painted+1
          end
        end
      end end
    end
    local minX,minY,maxX,maxY=W,H,0,0
    for y=14,c.eyeY-28 do for x=8,247 do
      if allowed(x,y) then minX=math.min(minX,x);minY=math.min(minY,y);maxX=math.max(maxX,x);maxY=math.max(maxY,y) end
    end end
    if maxY>minY then
      -- Broken panel stitches follow the same crown plane on all sixteen poses.
      local seamX=c.eyeX-25
      for y=minY+10,maxY-9,9 do
        local x=seamX+(y-minY)*0.12
        dab(x,y,2,3,-13);dab(x-2,y,1,2,11)
      end
      for x=minX+12,maxX-10,12 do dab(x,maxY-5,3,1,12) end
      if style=='costume-4' or style=='costume-5' then
        for y=minY+10,maxY-13,13 do
          for x=minX+10,maxX-8,14 do
            dab(x,y,3,2,style=='costume-4' and 12 or 9)
            dab(x+2,y+3,2,2,-10)
          end
        end
      end
    end
    if style=='headwear-1' and maxY>minY then
      -- A compact, forward-leaning embroidered R ties the cap to the kit.
      -- Paint only the existing crown allowance; no plate/background is added.
      local glyph={30,17,17,30,20,18,17}
      local originX=c.eyeX+7
      local originY=math.floor(minY+(maxY-minY)*0.43)
      for gy=0,6 do
        local slant=math.floor((6-gy)/3)
        for gx=0,4 do
          if (glyph[gy+1] & (1 << (4-gx)))~=0 then
            for dy=0,1 do for dx=0,1 do
              local x=originX+gx*2+slant+dx;local y=originY+gy*2+dy
              if allowed(x,y) then
                local b=base:getPixel(x,y)
                if math.max(pc.rgbaR(b),pc.rgbaG(b),pc.rgbaB(b))>120 then
                  detail:drawPixel(x,y,pc.rgba(40,107,104,255))
                  composite:drawPixel(x,y,pc.rgba(40,107,104,pc.rgbaA(b)))
                  painted=painted+1
                end
              end
            end end
          end
        end
      end
    end
    local top=c.eyeY+(c.row==0 and 44 or 41)
    if style=='costume-4' then
      -- Small paired pile clusters, anchored to the torso rather than frame noise.
      local row=0
      for y=top+17,c.waistY+56,12 do
        for x=c.waistX-55+(row%2)*6,c.waistX+50,13 do
          dab(x,y,4,2,13);dab(x+3,y+3,2,3,-13);dab(x-1,y+2,1,2,6)
        end
        row=row+1
      end
    elseif style=='costume-5' then
      local row=0
      for y=top+19,c.waistY+40,14 do
        for x=c.waistX-45+(row%2)*7,c.waistX+45,15 do
          dab(x,y,2,3,10);dab(x+2,y+2,2,2,-9)
        end
        row=row+1
      end
    elseif style=='costume-1' then
      -- Velvet fold glints and compact shearling clusters, without a new belt shape.
      for y=top+22,c.waistY-14,13 do
        dab(c.waistX-22,y,2,5,9);dab(c.waistX+28,y+3,2,4,-11)
      end
      for x=c.waistX-28,c.waistX+26,10 do
        dab(x,top+11,3,2,9);dab(x+2,top+14,2,2,-9)
      end
    end
    spr:newCel(detailLayer,n,detail,Point(0,0))
    sheet:drawImage(composite,Point(c.frame*W,c.row*H),255,BlendMode.SRC)
    if c.frame==0 then
      contact:drawImage(base,Point(styleIndex*2*W,c.row*H))
      contact:drawImage(composite,Point((styleIndex*2+1)*W,c.row*H))
    end
    local dir=output..'/cels/'..style
    app.fs.makeAllDirectories(dir)
    detail:saveAs(dir..'/'..c.gender..'-'..c.frame..'.png')
  end
  app.fs.makeAllDirectories(output..'/previews')
  sheet:saveAs(output..'/previews/'..style..'-sheet.png')
  originalLayer.isEditable=false
  materialReference.isEditable=false
  local male=spr:newTag(1,8);male.name='male';local female=spr:newTag(9,16);female.name='female'
  app.fs.makeAllDirectories(output..'/projects')
  spr:saveAs(output..'/projects/'..style..'.aseprite')
  print(style..': authored '..painted..' dab pixel writes across16 cels')
  spr:close()
  styleIndex=styleIndex+1
  collectgarbage('collect')
end
contact:saveAs(output..'/previews/before-after-contact.png')
