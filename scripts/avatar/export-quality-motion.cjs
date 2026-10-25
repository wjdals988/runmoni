// Mechanical sprite-grid extraction; no character painting, retouching or frame realignment.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require(process.env.RUNMONI_NODE_MODULES ? path.join(process.env.RUNMONI_NODE_MODULES,'sharp') : 'sharp');
const crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const dir=path.join(root,'docs/design/avatars/v0.3.0');
async function main(){
 const source=path.join(dir,'running-motion-art-source.png');
 const metadata=await sharp(source).metadata();
 if(metadata.width!==2048||metadata.height!==768)throw new Error('Unexpected grid dimensions');
 const frames=[];const hashes=[];
 for(let f=0;f<8;f++){
   const male=await sharp(source).extract({left:f*256,top:0,width:256,height:384}).png().toBuffer();
   const female=await sharp(source).extract({left:f*256,top:384,width:256,height:384}).png().toBuffer();
   const page=await sharp({create:{width:512,height:384,channels:4,background:'#00000000'}}).composite([{input:male,left:0,top:0},{input:female,left:256,top:0}]).raw().toBuffer();
   frames.push(page);hashes.push(crypto.createHash('sha256').update(page).digest('hex'));
 }
 const output=path.join(dir,'running-motion-review.gif');
 await sharp(Buffer.concat(frames),{raw:{width:512,height:384*8,channels:4,pageHeight:384}}).gif({loop:0,delay:Array(8).fill(90),colours:256,dither:0,effort:3}).toFile(output);
 const result=await sharp(output,{animated:true}).metadata();
 if(result.pages!==8||result.delay.some(t=>t!==90)||result.loop!==0)throw new Error('Animation metadata mismatch');
 const verification={sourceWidth:metadata.width,sourceHeight:metadata.height,cellWidth:256,cellHeight:384,frames:8,uniquePixelFrames:new Set(hashes).size,frameMillis:90,cycleMillis:720,loop:0,bytes:fs.statSync(output).size,sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'),frameHashes:hashes,stage:'raw visual motion proposal, mechanical grid extraction only; not a production-ready aligned sprite or Android capture',appIntegrated:false};
 fs.writeFileSync(path.join(dir,'preview-verification.json'),JSON.stringify(verification,null,2)+'\n');
 console.log(JSON.stringify({output,frames:result.pages,unique:verification.uniquePixelFrames,bytes:verification.bytes}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
