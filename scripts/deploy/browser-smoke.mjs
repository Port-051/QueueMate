import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {chromium} from 'playwright';
export async function browserSmoke(origin, kind) {
  assert.equal(origin, kind === 'app' ? 'https://app.queue-mate.com' : 'https://queue-mate.com');
  await mkdir('.release/reports', {recursive:true});
  const browser = await chromium.launch();
  const checks = [];
  try {
    for (const width of [1440, 390]) {
      const page = await browser.newPage({viewport:{width, height:900}});
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      for (const route of kind === 'app' ? ['/', '/login'] : ['/']) {
        const response = await page.goto(origin + route, {waitUntil:'networkidle', timeout:45000});
        assert.equal(response.status(), 200);
        const selector = kind === 'app' ? 'img.logo-mark' : 'img.brand-symbol';
        await page.locator(selector).first().waitFor();
        const logos = await page.locator(selector).evaluateAll(images => images.map(i => ({loaded:i.complete && i.naturalWidth > 0, src:i.getAttribute('src')})));
        assert.ok(logos.length > 0 && logos.every(i => i.loaded && i.src.includes('queuemate-mark.svg')), 'Canonical logo did not render');
        const icons = await page.locator('link[rel="icon"]').count();
        assert.ok(icons > 0, 'Favicon link missing');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, 'Horizontal overflow');
        assert.deepEqual(errors, [], 'Uncaught browser error');
        await page.screenshot({path:`.release/reports/${kind}-${width}-${route === '/' ? 'home' : 'login'}.png`});
        checks.push({route, width, logos:logos.length, icons});
      }
      await page.close();
    }
    return checks;
  } finally { await browser.close(); }
}
