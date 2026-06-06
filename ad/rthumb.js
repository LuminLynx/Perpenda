const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const jobs = [
    ['a','dark','thumb_a_dark.png'], ['b','dark','thumb_b_dark.png'],
    ['a','light','thumb_a_light.png'], ['b','light','thumb_b_light.png'],
  ];
  for (const [v, theme, out] of jobs) {
    const p = await b.newPage({ viewport: { width: 3840, height: 2160 }, deviceScaleFactor: 1 });
    await p.goto(`file:///tmp/htmlad/thumb.html?v=${v}&theme=${theme}`, { waitUntil: 'load' });
    await p.waitForFunction(() => window.__ready === true, { timeout: 60000 });
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: out, clip: { x:0,y:0,width:3840,height:2160 } });
    await p.close();
  }
  await b.close();
})();
