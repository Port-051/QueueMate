import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite} from '../src/render.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const draft={...structuredClone(config),allowIndexing:false,appReady:false};
const approved={...structuredClone(config),contentApproved:true,uiApproved:true,allowIndexing:true,appReady:false};

test('purchased domain and planned app address are exact',()=>{
  assert.equal(config.origin,'https://queue-mate.com');
  assert.equal(config.appUrl,'https://app.queue-mate.com/');
});
test('domain settings alone never bypass an explicit indexing refusal',()=>{
  assert.equal(draft.allowIndexing,false);
  assert.equal(draft.appReady,false);
  const r=renderSite(draft,{VERCEL_ENV:'production'});
  assert.equal(r.mode.indexable,false);
  assert.equal(r.sitemap,null);
  assert.doesNotMatch(r.html,/href="https:\/\/app\.queue-mate\.com/);
});
test('HTML and AI guide use the purchased domain without the former host',()=>{
  const r=renderSite(draft);
  assert.match(r.html,/<span>queue-mate\.com<\/span>/);
  assert.match(r.html,/rel="canonical" href="https:\/\/queue-mate\.com\/"/);
  assert.match(r.llms,/https:\/\/queue-mate\.com\//);
  for(const value of [r.html,r.llms,r.robots])assert.doesNotMatch(value,/q-mate\.com/);
});
test('approved sitemap and crawler declaration use only the purchased homepage',()=>{
  const r=renderSite(approved,{VERCEL_ENV:'production'});
  assert.match(r.robots,/Sitemap: https:\/\/queue-mate\.com\/sitemap\.xml/);
  assert.match(r.sitemap,/<loc>https:\/\/queue-mate\.com\/<\/loc>/);
  assert.equal((r.sitemap.match(/<loc>/g)||[]).length,1);
  assert.doesNotMatch(r.sitemap,/q-mate\.com|app\.queue-mate\.com|lastmod/);
});
test('preview stays noindex even with an approved production configuration',()=>{
  const r=renderSite(approved,{VERCEL_ENV:'preview',BUILD_TARGET:'production'});
  assert.equal(r.mode.indexable,false);
  assert.equal(r.sitemap,null);
});
test('host exception excludes old host, www and lookalike domains',async()=>{
  const vercel=JSON.parse(await readFile(new URL('../vercel.json',import.meta.url),'utf8'));
  const rule=vercel.headers.find(h=>h.missing?.some(m=>m.type==='host'));
  const pattern=new RegExp(rule.missing.find(m=>m.type==='host').value);
  assert.equal(pattern.test('queue-mate.com'),true);
  for(const host of ['q-mate.com','www.queue-mate.com','app.queue-mate.com','queue-mate.com.evil.test','queue-mateXcom','preview.vercel.app'])assert.equal(pattern.test(host),false);
  assert.ok(rule.headers.some(h=>h.key==='X-Robots-Tag'&&h.value.includes('noindex')));
});
test('old-domain artwork is not advertised until a replacement is reviewed',()=>{
  assert.equal(config.media.ogImage,'');
  const html=renderSite(draft).html;
  assert.doesNotMatch(html,/property="og:image"|name="twitter:image"/);
  assert.match(html,/name="twitter:card" content="summary"/);
});
test('footer derives its hostname from config rather than a hardcoded address',()=>{
  const html=renderSite({...draft,origin:'https://example.org'}).html;
  assert.match(html,/<span>example\.org<\/span>/);
  assert.doesNotMatch(html,/<span>queue-mate\.com<\/span>/);
});
