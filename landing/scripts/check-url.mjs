/** Anonymous, GET-only deployment audit. Importing this module never performs I/O. */
import {readFile, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

// Deliberately limited to our generated HTML; not a general-purpose HTML parser.
export function attributes(tag) {
  return Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)]
    .map(([, key, double, single]) => [key.toLowerCase(), double ?? single]));
}
export function tags(html, name) {
  return [...html.matchAll(new RegExp(`<${name}\\b[^>]*>`, 'gi'))].map(([tag]) => attributes(tag));
}
export function noindex(value = '') { return /(?:^|[\s,:])(noindex|none)(?:$|[\s,])/i.test(value); }
export function validateOrigin(input, mode, config) {
  if (!['preview', 'production'].includes(mode)) throw Error('Mode must be preview or production');
  const url = new URL(input);
  const local = mode === 'preview' && url.protocol === 'http:' && url.hostname === '127.0.0.1';
  if ((!local && url.protocol !== 'https:') || url.username || url.password || url.pathname !== '/' || url.search || url.hash)
    throw Error('Use a bare HTTPS origin (127.0.0.1 HTTP is allowed for local preview only)');
  if (mode === 'production' && url.origin !== new URL(config.origin).origin)
    throw Error('Production checks must target the canonical domain');
  return url;
}
export function validWebP(bytes) {
  return bytes.length >= 30 && bytes.toString('ascii', 0, 4) === 'RIFF' &&
    bytes.toString('ascii', 8, 12) === 'WEBP' && bytes.readUInt32LE(4) + 8 === bytes.length &&
    bytes.toString('ascii', 12, 16) === 'VP8 ' &&
    (bytes.readUInt16LE(26) & 0x3fff) === 1200 && (bytes.readUInt16LE(28) & 0x3fff) === 630;
}
export async function checkDeployment(input, mode, config, fetcher = fetch) {
  const origin = validateOrigin(input, mode, config);
  const canonical = new URL(config.origin).origin + '/';
  const report = {checkedAt: new Date().toISOString(), origin: origin.origin, mode,
    classification: 'CHECK_FAILED', checks: [], warnings: []};
  const check = (name, pass) => report.checks.push({name, pass: Boolean(pass)});
  const get = p => fetcher(new URL(p, origin), {method: 'GET', redirect: 'manual',
    credentials: 'omit', signal: AbortSignal.timeout(10000)});
  try {
    const home = await get('/');
    report.homeStatus = home.status;
    const location = home.headers.get('location');
    const destination = location ? new URL(location, origin) : null;
    const authRedirect = destination?.hostname === 'vercel.com' && /login|sso|auth/i.test(destination.pathname);
    if ([401, 403].includes(home.status) || (home.status >= 300 && home.status < 400 && authRedirect)) {
      await home.body?.cancel();
      report.classification = 'BLOCKED_AUTH';
      check('Homepage is public without login', false);
      return report; // Never follow authentication redirects or audit a login page as the site.
    }
    const html = await home.text();
    check('Home is 200 HTML', home.status === 200 && /text\/html/i.test(home.headers.get('content-type') || ''));
    check('Korean content and exactly one H1', /<html\b[^>]*lang=["']ko["']/i.test(html) &&
      (html.match(/<h1\b/gi) || []).length === 1 && /롤 듀오/.test(html) && /QueueMate/.test(html));
    const metas = tags(html, 'meta'), links = tags(html, 'link');
    const robots = metas.filter(t => /^(robots|googlebot|bingbot|yeti)$/i.test(t.name || '')).map(t => t.content || '');
    const header = home.headers.get('x-robots-tag') || '';
    const blocked = robots.some(noindex) || noindex(header);
    check('Indexing mode', mode === 'production' ? !blocked : blocked);
    check('Title and description', /<title>[^<]+<\/title>/i.test(html) &&
      metas.filter(t => t.name?.toLowerCase() === 'description' && t.content?.trim()).length === 1);
    check('Exactly one canonical URL', links.filter(t => t.rel?.toLowerCase() === 'canonical').length === 1 &&
      links.some(t => t.rel?.toLowerCase() === 'canonical' && t.href === canonical));
    check('MIME sniffing disabled', home.headers.get('x-content-type-options')?.toLowerCase() === 'nosniff');
    const ids = [...html.matchAll(/\bid=["']([^"']+)["']/g)].map(m => m[1]);
    check('No duplicate IDs', new Set(ids).size === ids.length);
    check('Anchor destinations exist', 
      [...html.matchAll(/href=["']#([^"']+)["']/g)].every(m => ids.includes(m[1])));
    check('Three native FAQs (excluding screenshot disclosures)', tags(html, 'details').filter(t => (t.class || '').split(/\s+/).includes('faq-item')).length === 3);
    const data = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
      .filter(m => attributes(m[1]).type?.toLowerCase() === 'application/ld+json');
    let schemaOK = false;
    try {const schema = JSON.parse(data[0]?.[2] || ''); schemaOK = data.length === 1 && schema['@type'] === 'WebSite' && schema.url === canonical;}
    catch { /* Invalid JSON is an audit failure, never a reason to skip the check. */ }
    check('Valid WebSite JSON-LD', schemaOK);
    check('App connection matches release state', config.appReady || !tags(html, 'a').some(a => a.href?.startsWith(config.appUrl)));
    const robotsResponse = await get('/robots.txt'), robotsText = await robotsResponse.text();
    check('robots.txt is readable', robotsResponse.status === 200 && /text\/plain/i.test(robotsResponse.headers.get('content-type') || '') &&
      /^Allow:\s*\//mi.test(robotsText) && !/^Disallow:\s*\/\s*$/mi.test(robotsText));
    const sitemapResponse = await get('/sitemap.xml'), sitemap = await sitemapResponse.text();
    check('Sitemap matches release mode', mode === 'production'
      ? sitemapResponse.status === 200 && /xml/i.test(sitemapResponse.headers.get('content-type') || '') &&
        sitemap.includes(`<loc>${canonical}</loc>`) && robotsText.includes(`Sitemap: ${canonical}sitemap.xml`)
      : sitemapResponse.status === 404);
    const paths = [['/assets/site.css', 'text/css'], ['/assets/concise.css', 'text/css'], ['/assets/queuemate-wordmark.svg', 'image/svg+xml'],
      ...(config.media?.ogImage ? [[config.media.ogImage, 'image/']] : [])];
    for (const [p, type] of paths) {
      const response = await get(p), bytes = Buffer.from(await response.arrayBuffer());
      let valid = bytes.length > 0 && !/^\s*<!doctype html|^\s*<html/i.test(bytes.toString('utf8', 0, 80));
      if (p.endsWith('.webp')) valid = valid && validWebP(bytes);
      if (p.endsWith('.svg')) valid = valid && /<svg\b/.test(bytes.toString('utf8'));
      check(`Asset ${p} has correct type and valid content`, response.status === 200 &&
        (response.headers.get('content-type') || '').toLowerCase().includes(type) && valid);
    }
    const missing = await get('/__qmate_expected_missing__'), missingHTML = await missing.text();
    check('Genuine noindex HTTP 404', missing.status === 404 && tags(missingHTML, 'meta')
      .some(m => m.name?.toLowerCase() === 'robots' && noindex(m.content)));
    if (mode === 'preview') report.warnings.push('Unindexed preview: no search ranking, indexing or field Core Web Vitals claims.');
    report.classification = report.checks.every(c => c.pass) ? 'PASS' : 'CHECK_FAILED';
  } catch (error) {check(`Network or parsing failure: ${error.message}`, false);}
  return report;
}
async function main() {
  const [input, mode = 'preview', output] = process.argv.slice(2);
  if (!input) throw Error('npm run check:url -- https://HOST preview|production [report.json]');
  const config = JSON.parse(await readFile(new URL('../site.config.json', import.meta.url), 'utf8'));
  const report = await checkDeployment(input, mode, config);
  for (const c of report.checks) console.log(`${c.pass ? 'PASS' : 'FAIL'} ${c.name}`);
  console.log(`${report.classification}: ${report.origin} (${report.mode})`);
  if (output) await writeFile(output, JSON.stringify(report, null, 2) + '\n');
  if (report.classification !== 'PASS') process.exitCode = report.classification === 'BLOCKED_AUTH' ? 2 : 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch(e => {console.error(e.message); process.exitCode = 1;});
