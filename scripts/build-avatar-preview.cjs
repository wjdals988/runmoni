// Asset export only: all source artwork is generated with the built-in image tool.
// No network, downloads, Aseprite installation, or Android build is performed.
const fs=require('node:fs');
const path=require('node:path');
const sharp=require(process.env.RUNMONI_NODE_MODULES ? path.join(process.env.RUNMONI_NODE_MODULES,'sharp') : 'sharp');
const root=path.resolve(__dirname,'..');
const dir=path.join(root,'docs/design/avatars/v0.1.0');
const input=path.join(dir,'run-atlas-source.png');
async function main(){
  const meta=await sharp(input).metadata();
  if(!meta.hasAlpha)throw new Error('Atlas must retain alpha');
  // Format export for a compact self-contained offline preview, keep source PNG intact.
  const webp=await sharp(input).webp({quality:92,alphaQuality:100,effort:4}).toBuffer();
  fs.writeFileSync(path.join(dir,'run-atlas-preview.webp'),webp);
  const template=fs.readFileSync(path.join(dir,'preview.fragment.html'),'utf8');
  const fragment=template.replace('__ATLAS_DATA_URI__','data:image/webp;base64,'+webp.toString('base64'));
  if(fragment.includes('__ATLAS_DATA_URI__')||Buffer.byteLength(fragment)>1e6)throw new Error('Inline preview export invalid/over 1 MB');
  fs.writeFileSync(path.join(dir,'wardrobe-preview.html'),fragment);
  const vizDir=process.argv[2];
  if(vizDir)fs.writeFileSync(path.join(vizDir,'runmoni-rpg-wardrobe-v010.html'),fragment);
  // Assemble the generated source frames into a real looping GIF; do not redraw artwork.
  const pages=[];
  for(let frame=0;frame<4;frame++){
    const parts=[];
    for(let row=0;row<2;row++){
      const x0=Math.round(frame*meta.width/4),x1=Math.round((frame+1)*meta.width/4);
      const y0=Math.round(row*meta.height/2),y1=Math.round((row+1)*meta.height/2);
      const png=await sharp(input).extract({left:x0,top:y0,width:x1-x0,height:y1-y0}).resize(240,240,{kernel:'nearest',fit:'fill'}).png().toBuffer();
      parts.push({input:png,left:row*256+8,top:20});
    }
    pages.push(await sharp({create:{width:512,height:280,channels:4,background:'#fff7ed'}}).composite(parts).raw().toBuffer());
  }
  const gif=await sharp(Buffer.concat(pages),{raw:{width:512,height:1120,channels:4,pageHeight:280}}).gif({loop:0,delay:[120,120,120,120],colours:256,dither:0,effort:4}).toBuffer();
  fs.writeFileSync(path.join(dir,'running-pair-preview.gif'),gif);
  console.log(JSON.stringify({source:{width:meta.width,height:meta.height,hasAlpha:meta.hasAlpha},webpBytes:webp.length,fragmentBytes:Buffer.byteLength(fragment),gifBytes:gif.length}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
