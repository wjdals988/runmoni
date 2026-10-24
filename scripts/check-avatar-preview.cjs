// Offline browser verification for the generated wardrobe preview.
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const packages=process.env.RUNMONI_NODE_MODULES;
const {chromium}=require(packages?path.join(packages,'playwright'):'playwright');
const sharp=require(packages?path.join(packages,'sharp'):'sharp');
const root=path.resolve(__dirname,'..');
const dir=path.join(root,'docs/design/avatars/v0.1.0');
async function main(){
  const gif=await sharp(path.join(dir,'running-pair-preview.gif'),{animated:true}).metadata();
  assert.equal(gif.pages,4);assert.equal(gif.loop,0);assert.deepEqual(gif.delay,[120,120,120,120]);
  const browser=await chromium.launch({channel:'msedge',headless:true});
  const page=await browser.newPage({viewport:{width:736,height:960}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('http://**/*',r=>r.abort());await page.route('https://**/*',r=>r.abort());
  const fragment=fs.readFileSync(path.join(dir,'wardrobe-preview.html'),'utf8');
  await page.setContent('<style>:root{--foreground:#172a31}body{font:16px system-ui;background:#fff9ef;margin:20px}select,button{font:inherit;padding:8px}h3,h4{margin:8px 0}</style>'+fragment);
  await page.waitForSelector('[data-ready="true"]');
  const before=await page.locator('#runmoni-avatar-v010').getAttribute('data-frame');
  await page.waitForTimeout(160);
  const after=await page.locator('#runmoni-avatar-v010').getAttribute('data-frame');
  assert.notEqual(before,after,'Run animation must advance');
  await page.getByRole('button',{name:'일시정지',exact:true}).click();
  const paused=await page.locator('#runmoni-avatar-v010').getAttribute('data-frame');
  await page.waitForTimeout(200);
  assert.equal(await page.locator('#runmoni-avatar-v010').getAttribute('data-frame'),paused,'Pause must freeze frame');
  let selections=0;
  for(let runner=0;runner<2;runner++){
    const images=new Set();
    for(let hair=0;hair<2;hair++)for(let outfit=0;outfit<2;outfit++){
      for(const [slot,value] of Object.entries({hair,outfit}))await page.locator(`select[data-runner="${runner}"][data-slot="${slot}"]`).selectOption(String(value));
      images.add(await page.locator('canvas').nth(runner).evaluate(c=>c.toDataURL()));selections++;
    }
    assert.equal(images.size,4,'Each runner must display four distinct combinations');
  }
  for(const [runner,values] of [[0,{hair:0,outfit:0}],[1,{hair:1,outfit:1}]])for(const [slot,value]of Object.entries(values))await page.locator(`select[data-runner="${runner}"][data-slot="${slot}"]`).selectOption(String(value));
  const output=path.join(root,'work/avatar-preview');fs.mkdirSync(output,{recursive:true});
  await page.screenshot({path:path.join(output,'wardrobe-desktop.png'),fullPage:true});
  await page.locator('select[data-runner="0"][data-slot="hair"]').selectOption('1');
  await page.locator('select[data-runner="1"][data-slot="outfit"]').selectOption('0');
  await page.screenshot({path:path.join(output,'wardrobe-swapped.png'),fullPage:true});
  await page.setViewportSize({width:320,height:900});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'320px must not overflow horizontally');
  await page.screenshot({path:path.join(output,'wardrobe-mobile.png'),fullPage:true});
  assert.equal(errors.length,0,errors.join('\n'));
  await browser.close();
  console.log(JSON.stringify({gifFrames:gif.pages,frameDelayMs:gif.delay[0],combinationChecks:selections,animationAdvances:true,pauseWorks:true,mobileWidth:320,browserErrors:errors.length,screenshots:output}));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
