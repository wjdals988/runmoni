"""Compile explicit clothing coverage DATA for the immutable approved 16 cells.

No body is reconstructed and no source pixel/file is edited here. Coordinates
describe sleeves over existing arms and tights over existing visible legs.
Hue is used only while authoring this fixed, reviewable mask, never at runtime.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import colorsys, hashlib, json, math

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / 'docs/design/avatars/wardrobe-v1'
APP = ROOT / 'app/src/main/assets/avatars/wardrobe-v1'
SOURCE = ROOT / 'docs/design/avatars/v0.3.0/running-motion-art-source.png'
SHA = 'f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SHA
source = Image.open(SOURCE).convert('RGBA')
assert source.size == (2048, 768)

# shoulder, elbow, sleeve-end before the hand. Cell-local coordinates are
# authored against each original pose, including the second half of the loop.
male = [
 [[119,195],[84,219],[74,236]], [[118,200],[101,225],[132,246]],
 [[112,196],[91,219],[91,239]], [[116,194],[81,219],[71,236]],
 [[126,194],[92,217],[82,232]], [[108,200],[92,226],[120,248]],
 [[106,200],[79,222],[84,241]], [[101,192],[66,215],[58,232]],
]
female = [
 [[106,188],[84,207],[76,222]], [[125,195],[115,216],[143,232]],
 [[122,188],[109,207],[119,225]], [[105,186],[83,206],[75,222]],
 [[109,187],[88,208],[81,223]], [[125,188],[116,215],[137,230]],
 [[113,190],[99,211],[104,231]], [[104,187],[83,206],[74,222]],
]
right = [
 [[179,202],[188,226],[201,221]], [[178,201],[184,236],[195,232]],
 [[174,202],[183,230],[199,222]], [[176,200],[186,224],[200,216]],
 [[185,199],[199,228],[215,219]], [[170,207],[177,240],[185,237]],
 [[170,206],[177,242],[190,237]], [[170,198],[179,232],[193,224]],
]
female_right = [
 [[177,190],[188,211],[202,207]], [[177,190],[190,215],[204,212]],
 [[177,190],[188,212],[204,209]], [[177,190],[188,211],[203,207]],
 [[177,190],[188,211],[203,207]], [[177,190],[190,215],[204,212]],
 [[171,194],[181,219],[196,215]], [[177,190],[188,211],[203,207]],
]

# Independent hand protection polygons, not the complement of sleeve coverage.
# These lock the original fingers/palms, including bent far arms in poses5..7.
male_hands = [
 [[[53,236],[62,231],[88,242],[85,254],[70,262],[53,252]], [[201,199],[219,195],[236,205],[237,220],[220,234],[203,232],[195,223]]],
 [[[126,241],[143,238],[159,249],[158,261],[139,265],[127,256]], [[192,214],[207,209],[224,219],[225,235],[209,244],[191,241]]],
 [[[89,234],[109,231],[128,240],[127,256],[112,261],[94,253]], [[196,204],[210,198],[226,207],[227,224],[212,235],[194,232]]],
 [[[50,235],[60,230],[85,241],[83,255],[68,261],[50,251]], [[198,197],[215,193],[233,202],[235,219],[217,233],[199,231],[192,222]]],
 [[[60,232],[71,227],[96,239],[93,252],[79,258],[60,248]], [[211,193],[228,190],[245,200],[246,216],[229,232],[211,229],[204,219]]],
 [[[112,244],[127,237],[144,246],[145,260],[129,268],[113,260]], [[179,216],[193,211],[211,220],[211,236],[196,246],[178,241]]],
 [[[64,235],[78,230],[98,241],[96,255],[81,263],[65,254]], [[178,216],[192,211],[210,221],[210,239],[195,249],[177,243]]],
 [[[40,231],[52,226],[76,238],[73,251],[58,257],[41,247]], [[179,202],[193,197],[210,206],[212,222],[196,236],[179,232]]],
]
female_hands = [
 [[[55,214],[65,207],[93,219],[90,233],[75,241],[55,231]], [[198,189],[213,182],[233,189],[238,204],[222,220],[201,218],[194,208]]],
 [[[129,221],[144,216],[166,226],[163,239],[145,246],[129,236]], [[209,196],[223,188],[243,197],[247,213],[229,230],[210,224],[203,215]]],
 [[[105,222],[121,215],[140,227],[136,241],[121,248],[106,238]], [[207,191],[222,184],[240,192],[244,208],[228,224],[208,222],[201,211]]],
 [[[53,213],[64,206],[91,218],[89,232],[73,240],[53,231]], [[197,190],[212,184],[233,192],[237,208],[221,224],[200,220],[193,211]]],
 [[[59,215],[70,208],[97,220],[94,234],[79,242],[59,233]], [[201,190],[217,182],[238,191],[242,208],[224,224],[204,221],[197,211]]],
 [[[126,222],[141,216],[158,226],[156,239],[140,247],[127,237]], [[212,194],[227,187],[246,195],[250,211],[234,228],[213,224],[206,214]]],
 [[[87,228],[101,220],[120,232],[117,246],[102,251],[88,241]], [[194,199],[209,192],[228,201],[232,216],[215,233],[196,228],[189,217]]],
 [[[52,213],[63,206],[90,218],[87,232],[72,240],[52,231]], [[196,190],[211,183],[231,191],[235,207],[219,223],[199,220],[192,210]]],
]

def projection(x, y, a, b):
    dx, dy = b[0]-a[0], b[1]-a[1]
    t = ((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)
    q = max(0, min(1, t))
    return t, math.hypot(x-a[0]-q*dx, y-a[1]-q*dy)

mask = Image.new('RGBA', source.size)
hand_protection = Image.new('RGBA', source.size)
inspection = []
for row in range(2):
    for frame in range(8):
        arms = [(male if row == 0 else female)[frame], (right if row == 0 else female_right)[frame]]
        hands = (male_hands if row==0 else female_hands)[frame]
        hand_tile=Image.new('1',(256,384)); hd=ImageDraw.Draw(hand_tile)
        for polygon in hands: hd.polygon([tuple(p) for p in polygon],fill=1)
        hand_lock=list(hand_tile.get_flattened_data())
        tile = source.crop((frame*256,row*384,frame*256+256,row*384+384))
        labels=[]; counts={'shortSleeve':0,'longSleeve':0,'legCoverage':0}
        for i,(r,g,b,a) in enumerate(tile.get_flattened_data()):
            x,y=i%256,i//256
            h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
            skin=13<=h*360<=55 and s>=.16 and v>=.18 and a>=200
            flags=0; cuff=0
            if skin and not hand_lock[i]:
                for arm_index,(shoulder,elbow,end) in enumerate(arms):
                    t,d=projection(x,y,shoulder,elbow)
                    u,e=projection(x,y,elbow,end)
                    minimum_y=(182 if row==0 else 176) if arm_index==0 else (201 if row==0 else 186)
                    if -.8<=t<=.66 and d<=24 and y>=minimum_y:
                        flags |= 1
                        if .50<=t<=.66: cuff |= 1
                    joint=math.hypot(x-elbow[0],y-elbow[1])<=17
                    if ((-.8<=t<=1 and d<=24) or (-.2<=u<=1 and e<=19) or joint) and y>=minimum_y:
                        flags |= 2
                        if .72<=u<=1 and e<=15: cuff |= 2
                # Male shorts already expose these legs. Female tights remain
                # the approved silhouette; no missing skin underlay is invented.
                if row==0 and 267<=y<=360 and 45<=x<=220:
                    flags |= 4
            shade=round(max(0,min(1,(.2126*r+.7152*g+.0722*b-65)/160))*255)
            labels.append((flags,shade,cuff,255) if flags else (0,0,0,0))
            for flag,key in [(1,'shortSleeve'),(2,'longSleeve'),(4,'legCoverage')]:
                if flags & flag: counts[key]+=1
        assert counts['shortSleeve']>50 and counts['longSleeve']>100,(row,frame,counts)
        assert row==1 or counts['legCoverage']>100,(row,frame,counts)
        out=Image.new('RGBA',(256,384));out.putdata(labels);mask.paste(out,(frame*256,row*384))
        lock=Image.new('RGBA',(256,384));lock.putdata([(255,255,255,255) if value else (0,0,0,0) for value in hand_lock])
        hand_protection.paste(lock,(frame*256,row*384))
        assert all(not hand_lock[i] or labels[i][3]==0 for i in range(len(labels)))
        inspection.append({'gender':'male' if row==0 else 'female','frame':frame,'pixels':counts,'arms':arms,'handProtection':hands})

DOC.mkdir(parents=True,exist_ok=True); APP.mkdir(parents=True,exist_ok=True)
mask.save(DOC/'coverage.png')
hand_protection.save(DOC/'hand-protection.png')
manifest={'schemaVersion':1,'version':'approved-wardrobe-v1','sourceSha256':SHA,'canvas':[2048,768],
 'cell':[256,384],'frames':8,'rows':2,'file':'coverage.png',
 'sha256':hashlib.sha256((DOC/'coverage.png').read_bytes()).hexdigest(),
 'handProtectionFile':'hand-protection.png','handProtectionSha256':hashlib.sha256((DOC/'hand-protection.png').read_bytes()).hexdigest(),
 'encoding':'A255,R flags(short1,long2,leg4),G shade0..255,B cuff flags1..2. A0 copies original.',
 'visualApproval':'pending','scope':'Fixed-frame clothing coverage only; no hidden skin, hair or body reconstruction.'}
for folder in [DOC,APP]:
 (folder/'coverage.png').write_bytes((DOC/'coverage.png').read_bytes())
 (folder/'hand-protection.png').write_bytes((DOC/'hand-protection.png').read_bytes())
 (folder/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(DOC/'coverage-inspection.json').write_text(json.dumps({'sourceSha256':SHA,'cells':inspection},indent=2)+'\n')
print(json.dumps({'sha256':manifest['sha256'],'cells':inspection},indent=2))
