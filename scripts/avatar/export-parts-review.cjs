// Mechanical layout/encoding of the passed production-Kotlin host preview.
// No character painting, retouching, interpolation or frame reordering.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const sharp=require(process.env.RUNMONI_NODE_MODULES?path.join(process.env.RUNMONI_NODE_MODULES,'sharp'):'sharp');
const dir=path.resolve(__dirname,'../../docs/design/avatars/v0.4.0'),host=path.join(dir,'host-preview');
async function main(){
 const status=JSON.parse(fs.readFileSync(path.join(host,'run-status.json')));
 if(status.status!=='passed')throw Error('A passed current host preview is required');
 const provenance=JSON.parse(fs.readFileSync(path.join(host,'source-provenance.json')));
 for(const p of [...provenance.sourceSha256,...provenance.artworkSha256]){
  const actual=crypto.createHash('sha256').update(fs.readFileSync(p.path)).digest('hex');
  if(actual!==p.sha256)throw Error(`Stale host preview for ${p.path}`);
 }
 const outputs=[];
 for(const [name,clips,columns]of [
  ['running-pair',['male','female'],2],
  ['special-sets',['male','costume_1','costume_2','costume_3','costume_4','costume_5'],3]
 ]){
  const width=columns*256,height=Math.ceil(clips.length/columns)*384,frames=[];
  for(let f=0;f<8;f++){
   const overlays=clips.map((clip,i)=>({input:path.join(host,clip,`frame_${f}.png`),left:(i%columns)*256,top:Math.floor(i/columns)*384}));
   frames.push(await sharp({create:{width,height,channels:4,background:'#f6f3ed'}}).composite(overlays).raw().toBuffer());
  }
  const output=path.join(dir,`${name}.gif`);
  await sharp(Buffer.concat(frames),{raw:{width,height:height*8,channels:4,pageHeight:height}})
   .gif({loop:0,delay:Array(8).fill(90),colours:256,dither:0,effort:3}).toFile(output);
  await sharp(frames[0],{raw:{width,height,channels:4}}).png().toFile(path.join(dir,`${name}.png`));
  const meta=await sharp(output,{animated:true}).metadata();
  if(meta.pages!==8||meta.delay.some(t=>t!==90)||meta.loop!==0)throw Error(`Incorrect GIF timing ${name}`);
  outputs.push({file:`${name}.gif`,width,height,clips,frames:8,frameMillis:90,cycleMillis:720,bytes:fs.statSync(output).size,sha256:crypto.createHash('sha256').update(fs.readFileSync(output)).digest('hex')});
 }
 fs.writeFileSync(path.join(dir,'review-verification.json'),JSON.stringify({surface:'Production Kotlin via host Java2D, NOT an Android capture',hostRunId:status.runId,outputs},null,2)+'\n');
 console.log(JSON.stringify(outputs.map(({file,bytes,frames})=>({file,bytes,frames}))));
}
main().catch(e=>{console.error(e);process.exitCode=1});
