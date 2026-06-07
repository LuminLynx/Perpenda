const { chromium } = require('playwright');
const path = require('path');
const SRC = 'file://' + path.join(__dirname, 'card.html');
const theme = process.env.THEME || 'light';
const out = process.env.OUT || 'card_2x.png';
(async () => {
  const b = await chromium.launch(
    process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}
  );
  const p = await b.newPage({ viewport: { width: 2160, height: 2700 }, deviceScaleFactor: 1 });
  await p.goto(`${SRC}?theme=${theme}`, { waitUntil: 'load' });
  await p.waitForFunction(() => window.__ready === true, { timeout: 60000 });
  await p.evaluate(() => document.fonts.ready);
  await p.screenshot({ path: out, clip: { x:0, y:0, width:2160, height:2700 } });
  await b.close();
})();
