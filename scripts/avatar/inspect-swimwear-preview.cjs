// Read-only artwork inspection; never draws, recolors, resizes or overwrites PNG files.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require(process.env.RUNMONI_SHARP_MODULE || 'sharp');
const root = path.resolve(__dirname, '../..');
const folder = path.join(root, 'docs/design/avatars/v0.5.0');
const names = ['swimwear-mint-coral.png', 'swimwear-blue-purple.png'];
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

(async () => {
  const images = [];
  for (const name of names) {
    const file = path.join(folder, name);
    const png = fs.readFileSync(file);
    const { data, info } = await sharp(png).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let alphaMin = 255, alphaMax = 0, transparentPixels = 0, visiblePixels = 0;
    const bounds = [info.width, info.height, -1, -1];
    for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
      const a = data[(y * info.width + x) * 4 + 3];
      alphaMin = Math.min(alphaMin, a); alphaMax = Math.max(alphaMax, a);
      if (a === 0) transparentPixels++;
      if (a >= 16) {
        visiblePixels++;
        bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
      }
    }
    images.push({ name, data, info, details: { file: name, sha256: digest(png), bytes: png.length,
      width: info.width, height: info.height, channels: info.channels, alphaMin, alphaMax,
      transparentPixels, visiblePixels, alpha16Bounds: bounds } });
  }
  const [a, b] = images;
  const sameSize = a.info.width === b.info.width && a.info.height === b.info.height;
  const faceChecks = [];
  if (sameSize) {
    // Fixed face-only rectangles in normalized preview coordinates, chosen by visual inspection.
    // These compare generated colorways, not either image to the approved 0.3 identity.
    for (const [name, box] of [['maleFace', [.18,.205,.402,.405]], ['femaleFace', [.695,.265,.915,.465]]]) {
      const [left, top, right, bottom] = box.map((v, i) => Math.round(v * (i % 2 ? a.info.height : a.info.width)));
      let opaqueCompared = 0, pixelsOverRgbDelta3 = 0, maxRgbDelta = 0, alphaDifferences = 0;
      for (let y = top; y < bottom; y++) for (let x = left; x < right; x++) {
        const i = (y * a.info.width + x) * 4;
        if (a.data[i+3] !== b.data[i+3]) alphaDifferences++;
        if (a.data[i+3] < 250 || b.data[i+3] < 250) continue;
        opaqueCompared++;
        const delta = Math.max(...[0,1,2].map(c => Math.abs(a.data[i+c]-b.data[i+c])));
        maxRgbDelta = Math.max(maxRgbDelta, delta);
        if (delta > 3) pixelsOverRgbDelta3++;
      }
      faceChecks.push({ name, box: [left,top,right,bottom], opaqueCompared, pixelsOverRgbDelta3,
        maxRgbDelta, alphaDifferences, exactIdentityProven: false });
    }
  }
  const report = {
    inspectedAt: new Date().toISOString(), mode: 'read-only PNG metadata and generated-colorway face-region comparison',
    images: images.map(i => i.details), sameSize, faceChecks,
    productionReady: false, humanApproved: false, appIntegrated: false,
    limitations: [
      'Both PNGs are whole-character image_gen proposals. Neither is an eight-frame animation.',
      'The face-region comparison does not prove identity equivalence to the 0.3 approved reference.',
      'Generated garment recoloring is not a runtime mask/palette implementation; non-garment pixels can change.',
      'No Android capture, device verification, build or public release was performed for these images.'
    ]
  };
  fs.writeFileSync(path.join(folder, 'preview-inspection.json'), JSON.stringify(report, null, 2)+'\n');
  console.log(JSON.stringify(report, null, 2));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
