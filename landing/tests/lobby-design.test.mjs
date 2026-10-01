import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderSite,renderUseFlow} from '../src/render.mjs';
const c = JSON.parse(await readFile(new URL('../site.config.json', import.meta.url), 'utf8'));
const html = renderSite(c,{VERCEL_ENV:'production'}).html;
const flow = renderUseFlow();
const css = await readFile(new URL('../public/assets/concise.css', import.meta.url),'utf8');

test('lobby expresses five seats and preserves third-member toggle, not a full party',()=>{
  assert.equal((flow.match(/<li>/g)||[]).length,2);
  assert.equal((flow.match(/class="after-join self-member"/g)||[]).length,1);
  assert.equal((flow.match(/<details class="join-demo">/g)||[]).length,1);
  assert.match(flow,/<span class="before-join">2<\/span><span class="after-join">3<\/span>/);
  assert.match(flow,/남은 빈자리/);
  assert.match(flow,/데모 · 예시 데이터/);
  assert.match(flow,/실제 참가·음성 연결은 되지 않습니다/);
  assert.match(flow,/모집 중에도 함께 대화/);
});
test('visual examples do not become trackers, live requests, fake endorsements or font downloads',()=>{
  assert.equal((html.match(/class="feature-visual /g)||[]).length,3);
  assert.equal((html.match(/aria-hidden="true"><div class="(?:match-input|sound-orbit)"/g)||[]).length,2);
  assert.doesNotMatch(html,/<script src=|<iframe|<video|getUserMedia|실시간 접속자|전환율|별점|누적 매칭/);
  assert.doesNotMatch(css,/@import|@font-face|url\(https|infinite|animation-delay/);
});
test('motion is one shot, respects reduced motion, and native details still works without JS',()=>{
  assert.match(css,/@media\(prefers-reduced-motion:reduce\)/);
  assert.match(css,/animation:none!important/);
  assert.match(css,/seat-arrive \.32s ease both/);
  assert.match(css,/\.join-demo\{display:block;grid-column:3;grid-row:4/);
  assert.match(html,/<details class="join-demo">/);
});
test('existing discovery and ownership settings remain available in first HTML',()=>{
  assert.match(html,/<meta name="google-site-verification"/);
  assert.match(html,/<meta name="naver-site-verification"/);
  assert.match(html,/content="index, follow, max-image-preview:large"/);
  assert.match(html,/rel="canonical" href="https:\/\/queue-mate\.com\/"/);
  assert.match(html,/<h1 id="hero-title">조건에 맞는 팀원을 찾고,/);
  assert.match(html,/롤 듀오·파티를 자동으로 찾거나, 모집방에 직접 참여하세요/);
  assert.equal(c.appReady,false);
});
