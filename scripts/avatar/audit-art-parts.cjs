// Static PNG/manifest audit. It does not replace Android rendering or visual review.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const sharp=require(process.env.RUNMONI_NODE_MODULES?path.join(process.env.RUNMONI_NODE_MODULES,'sharp'):'sharp');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'app/src/main/assets/avatars/v0.4.0');
const assert=(ok,message)=>{if(!ok)throw Error(message)};
async function main(){
 const manifestBytes=fs.readFileSync(path.join(dir,'manifest.json'));
 const m=JSON.parse(manifestBytes);
 assert(m.canvas.join('x')==='256x384'&&m.frameMillis===90,'Clip dimensions/timing mismatch');
 assert(m.tops.length===6&&m.bottoms.length===6&&m.shoes.length===4&&m.costumes.length===6&&!m.costumes[0],'Recipe count mismatch');
 const references=['skin_torso','skin_pelvis','skin_upper_arm','skin_forearm','skin_thigh','skin_shin','joint_skin'];
 for(let i=0;i<4;i++)references.push(`head_${i}`,`hair_${i}_front`);
 for(const recipe of [...m.tops,...m.bottoms,...m.shoes,...m.costumes.slice(1)]){
  for(const key of ['part','torso','pelvis','hoodFront','hoodBack','shoePart','upperArmPart','forearmPart','thighPart','shinPart','jointPart'])if(recipe[key])references.push(recipe[key]);
  for(const key of ['palette','shoePalette'])if(recipe[key])assert(m.palettes[recipe[key]],`Missing palette ${recipe[key]}`);
  for(const key of ['upperArmCoverage','forearmCoverage','thighCoverage','shinCoverage'])if(key in recipe)assert(recipe[key]>=0&&recipe[key]<=1,`Bad coverage ${key}`);
 }
 for(const id of references)assert(m.parts[id],`Missing part ${id}`);
 const expectedBones={skin_upper_arm:40,skin_forearm:34,skin_thigh:50,skin_shin:57};
 const parts={};let totalBytes=0;
 for(const [id,p]of Object.entries(m.parts)){
  assert(!p.file.includes('..')&&!path.isAbsolute(p.file),'Asset path escaped root');
  const bytes=fs.readFileSync(path.join(dir,p.file));totalBytes+=bytes.length;
  const {data,info}=await sharp(bytes).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let foreground=0,transparent=0;
  for(let i=3;i<data.length;i+=4){if(data[i]>=16)foreground++;if(data[i]===0)transparent++;}
  assert(foreground>16&&transparent>0,`Empty or opaque rectangle ${id}`);
  assert(p.pivot.length===2&&p.pivot.every(Number.isFinite),`Bad pivot ${id}`);
  if(expectedBones[id])assert(p.tip&&Math.abs(Math.hypot(p.tip[0]-p.pivot[0],p.tip[1]-p.pivot[1])-expectedBones[id])<.01,`Bone length mismatch ${id}`);
  parts[id]={width:info.width,height:info.height,foregroundPixels:foreground,transparentPixels:transparent,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
 }
 const result={kind:'static PNG and manifest checks; not Android or visual-quality certification',parts:Object.keys(parts).length,totalPngBytes:totalBytes,frameCount:8,frameMillis:90,cycleMillis:720,manifestSha256:crypto.createHash('sha256').update(manifestBytes).digest('hex'),checks:['recipe completeness','PNG alpha/background','finite pivots','four normalized bone lengths','local asset paths','palette references','garment coverage ranges'],details:parts};
 fs.writeFileSync(path.join(root,'docs/design/avatars/v0.4.0/static-verification.json'),JSON.stringify(result,null,2)+'\n');
 console.log(JSON.stringify({parts:result.parts,totalPngBytes:totalBytes,checks:result.checks.length}));
}
main().catch(e=>{console.error(e);process.exitCode=1});
