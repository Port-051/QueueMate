import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite, render404} from '../src/render.mjs';
import {attributes, noindex, validWebP, validateOrigin, checkDeployment} from '../scripts/check-url.mjs';
const config = JSON.parse(await readFile(new URL('../site.config.json', import.meta.url)));
// Image-enabled fixture keeps the image integrity cases independent of release settings.
config.media = {...config.media, ogImage: '/assets/og-cover.webp'};
const image = await readFile(new URL('../public/assets/og-cover.webp', import.meta.url));
const origin = 'https://qmate-audit.vercel.app';
function mock({production = false, edit = () => {}} = {}) {
  const c = production ? {...config, contentApproved: true, uiApproved: true, allowIndexing: true} : config;
  const r = renderSite(c, {VERCEL_ENV: production ? 'production' : 'preview'});
  const responses = new Map([
    ['/', [r.html, 200, 'text/html']], ['/robots.txt', [r.robots, 200, 'text/plain']],
    ['/sitemap.xml', [r.sitemap || render404(), r.sitemap ? 200 : 404, r.sitemap ? 'application/xml' : 'text/html']],
    ['/assets/site.css', ['body{margin:0}', 200, 'text/css']],
    ['/assets/concise.css', ['.hero{min-height:650px}', 200, 'text/css']],
    ['/assets/queuemate-wordmark.svg', ['<svg xmlns="http://www.w3.org/2000/svg"></svg>', 200, 'image/svg+xml']],
    ['/assets/og-cover.webp', [image, 200, 'image/webp']],
    ['/__qmate_expected_missing__', [render404(), 404, 'text/html']],
  ]);
  edit(responses);
  return async (url, options) => {
    assert.equal(options.method, 'GET'); assert.equal(options.redirect, 'manual'); assert.equal(options.credentials, 'omit');
    const item = responses.get(url.pathname); assert.ok(item, `Unexpected URL: ${url}`);
    const [body, status, type, headers = {}] = item;
    return new Response(body, {status, headers: {'content-type': type, 'x-content-type-options': 'nosniff', ...headers}});
  };
}
test('audit passes an anonymous unindexed preview', async () => {
  const r = await checkDeployment(origin, 'preview', config, mock());
  assert.equal(r.classification, 'PASS'); assert.equal(r.checks.length, 18);
});
test('audit passes a deliberately approved canonical release', async () => {
  assert.equal((await checkDeployment(config.origin, 'production', config, mock({production: true}))).classification, 'PASS');
});
for (const status of [302, 401, 403]) test(`authentication response ${status} stops without following or inspecting assets`, async () => {
  let calls = 0;
  const f = async (_, options) => {calls++; assert.equal(options.redirect, 'manual'); return new Response('', {status, headers: {location: 'https://vercel.com/login?next=private'}});};
  const r = await checkDeployment(origin, 'preview', config, f);
  assert.equal(r.classification, 'BLOCKED_AUTH'); assert.equal(calls, 1); assert.equal(JSON.stringify(r).includes('private'), false);
});
for (const [name, edit] of [
  ['login HTML returned as 200', m => m.get('/')[0] = '<html><h1>Log in to Vercel</h1></html>'],
  ['HTML disguised as a WebP', m => m.get('/assets/og-cover.webp')[0] = '<html>not an image</html>'],
  ['truncated WebP', m => m.get('/assets/og-cover.webp')[0] = image.subarray(0, image.length - 1)],
  ['fake successful 404 page', m => m.get('/__qmate_expected_missing__')[1] = 200],
  ['missing CSS', m => m.get('/assets/site.css')[1] = 404],
  ['missing concise CSS', m => m.get('/assets/concise.css')[1] = 404],
  ['screenshot disclosures mistaken for FAQs', m => m.get('/')[0] = m.get('/')[0].replaceAll('class="faq-item"', 'class="screen-details"')],
  ['duplicate canonical', m => m.get('/')[0] += `<link rel="canonical" href="${config.origin}/">`],
  ['invalid JSON-LD', m => m.get('/')[0] = m.get('/')[0].replace('"@context"', 'INVALID')],
]) test(`audit rejects ${name}`, async () => {
  assert.equal((await checkDeployment(origin, 'preview', config, mock({edit}))).classification, 'CHECK_FAILED');
});
for (const value of ['noindex', 'NONE', 'googlebot: noindex']) test(`release rejects restrictive header ${value}`, async () => {
  const f = mock({production: true, edit: m => m.get('/')[3] = {'x-robots-tag': value}});
  assert.equal((await checkDeployment(config.origin, 'production', config, f)).classification, 'CHECK_FAILED');
});
test('release rejects a crawler-specific noindex meta', async () => {
  const f = mock({production: true, edit: m => m.get('/')[0] += '<meta content="NOINDEX" name="Googlebot">'});
  assert.equal((await checkDeployment(config.origin, 'production', config, f)).classification, 'CHECK_FAILED');
});
test('quoted attribute order and case do not determine the result', () => {
  assert.deepEqual(attributes("<meta CONTENT='noindex' NAME=\"robots\">"), {content: 'noindex', name: 'robots'});
  assert.equal(noindex('noindexing'), false); assert.equal(noindex('index, follow'), false); assert.equal(noindex('none'), true);
});
test('WebP is validated from bytes rather than the MIME header', () => {assert.ok(validWebP(image)); assert.equal(validWebP(Buffer.alloc(0)), false);});
test('network errors remain failures', async () => {
  const r = await checkDeployment(origin, 'preview', config, async () => {throw Error('network unavailable');});
  assert.equal(r.classification, 'CHECK_FAILED'); assert.equal(r.checks[0].pass, false);
});
test('invalid deployment origins are rejected before network I/O', () => {
  for (const input of ['http://example.com', 'https://u:p@example.com', 'https://example.com/a', 'https://example.com/?token=x'])
    assert.throws(() => validateOrigin(input, 'preview', config));
  assert.throws(() => validateOrigin(origin, 'production', config));
  assert.throws(() => validateOrigin(origin, 'unknown', config));
  assert.equal(validateOrigin('http://127.0.0.1:4173', 'preview', config).port, '4173');
});
