const { chromium } = require('playwright');
const path = require('path');
const SRC = 'file://' + path.join(__dirname, 'avatar.html');
(async () => {
  const b = await chromium.launch(
    process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}
  );
  for (const theme of ['cream', 'ink', 'oxblood', 'paper']) {
    const p = await b.newPage({ viewport: { width: 1024, height: 1024 }, deviceScaleFactor: 1 });
    await p.goto(`${SRC}?theme=${theme}`, { waitUntil: 'load' });
    await p.waitForFunction(() => window.__ready === true, { timeout: 60000 });
    await p.screenshot({ path: `avatar_${theme}.png`, clip: { x:0, y:0, width:1024, height:1024 } });
    await p.close();
  }
  await b.close();
})();
