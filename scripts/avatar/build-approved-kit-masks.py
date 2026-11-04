"""Compile source-specific garment label DATA; never write or regenerate source artwork.

Authored geometry/palette constraints in annotations.json are only used here.
Android applies the resulting explicit mask, never skin/hue detection at runtime.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import colorsys, hashlib, json
from collections import deque,Counter
ROOT=Path(__file__).resolve().parents[2]
DOC=ROOT/'docs/design/avatars/running-kit-v1';APP=ROOT/'app/src/main/assets/avatars/running-kit-v1'
SOURCE=ROOT/'docs/design/avatars/v0.3.0/running-motion-art-source.png'
SHA='f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
def digest(b):return hashlib.sha256(b).hexdigest()
assert digest(SOURCE.read_bytes())==SHA
im=Image.open(SOURCE).convert('RGBA');assert im.size==(2048,768)
def rect(x0,y0,x1,y1):return [[x0,y0],[x1,y0],[x1,y1],[x0,y1]]
# Fixed source coordinates, not a body rig or generated clothing geometry.
if not (DOC/'annotations.json').exists():
 cells=[]
 for row in range(2):
  for f in range(8):
   cells.append({'gender':'male' if row==0 else 'female','frame':f,
    'regions':{'top':rect(109,179,193,265) if row==0 else rect(98,160,207,244),'bottom':rect(87,237,190,312) if row==0 else rect(72,223,208,326),'shoes':rect(13,278,253,383) if row==0 else rect(22,263,253,373)},
    'headProtection':[rect(0,0,255,179)] if row==0 else [rect(0,0,255,157),[[128,125],[234,125],[234,163],[208,177],[174,182],[148,166]]],
    'paddingProtection':rect(0,0,255,11) if row==1 else None,
    'minimumComponentPixels':{'top':48,'bottom':64,'shoes':12 if row==0 else 48}})
 data={'schemaVersion':1,'sourceSha256':SHA,'coordinateSystem':'256x384 cell-local; male row0/female row1; 8 frames each','authoring':'Source-specific manually bounded garment regions and independent head/skin/ink/white protection. Runtime uses compiled explicit labels only.','cells':cells,'protectionRules':{'skinHueDegrees':[13,55],'skinMinimumSaturation':.16,'skinMinimumValue':.18,'inkMaximumLuminance':24,'whiteMaximumSaturation':.20,'whiteMinimumValue':.60},'shadeLuminanceRanges':{'male':{'top':[45,210],'bottom':[22,85],'shoes':[45,235]},'female':{'top':[35,210],'bottom':[22,85],'shoes':[60,225]}}}
 (DOC/'annotations.json').write_text(json.dumps(data,indent=2)+'\n')
ann=json.loads((DOC/'annotations.json').read_text());assert ann['sourceSha256']==SHA and len(ann['cells'])==16
mask=Image.new('RGBA',im.size);protected=Image.new('RGBA',im.size);report=[]
rules=ann['protectionRules']
for cell in ann['cells']:
 row=0 if cell['gender']=='male' else 1;frame=cell['frame'];ox=frame*256;oy=row*384
 src=im.crop((ox,oy,ox+256,oy+384));pixels=list(src.get_flattened_data())
 protect=Image.new('1',(256,384));draw=ImageDraw.Draw(protect)
 for poly in cell['headProtection']:draw.polygon([tuple(p) for p in poly],fill=1)
 if cell['paddingProtection']:draw.polygon([tuple(p) for p in cell['paddingProtection']],fill=1)
 protect=list(protect.get_flattened_data());hsv=[];lum=[]
 for i,(r,g,b,a) in enumerate(pixels):
  h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255);h*=360;light=.2126*r+.7152*g+.0722*b;hsv.append((h,s,v));lum.append(light)
  # Independently authored protection, not the complement of the garment labels.
  skin=rules['skinHueDegrees'][0]<=h<=rules['skinHueDegrees'][1] and s>=rules['skinMinimumSaturation'] and v>=rules['skinMinimumValue']
  ink=light<=rules['inkMaximumLuminance']
  white=s<=rules['whiteMaximumSaturation'] and v>=rules['whiteMinimumValue']
  if a>=16 and (skin or ink or white):protect[i]=1
 labels=[(0,0,0,0)]*len(pixels);counts={};components={}
 for region,name in enumerate(['top','bottom','shoes'],1):
  roi=Image.new('1',(256,384));ImageDraw.Draw(roi).polygon([tuple(p) for p in cell['regions'][name]],fill=1);inside=list(roi.get_flattened_data())
  candidates=set()
  for i,(r,g,b,a) in enumerate(pixels):
   if not inside[i] or protect[i] or a<200:continue
   h,s,v=hsv[i]
   if name=='top':ok=((h<=12 or h>=348) and s>=.25 and v>=.30) if row==0 else (140<=h<=195 and s>=.25 and v>=.25)
   elif name=='bottom':ok=195<=h<=265 and s>=.20 and 24<lum[i]<110
   else:ok=(135<=h<=195 and s>=.25 and v>=.30) if row==0 else ((h<=12 or h>=348) and s>=.30 and v>=.35)
   if ok:candidates.add(i)
  accepted=[];sizes=[]
  explicit=Image.new('1',(256,384));edraw=ImageDraw.Draw(explicit)
  for poly in cell.get('includeSmallComponents',{}).get(name,[]):edraw.polygon([tuple(p) for p in poly],fill=1)
  explicit=list(explicit.get_flattened_data())
  while candidates:
   seed=candidates.pop();part=[seed];q=deque([seed])
   while q:
    cur=q.popleft();x=cur%256;y=cur//256
    for nx,ny in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
     j=ny*256+nx
     if 0<=nx<256 and 0<=ny<384 and j in candidates:candidates.remove(j);q.append(j);part.append(j)
   sizes.append(len(part))
   if len(part)>=cell['minimumComponentPixels'][name] or all(explicit[i] for i in part):accepted.extend(part)
  low,high=ann['shadeLuminanceRanges'][cell['gender']][name]
  for i in accepted:
   assert labels[i][3]==0,'Overlapping garment annotations'
   shade=round(max(0,min(1,(lum[i]-low)/(high-low)))*255)
   labels[i]=(region,shade,0,255)
  counts[name]=len(accepted);components[name]=sorted(sizes,reverse=True)[:10]
  assert accepted,(cell['gender'],frame,name,'no garment pixels')
 assert all(not protect[i] or labels[i][3]==0 for i in range(len(labels)))
 tile=Image.new('RGBA',(256,384));tile.putdata(labels);mask.paste(tile,(ox,oy))
 lock=Image.new('RGBA',(256,384));lock.putdata([(255,255,255,255) if x else (0,0,0,0) for x in protect]);protected.paste(lock,(ox,oy))
 report.append({'gender':cell['gender'],'frame':frame,'labelledPixels':counts,'protectedPixels':sum(bool(x) for x in protect),'componentSizes':components})
DOC.mkdir(parents=True,exist_ok=True);APP.mkdir(parents=True,exist_ok=True)
for name,picture in [('fabric-mask.png',mask),('protected-mask.png',protected)]:
 picture.save(DOC/name,optimize=False)
 (APP/name).write_bytes((DOC/name).read_bytes())
manifest={'schemaVersion':1,'maskVersion':'approved-running-kit-v1','canvas':[2048,768],'cell':[256,384],'rows':2,'frames':8,'sourceAsset':'avatars/v0.3.0/running-motion.png','sourceSha256':SHA,'maskFile':'fabric-mask.png','maskSha256':digest((DOC/'fabric-mask.png').read_bytes()),'protectedFile':'protected-mask.png','protectedSha256':digest((DOC/'protected-mask.png').read_bytes()),'annotationSha256':digest((DOC/'annotations.json').read_bytes()),'regions':{'1':'top','2':'bottom','3':'shoes-fabric'},'maskEncoding':'RGBA alpha255; red region1..3; green shade0..255; blue0. Alpha0 copies source exactly.','visualApproval':'pending','artworkScope':'Existing basic outfit colors only; no new garment forms, no body-part reconstruction.'}
for folder in [DOC,APP]:(folder/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(DOC/'mask-inspection.json').write_text(json.dumps({'sourceSha256':SHA,'sourceUnchanged':digest(SOURCE.read_bytes())==SHA,'protectedIntersection':0,'cells':report},indent=2)+'\n')
print(json.dumps({'maskSha256':manifest['maskSha256'],'protectedSha256':manifest['protectedSha256'],'cells':[{'gender':r['gender'],'frame':r['frame'],'counts':r['labelledPixels']} for r in report]},indent=2))
