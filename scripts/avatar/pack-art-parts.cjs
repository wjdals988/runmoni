// Mechanical extraction and nearest-neighbour sizing of generated PNG rig parts.
// Artwork is authored with image_gen; this script does not paint character art.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require(process.env.RUNMONI_NODE_MODULES ? path.join(process.env.RUNMONI_NODE_MODULES, 'sharp') : 'sharp');
const root = path.resolve(__dirname, '../..');
const sourceDir = path.join(root, 'docs/design/avatars/v0.4.0');
const outputDir = path.join(root, 'app/src/main/assets/avatars/v0.4.0');
const hash = data => crypto.createHash('sha256').update(data).digest('hex');

async function pack() {
  const config = JSON.parse(fs.readFileSync(path.join(sourceDir, 'parts-layout.json'), 'utf8'));
  fs.mkdirSync(outputDir, {recursive: true});
  const manifest = {...config.manifest, parts: {}};
  const evidence = {tool: 'built-in image_gen artwork; mechanical alpha-bound crop and nearest-neighbour resize', sources: {}, parts: {}};
  for (const sheet of config.sheets) {
    const source = path.join(sourceDir, sheet.file);
    const bytes = fs.readFileSync(source);
    const {data, info} = await sharp(bytes).ensureAlpha().raw().toBuffer({resolveWithObject: true});
    // Model output sizes can be a few pixels off an integral grid. Cell edges
    // are rounded independently; do not rescale or blur the complete source.
    evidence.sources[sheet.file] = {sha256: hash(bytes), width: info.width, height: info.height};
    const cw = info.width / sheet.columns, ch = info.height / sheet.rows;
    for (const entry of sheet.parts) {
      const col = entry.cell % sheet.columns, row = Math.floor(entry.cell / sheet.columns);
      const region = entry.region || [0, 0, 1, 1];
      const x0 = Math.max(0,Math.round(col*cw + region[0]*cw)), y0 = Math.max(0,Math.round(row*ch + region[1]*ch));
      const x1 = Math.min(info.width,Math.round(col*cw + region[2]*cw)), y1 = Math.min(info.height,Math.round(row*ch + region[3]*ch));
      let left = x1, top = y1, right = -1, bottom = -1, opaque = 0;
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        if (data[(y*info.width+x)*4+3] >= 16) {
          left = Math.min(left,x); right = Math.max(right,x); top = Math.min(top,y); bottom = Math.max(bottom,y); opaque++;
        }
      }
      if (!opaque) throw Error(`Empty part ${entry.id}`);
      const png = await sharp(bytes).extract({left,top,width:right-left+1,height:bottom-top+1})
        .resize(entry.size[0],entry.size[1],{fit:'fill',kernel:'nearest'}).png().toBuffer();
      const file = `${entry.id}.png`;
      fs.writeFileSync(path.join(outputDir,file),png);
      manifest.parts[entry.id] = {file,pivot:entry.pivot,...(entry.tip?{tip:entry.tip}:{}),...(entry.sourcePalette?{sourcePalette:entry.sourcePalette}:{})};
      evidence.parts[entry.id] = {source:sheet.file,cell:entry.cell,alphaBounds:[left,top,right+1,bottom+1],size:entry.size,pivot:entry.pivot,sha256:hash(png),bytes:png.length};
    }
  }
  for (const [id, source] of Object.entries(config.aliases || {})) {
    if (!manifest.parts[source]) throw Error(`Missing alias source ${source}`);
    manifest.parts[id] = {...manifest.parts[source]};
  }
  fs.writeFileSync(path.join(outputDir,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  evidence.totalPngBytes = Object.values(evidence.parts).reduce((sum,p)=>sum+p.bytes,0);
  evidence.partCount = Object.keys(manifest.parts).length;
  fs.writeFileSync(path.join(sourceDir,'packing-verification.json'),JSON.stringify(evidence,null,2)+'\n');
  console.log(JSON.stringify({partCount:evidence.partCount,totalPngBytes:evidence.totalPngBytes,outputDir}));
}
pack().catch(error => {console.error(error);process.exitCode=1;});
