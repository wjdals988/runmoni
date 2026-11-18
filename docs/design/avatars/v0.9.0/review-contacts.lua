-- Inspection-only nearest-neighbour exports from actual Aseprite composites.
local input=assert(app.params.input)
local output=assert(app.params.output)
local refs=dofile(assert(app.params.references)..'/references.lua')
local pc=app.pixelColor
app.fs.makeAllDirectories(output)
for _,size in ipairs({80,112,144}) do
 local cellW=math.floor(size*256/384)
 local contact=Image(cellW*6,size*13,ColorMode.RGB)
 contact:clear(pc.rgba(241,246,242,255))
 for i,s in ipairs(refs.styles) do
  local sheet=Image{fromFile=input..'/previews/'..s.key..'-sheet.png'}
  for row=0,1 do
   local cell=Image(256,384,ColorMode.RGB)
   cell:drawImage(sheet,Point(0,-row*384),255,BlendMode.SRC)
   if row==1 then
    -- Display padding is a presentation-only crop in the actual app too.
    for y=0,11 do for x=0,255 do cell:drawPixel(x,y,0) end end
   end
   cell:resize(cellW,size)
   contact:drawImage(cell,Point(((i-1)%3*2+row)*cellW,math.floor((i-1)/3)*size))
  end
 end
 contact:saveAs(output..'/all-items-'..size..'px.png')
end
for _,slot in ipairs({'hair','headwear','face','top','bottom','costume','socks','shoes','ear','eyewear','wrist','waist','hydration'}) do
 local contact=Image(256*4,384*2,ColorMode.RGB);contact:clear(pc.rgba(241,246,242,255))
 local before=nil;local col=1
 for _,s in ipairs(refs.styles) do if s.slot==slot then
  local sheet=Image{fromFile=input..'/previews/'..s.key..'-sheet.png'}
  if not before then
   before=Image{fromFile=assert(app.params.references)..'/'..s.key..'-base.png'}
   for row=0,1 do
    local cell=Image(256,384,ColorMode.RGB);cell:drawImage(before,Point(0,-row*384),255,BlendMode.SRC)
    contact:drawImage(cell,Point(0,row*384))
   end
  end
  for row=0,1 do
   local cell=Image(256,384,ColorMode.RGB);cell:drawImage(sheet,Point(0,-row*384),255,BlendMode.SRC)
   contact:drawImage(cell,Point(col*256,row*384))
  end
  col=col+1
 end end
 contact:saveAs(output..'/'..slot..'-reference-three-proposals.png')
end
print('Inspection contacts only: all39 eachgender80/112/144px and13 full-size reference+3proposal comparisons')
