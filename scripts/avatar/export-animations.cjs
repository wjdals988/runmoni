// Export animations from actual Android capture frames and the exact app raster.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require(process.env.RUNMONI_NODE_MODULES ? path.join(process.env.RUNMONI_NODE_MODULES,'sharp') : 'sharp');
const root=path.resolve(__dirname,'../..');
const output=path.join(root,'docs/design/avatars/v0.2.0');
const input=path.resolve(process.argv[2]||path.join(root,'work/avatar-native-sticky'));
async function main(){
  const native=path.join(output,'native-screenshots');fs.mkdirSync(native,{recursive:true});
  for(const name of ['closet-running-gear.png','closet-santa.png'])fs.copyFileSync(path.join(input,name),path.join(native,name));
  const pages=[];
  for(let f=0;f<8;f++){
    const file=path.join(input,`native-run-${f}.png`);
    fs.copyFileSync(file,path.join(native,`native-run-${f}.png`));
    pages.push(await sharp(file).resize(384,448,{kernel:'nearest'}).flatten({background:'#fff8ef'}).ensureAlpha().raw().toBuffer());
  }
  await sharp(Buffer.concat(pages),{raw:{width:384,height:448*8,channels:4,pageHeight:448}})
    .gif({loop:0,delay:Array(8).fill(90),colours:128,dither:0,effort:4}).toFile(path.join(output,'native-running-8frames.gif'));
  const atlas=path.join(output,'costume-running-atlas-v2.png');const costume=[];
  for(let f=0;f<8;f++){
    const parts=[];
    for(const [i,row]of [1,4,5].entries()){
      parts.push({input:await sharp(atlas).extract({left:f*96,top:row*112,width:96,height:112}).resize(192,224,{kernel:'nearest'}).png().toBuffer(),left:i*192,top:0});
    }
    costume.push(await sharp({create:{width:576,height:224,channels:4,background:'#fff8ef'}}).composite(parts).raw().toBuffer());
  }
  await sharp(Buffer.concat(costume),{raw:{width:576,height:224*8,channels:4,pageHeight:224}})
    .gif({loop:0,delay:Array(8).fill(90),colours:128,dither:0,effort:4}).toFile(path.join(output,'costume-running-8frames.gif'));
  for(const name of ['native-running-8frames.gif','costume-running-8frames.gif']){
    const m=await sharp(path.join(output,name),{animated:true}).metadata();
    if(m.pages!==8||m.delay.some(d=>d!==90)||m.loop!==0)throw new Error('Animation metadata mismatch');
    console.log(JSON.stringify({file:name,frames:m.pages,delayMs:90,loop:0,bytes:fs.statSync(path.join(output,name)).size}));
  }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
