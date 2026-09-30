import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const c=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const body=html=>{const match=html.match(/<body>[\s\S]*<\/body>/);assert.ok(match);return match[0];};

test('approved production landing is indexable before the matching app launches',()=>{
  const r=renderSite(c,{VERCEL_ENV:'production'});
  assert.equal(c.allowIndexing,true);assert.equal(c.appReady,false);
  assert.equal(r.mode.indexable,true);
  assert.match(r.html,/<meta name="robots" content="index, follow, max-image-preview:large">/);
  assert.match(r.html,/서비스 준비 중/);
  assert.match(r.html,/href="#preview" data-cta="explore-preview"/);
  assert.doesNotMatch(r.html,/href="https:\/\/app\.queue-mate\.com/);
});
test('published sitemap contains only the canonical introduction page',()=>{
  const r=renderSite(c,{VERCEL_ENV:'production'});
  assert.equal((r.sitemap.match(/<loc>/g)||[]).length,1);
  assert.match(r.sitemap,/<loc>https:\/\/queue-mate\.com\/<\/loc>/);
  assert.doesNotMatch(r.sitemap,/app\.|vercel\.app|lastmod|\/login|\/party/);
  assert.match(r.robots,/Sitemap: https:\/\/queue-mate\.com\/sitemap\.xml/);
});
for(const env of [{},{VERCEL_ENV:'preview'},{VERCEL_ENV:'development'},{VERCEL_ENV:'preview',BUILD_TARGET:'production'}]){
  test(`nonproduction HTML stays noindex: ${JSON.stringify(env)}`,()=>{
    const r=renderSite(c,env);
    assert.equal(r.mode.indexable,false);assert.equal(r.sitemap,null);
    assert.match(r.html,/<meta name="robots" content="noindex, nofollow">/);
  });
}
test('search publication does not change any approved visible body HTML',()=>{
  const off=renderSite({...c,allowIndexing:false},{VERCEL_ENV:'production'});
  const on=renderSite(c,{VERCEL_ENV:'production'});
  assert.equal(body(off.html),body(on.html));
});
test('the production deployment still excludes every noncanonical Vercel host',async()=>{
  const v=JSON.parse(await readFile(new URL('../vercel.json',import.meta.url),'utf8'));
  const rule=v.headers.find(r=>r.headers?.some(h=>h.key==='X-Robots-Tag'&&h.value.includes('noindex')));
  assert.ok(rule);const pattern=new RegExp(rule.missing.find(m=>m.type==='host').value);
  assert.equal(pattern.test('queue-mate.com'),true);
  for(const host of ['queue-mate-landing.vercel.app','preview.vercel.app','www.queue-mate.com','app.queue-mate.com','q-mate.com','queue-mate.com.example.org'])assert.equal(pattern.test(host),false);
});
test('switching publication off remains a working rollback without touching the app',()=>{
  const r=renderSite({...c,allowIndexing:false},{VERCEL_ENV:'production'});
  assert.equal(r.mode.indexable,false);assert.equal(r.sitemap,null);
  assert.match(r.html,/noindex, nofollow/);assert.match(r.html,/서비스 준비 중/);
});
