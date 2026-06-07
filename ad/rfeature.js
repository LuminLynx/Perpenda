const { chromium } = require('playwright');
const path = require('path');
const SRC = 'file://' + path.join(__dirname, 'feature.html');
(async () => {
  const b = await chromium.launch(
    process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}
  );
  // Play feature graphic spec: exactly 1024x500. Render at 2x (2048x1000) for
  // crisp text, then downscale to 1024x500 in ffmpeg outside this script.
  const jobs = [
    ['a','light','feature_a_light_2x.png'], ['a','dark','feature_a_dark_2x.png'],
    ['b','light','feature_b_light_2x.png'], ['b','dark','feature_b_dark_2x.png'],
    ['a','paper','feature_a_paper_2x.png'], ['b','paper','feature_b_paper_2x.png'],
  ];
  for (const [v, theme, out] of jobs) {
    const p = await b.newPage({ viewport: { width: 2048, height: 1000 }, deviceScaleFactor: 1 });
    await p.goto(`${SRC}?v=${v}&theme=${theme}`, { waitUntil: 'load' });
    await p.waitForFunction(() => window.__ready === true, { timeout: 60000 });
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: out, clip: { x:0,y:0,width:2048,height:1000 } });
    await p.close();
  }
  await b.close();
})();
