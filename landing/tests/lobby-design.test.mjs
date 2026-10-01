import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite,renderUseFlow} from '../src/render.mjs';
const config=JSON.parse(await readFile(new URL('../site.config.json',import.meta.url),'utf8'));
const html=renderSite(config,{VERCEL_ENV:'production'}).html;

const css=await readFile(new URL('../public/assets/product-ui.css',import.meta.url),'utf8');
test('actual captures replace fabricated seats without changing the headline',()=>{
 const flow=renderUseFlow();
 assert.match(flow,/class="actual-showcase"/);assert.match(flow,/실제 UI · 예시 데이터/);
 assert.doesNotMatch(flow,/self-member|seat-symbol|panel-orbit|class="voice-panel"/);
 assert.match(html,/<h1 id="hero-title">조건에 맞는 팀원을 찾고,/);
});
test('review controls never become live matchmaking, microphone or endorsement claims',()=>{
 assert.doesNotMatch(html,/<iframe|<video|getUserMedia|실시간 접속자|누적 매칭|class="feature-visual /);
 assert.doesNotMatch(css,/@import|@font-face|url\(https|infinite/);
 assert.match(html,/실제 참가·음성 연결은 되지 않습니다/);
});
test('native state toggle and overflow rules work without a client script',()=>{
 assert.match(html,/<details class="join-demo" open>/);
 assert.match(css,/:has\(\.join-demo\[open\]\)/);
 assert.match(css,/@media\(prefers-reduced-motion:reduce\)/);
 assert.match(css,/overflow:auto/);assert.match(html,/좌우로 밀어/);
});
test('ownership, analytics configuration and indexing are preserved',()=>{
 for(const name of ['google','naver'])assert.ok(html.includes(`name="${name}-site-verification"`));
 assert.match(html,/rel="canonical" href="https:\/\/queue-mate\.com\/"/);
 assert.equal(config.appReady,false);assert.equal(config.allowIndexing,true);
 assert.deepEqual(config.analytics,{enabled:true,measurementId:'G-89S881436M'});
});
