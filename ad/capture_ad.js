const { chromium } = require('playwright');
const fs = require('fs');
const SRC = 'file://' + (process.env.AD || '/tmp/htmlad/ad.html');
const W = 3840, H = 2160;
const FPS = Number(process.env.FPS || 30);
const DURATION = Number(process.env.DURATION || 23);
const FRAMES = Math.round(FPS * DURATION);
const OUT = process.env.OUT || 'frames_ad';

(async () => {
  fs.rmSync(OUT, { recursive: true, force: true });
  fs.mkdirSync(OUT);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });

  await page.addInitScript(() => {
    let vnow = 0, queue = [];
    window.requestAnimationFrame = (cb) => { queue.push(cb); return queue.length; };
    window.cancelAnimationFrame = () => {};
    performance.now = () => vnow;
    window.__advance = (ms) => { vnow += ms; const cbs = queue; queue = []; for (const cb of cbs) { try { cb(vnow); } catch (e) {} } };
  });

  await page.goto(SRC, { waitUntil: 'load' });
  await page.waitForFunction(() => window.__ready === true, { timeout: 60000 });
  await page.evaluate(() => document.fonts.ready);

  const clip = { x: 0, y: 0, width: W, height: H };
  const STEP = 1000 / FPS;
  for (let i = 0; i < FRAMES; i++) {
    await page.evaluate(ms => window.__advance(ms), STEP);
    await page.evaluate(() => new Promise(r => setTimeout(r, 0)));
    await page.screenshot({ path: `${OUT}/f_${String(i).padStart(5,'0')}.png`, clip });
    if (i % 30 === 0) process.stdout.write(`\rframe ${i}/${FRAMES}`);
  }
  await browser.close();
  console.log(`\ndone: ${FRAMES} frames @ ${FPS}fps -> ${FRAMES/FPS}s (3840x2160)`);
})();
