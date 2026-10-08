import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const expected={google:'g6PhcX7ftM9vZGQDFn5XqL0iQIzSfHbwhmUF7HP8kW8',naver:'83c067fdaf3534e3e9a2c0099b48467bad304fba',bing:''};
const body=html=>{const m=html.match(/<body>[\s\S]*<\/body>/);assert.ok(m);return m[0];};
test('ownership configuration contains only the two user-provided tokens',()=>{
  assert.deepEqual(c.verification,expected);
});
for(const engine of ['google','naver'])test(`${engine} token appears exactly once in the initial HTML head`,()=>{
  const html=renderSite(c,{VERCEL_ENV:'production'}).html;
  const head=html.match(/<head>[\s\S]*?<\/head>/)?.[0];assert.ok(head);
  const tag=`<meta name="${engine}-site-verification" content="${expected[engine]}">`;
  assert.ok(head.includes(tag));assert.equal(html.split(tag).length-1,1);
  assert.ok(html.indexOf(tag)<html.indexOf('<body>'));
  assert.ok(!body(html).includes(expected[engine]));
});
test('verification does not modify the approved body, crawler files or app state',()=>{
  const withTags=renderSite(c,{VERCEL_ENV:'production'});
  const withoutTags=renderSite({...c,verification:{google:'',naver:'',bing:''}},{VERCEL_ENV:'production'});
  assert.equal(body(withTags.html),body(withoutTags.html));
  for(const key of ['robots','sitemap','llms'])assert.equal(withTags[key],withoutTags[key]);
  assert.equal(c.allowIndexing,true);assert.equal(c.appReady,true);
  assert.match(withTags.html,/content="index, follow, max-image-preview:large"/);
  assert.match(withTags.html,/href="https:\/\/app\.queue-mate\.com\/login"/);
});
test('adding verification does not expose preview deployments to indexing',()=>{
  const r=renderSite(c,{VERCEL_ENV:'preview'});
  assert.equal(r.mode.indexable,false);assert.equal(r.sitemap,null);
  assert.match(r.html,/content="noindex, nofollow"/);
});
